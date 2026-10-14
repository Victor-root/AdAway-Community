package org.adaway.vpn

import android.content.Context
import android.net.VpnService
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import org.adaway.data.settings.BlockingMethod
import org.adaway.data.settings.SettingsRepository
import org.adaway.system.AlwaysOnVpn
import org.adaway.system.DiagnosticLog
import org.adaway.system.Notifications
import java.util.concurrent.atomic.AtomicBoolean

/** Tag of the VPN events in the logs. */
internal const val VPN_LOG_TAG = "AdAwayVpn"

enum class StartResult { STARTED, NOT_WANTED, CONSENT_REQUIRED, REFUSED }

/** Whether AdAway is the Android always-on VPN, and blocks connections without it (lockdown). */
enum class AlwaysOnMode { OFF, ON, LOCKDOWN, UNKNOWN }

/**
 * Starts the VPN service and tells its status. The VPN runs only while the user wants it, as
 * stored in the settings: turning that off is enough for the running session to stop.
 */
class VpnController(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val diagnosticLog: DiagnosticLog,
) {
    private val mutableStatus = MutableStateFlow(VpnStatus.STOPPED)
    val status: StateFlow<VpnStatus> = mutableStatus.asStateFlow()

    @Volatile
    private var attached: VpnSession? = null
    private val consentPrompted = AtomicBoolean(false)

    private val session: VpnSession? get() = attached?.takeUnless { it.isEnded }

    /** Whether Android must first ask the user to let the app create a VPN. */
    fun needsConsent(): Boolean = VpnService.prepare(context) != null

    /** True the first time only: the consent is asked on its own once per launch of the app at most. */
    fun claimConsentPrompt(): Boolean = consentPrompted.compareAndSet(false, true)

    fun alwaysOnMode(): AlwaysOnMode = session?.alwaysOnMode() ?: when (val app = AlwaysOnVpn.read(context)) {
        AlwaysOnVpn.None -> AlwaysOnMode.OFF
        is AlwaysOnVpn.App -> if (app.packageName == context.packageName) AlwaysOnMode.ON else AlwaysOnMode.OFF
        AlwaysOnVpn.Unknown -> AlwaysOnMode.UNKNOWN
    }

    /** The user turns ad blocking on, without any delay. */
    suspend fun enable(): StartResult {
        if (needsConsent()) return consentRequired()
        settingsRepository.update { it.copy(vpnWanted = true) }
        session?.let {
            it.requestStart()
            return StartResult.STARTED
        }
        return startService()
    }

    suspend fun disable() {
        settingsRepository.update { it.copy(vpnWanted = false) }
        mutableStatus.compareAndSet(VpnStatus.CONSENT_REQUIRED, VpnStatus.STOPPED)
    }

    /** Starts the VPN the user wants when it is not running, as after the app was killed. */
    suspend fun ensureRunning(): StartResult {
        val settings = settingsRepository.settings.first()
        if (settings.blockingMethod != BlockingMethod.VPN || !settings.vpnWanted) return StartResult.NOT_WANTED
        if (session != null) return StartResult.STARTED
        if (needsConsent()) return consentRequired()
        return startService()
    }

    /** Rebuilds the running tunnel, as the user asks it when the VPN stops working. False when not running. */
    fun reset(): Boolean {
        val running = session ?: return false
        running.rebuildNow()
        return true
    }

    /** As [ensureRunning], for callers in the background which can only tell the user by notification. */
    suspend fun ensureRunningInBackground() = notifyFailure(ensureRunning())

    /** As [enable], from a notification. */
    suspend fun enableInBackground() = notifyFailure(enable())

    /** The running tunnel takes the new final list at once. A wanted VPN not running starts. */
    suspend fun onBlockListApplied() {
        val running = session
        if (running != null) running.reloadRules() else ensureRunningInBackground()
    }

    fun attach(session: VpnSession) {
        attached = session
    }

    fun detach(session: VpnSession) {
        if (attached === session) attached = null
    }

    fun publish(status: VpnStatus) {
        mutableStatus.value = status
    }

    private fun notifyFailure(result: StartResult) {
        when (result) {
            StartResult.CONSENT_REQUIRED -> Notifications.showVpnConsentRequired(context)
            StartResult.REFUSED -> Notifications.showVpnStartRefused(context)
            StartResult.STARTED, StartResult.NOT_WANTED -> {}
        }
    }

    private fun consentRequired(): StartResult {
        diagnosticLog.warn(VPN_LOG_TAG, "VPN consent missing")
        if (session == null) mutableStatus.value = VpnStatus.CONSENT_REQUIRED
        return StartResult.CONSENT_REQUIRED
    }

    private fun startService(): StartResult = try {
        ContextCompat.startForegroundService(context, AdAwayVpnService.startIntent(context))
        StartResult.STARTED
    } catch (_: IllegalStateException) {
        // Android does not let an app in the background start a foreground service (Android 12+).
        diagnosticLog.warn(VPN_LOG_TAG, "Start refused in the background")
        StartResult.REFUSED
    }
}
