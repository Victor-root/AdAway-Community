package org.adaway.vpn

import android.net.Network
import android.net.VpnService
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.adaway.data.settings.BlockingMethod
import org.adaway.data.settings.Settings
import org.adaway.data.settings.SettingsRepository
import org.adaway.data.settings.SystemAppExclusion
import org.adaway.data.settings.VpnDnsMode
import org.adaway.engine.Engine
import org.adaway.system.DiagnosticLog
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException

/**
 * One run of the VPN service: builds the tunnel, keeps it on the network Android uses, and closes
 * it when the user no longer wants the VPN. Everything runs on a single thread, in order.
 */
class VpnSession(
    private val service: VpnService,
    private val settingsRepository: SettingsRepository,
    private val diagnosticLog: DiagnosticLog,
    finalDirectory: File,
    private val onStatus: (VpnStatus) -> Unit,
    private val onEnded: (VpnSession, VpnStatus) -> Unit,
) {
    /** The settings read when building the tunnel, which only a rebuild can apply. */
    private data class BuildSettings(
        val ipv6Enabled: Boolean,
        val monitor: Boolean,
        val systemAppExclusion: SystemAppExclusion,
        val excludedApps: Set<String>,
        val dnsMode: VpnDnsMode,
        val customDnsServers: List<String>,
        val dohUrl: String,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val builder = TunnelBuilder(service, finalDirectory)
    private val throttle = ReconnectThrottle()
    private val networks = NetworkTracker(service, dispatch = { block -> scope.launch { block() } }, onChange = ::onNetworkChanged)
    private var settings: Settings? = null
    private var status = VpnStatus.STARTING
    private var tunnel: ActiveTunnel? = null
    private var rebuildJob: Job? = null
    private var interfaceCheckJob: Job? = null

    /** Set once the final status is published: from then on, a new session may start. */
    @Volatile
    var isEnded = false
        private set

    /** Starts the session. [markWanted] records that the user wants the VPN, as Android asked. */
    fun start(markWanted: Boolean) {
        scope.launch {
            onStatus(status)
            if (markWanted) settingsRepository.update { it.copy(vpnWanted = true) }
            networks.start()
            settingsRepository.settings.collect(::onSettings)
        }
    }

    /** An explicit request of the user: a pending rebuild happens now. */
    fun requestStart() {
        scope.launch {
            if (status != VpnStatus.RECONNECTING) return@launch
            throttle.reset()
            scheduleRebuild()
        }
    }

    /** Rebuilds the tunnel from scratch, reading the DNS servers again, at once. */
    fun rebuildNow() {
        scope.launch {
            throttle.reset()
            scheduleRebuild()
        }
    }

    /** Applies a new final list to the running tunnel. A tunnel built later reads it anyway. */
    fun reloadRules() {
        scope.launch {
            val running = tunnel ?: return@launch
            try {
                Engine.reloadTunnelRules(running.handle)
            } catch (exception: RuntimeException) {
                diagnosticLog.warn(VPN_LOG_TAG, "Rules not reloaded: $exception")
            }
        }
    }

    /** Android took the VPN back. Without the consent, the user chose another VPN app. */
    fun revoke(consentLost: Boolean) {
        scope.launch {
            if (consentLost) settingsRepository.update { it.copy(vpnWanted = false) }
            end(VpnStatus.STOPPED)
        }
    }

    fun stop() {
        scope.launch { end(VpnStatus.STOPPED) }
    }

    /** Asked to Android by the service itself, the only reliable way. Null before Android 10. */
    fun alwaysOnMode(): AlwaysOnMode? = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> null
        service.isLockdownEnabled -> AlwaysOnMode.LOCKDOWN
        service.isAlwaysOn -> AlwaysOnMode.ON
        else -> AlwaysOnMode.OFF
    }

    private fun onSettings(new: Settings) {
        val old = settings
        settings = new
        when {
            !new.vpnWanted || new.blockingMethod != BlockingMethod.VPN -> end(VpnStatus.STOPPED)
            old == null -> scheduleRebuild()
            buildSettings(old) != buildSettings(new) && status != VpnStatus.WAITING_FOR_NETWORK -> {
                diagnosticLog.info(VPN_LOG_TAG, "Settings changed")
                throttle.reset()
                scheduleRebuild()
            }
        }
    }

    private fun onNetworkChanged() {
        if (isEnded || settings == null) return
        val running = tunnel
        val target = networks.target(preferred = running?.network)
        when {
            target == null -> if (status != VpnStatus.WAITING_FOR_NETWORK) {
                diagnosticLog.info(VPN_LOG_TAG, "No network")
                rebuildJob?.cancel()
                closeTunnel()
                setStatus(VpnStatus.WAITING_FOR_NETWORK)
            }
            status == VpnStatus.WAITING_FOR_NETWORK -> {
                diagnosticLog.info(VPN_LOG_TAG, "Network back")
                throttle.reset()
                scheduleRebuild()
            }
            // A rebuild already waiting reads the latest networks.
            running == null || rebuildJob?.isActive == true -> {}
            target != running.network -> {
                diagnosticLog.info(VPN_LOG_TAG, "Network changed")
                scheduleRebuild()
            }
            else -> {
                val selection = selectUpstream(target)
                val current = running.selection
                // A fallback is no change worth a rebuild: the network may soon give its servers.
                val changed = when (selection) {
                    is DnsUpstream.Servers -> !selection.usesFallback &&
                        selection.servers.toSet() != (current as? DnsUpstream.Servers)?.servers?.toSet()
                    is DnsUpstream.Https -> selection != current
                }
                if (changed) {
                    diagnosticLog.info(VPN_LOG_TAG, "DNS servers changed")
                    scheduleRebuild()
                }
            }
        }
    }

    private fun scheduleRebuild() {
        rebuildJob?.cancel()
        val wait = throttle.nextDelay()
        if (tunnel == null && status != VpnStatus.STARTING) setStatus(VpnStatus.RECONNECTING)
        rebuildJob = scope.launch {
            delay(wait)
            rebuild()
        }
    }

    private fun rebuild() {
        val settings = settings ?: return
        val network = networks.target(preferred = tunnel?.network)
        if (network == null) {
            closeTunnel()
            setStatus(VpnStatus.WAITING_FOR_NETWORK)
            return
        }
        val selection = selectUpstream(network)
        diagnosticLog.info(VPN_LOG_TAG, "Building on $network with $selection")
        // The new tunnel replaces the previous one before it closes, so that the VPN never drops.
        val built = try {
            builder.build(
                network = network,
                selection = selection,
                virtualNetwork = VirtualNetwork.avoiding(networks.usedPrefixes(network)),
                excludedPackages = VpnExclusions.packages(service, settings),
                monitor = settings.vpnConnectionMonitor,
            )
        } catch (exception: IOException) {
            diagnosticLog.warn(VPN_LOG_TAG, "Network not usable: $exception")
            closeTunnel()
            setStatus(VpnStatus.RECONNECTING)
            scheduleRebuild()
            return
        } catch (exception: RuntimeException) {
            diagnosticLog.warn(VPN_LOG_TAG, "Tunnel not created: $exception")
            end(VpnStatus.STOPPED)
            return
        }
        closeTunnel()
        if (built == null) {
            end(VpnStatus.CONSENT_REQUIRED)
            return
        }
        tunnel = built
        built.run { exit -> scope.launch { onTunnelExit(built, exit) } }
        setStatus(VpnStatus.RUNNING)
        checkInterfacePeriodically()
    }

    private fun onTunnelExit(exited: ActiveTunnel, exit: Int) {
        // A tunnel closed on purpose is no longer the current one.
        if (exited !== tunnel) return
        diagnosticLog.warn(VPN_LOG_TAG, "Tunnel ended with code $exit")
        closeTunnel()
        scheduleRebuild()
    }

    /** Android may remove the interface without telling: the tunnel is then rebuilt. */
    private fun checkInterfacePeriodically() {
        interfaceCheckJob = scope.launch {
            while (isActive) {
                delay(INTERFACE_CHECK_MILLIS)
                if (!hasTunInterface()) {
                    diagnosticLog.warn(VPN_LOG_TAG, "Tunnel interface gone")
                    closeTunnel()
                    scheduleRebuild()
                }
            }
        }
    }

    private fun closeTunnel() {
        interfaceCheckJob?.cancel()
        interfaceCheckJob = null
        tunnel?.close()
        tunnel = null
    }

    private fun end(finalStatus: VpnStatus) {
        if (isEnded) return
        rebuildJob?.cancel()
        closeTunnel()
        networks.stop()
        setStatus(finalStatus)
        isEnded = true
        onEnded(this, finalStatus)
        scope.cancel()
    }

    private fun setStatus(new: VpnStatus) {
        if (new == status) return
        diagnosticLog.info(VPN_LOG_TAG, "Status $new")
        status = new
        onStatus(new)
    }

    /** The chosen servers, or the network ones when none of the chosen addresses is valid. */
    private fun selectUpstream(network: Network): DnsUpstream {
        val settings = settings
        val custom = settings?.vpnCustomDnsServers.orEmpty().mapNotNull(::numericAddress)
        return when {
            settings?.vpnDnsMode == VpnDnsMode.HTTPS -> DnsUpstream.Https(settings.vpnDohUrl)
            settings?.vpnDnsMode == VpnDnsMode.CUSTOM && custom.isNotEmpty() -> DnsUpstream.Servers(custom, usesFallback = false)
            else -> DnsServerSelection.select(
                networkServers = networks.dnsServers(network),
                ipv6Enabled = settings?.ipv6Enabled == true,
                hasIpv4 = networks.hasIpv4(),
                hasIpv6 = networks.hasIpv6(),
            )
        }
    }

    /** Never a name to resolve, which would block. */
    private fun numericAddress(address: String): InetAddress? =
        Engine.normalizeIpAddress(address)?.let(InetAddress::getByName)

    private fun buildSettings(settings: Settings) = BuildSettings(
        ipv6Enabled = settings.ipv6Enabled,
        monitor = settings.vpnConnectionMonitor,
        systemAppExclusion = settings.vpnSystemAppExclusion,
        excludedApps = settings.vpnExcludedApps,
        dnsMode = settings.vpnDnsMode,
        customDnsServers = settings.vpnCustomDnsServers,
        dohUrl = settings.vpnDohUrl,
    )

    private fun hasTunInterface(): Boolean = try {
        NetworkInterface.getNetworkInterfaces()?.toList()?.any { it.name.startsWith("tun") } ?: true
    } catch (_: SocketException) {
        // Not listable for now: assume all is well rather than rebuilding for nothing.
        true
    }

    private companion object {
        const val INTERFACE_CHECK_MILLIS = 10_000L
    }
}
