package org.adaway.vpn

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import org.adaway.AdAwayApplication
import org.adaway.system.Notifications

/**
 * The VPN service, in the foreground while a session runs. Started by the app, by Android when
 * AdAway is the always-on VPN, or again by Android after the process was killed.
 */
class AdAwayVpnService : VpnService() {
    private val container get() = (application as AdAwayApplication).container
    private var session: VpnSession? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (session?.isEnded == false) return START_STICKY
        // Restarted by Android after the process was killed: the foreground service type needs the consent.
        if (intent == null && prepare(this) != null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!startForeground()) return START_NOT_STICKY
        val started = VpnSession(
            service = this,
            settingsRepository = container.settingsRepository,
            finalDirectory = container.engineFiles.finalDirectory,
            onStatus = ::onStatus,
            onEnded = { ended, status -> ContextCompat.getMainExecutor(this).execute { onSessionEnded(ended, status) } },
        )
        session = started
        container.vpnController.attach(started)
        started.start(markWanted = intent?.action == SERVICE_INTERFACE)
        return START_STICKY
    }

    override fun onRevoke() {
        val running = session
        if (running == null) {
            super.onRevoke()
        } else {
            running.revoke(consentLost = prepare(this) != null)
        }
    }

    override fun onDestroy() {
        session?.let {
            it.stop()
            container.vpnController.detach(it)
        }
        session = null
        super.onDestroy()
    }

    private fun startForeground(): Boolean = try {
        val notification = Notifications.vpnStatus(this, VpnStatus.STARTING)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(Notifications.VPN_STATUS_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        } else {
            startForeground(Notifications.VPN_STATUS_ID, notification)
        }
        true
    } catch (_: IllegalStateException) {
        refuseStart()
    } catch (_: SecurityException) {
        refuseStart()
    }

    /** Android refused the foreground: the user intention is kept for a later restart. */
    private fun refuseStart(): Boolean {
        container.vpnController.publish(VpnStatus.STOPPED)
        Notifications.showVpnStartRefused(this)
        stopSelf()
        return false
    }

    /** Called on the session thread. */
    private fun onStatus(status: VpnStatus) {
        container.vpnController.publish(status)
        if (status == VpnStatus.RUNNING) Notifications.cancelVpnAttention(this)
        if (status.isServiceAlive()) Notifications.showVpnStatus(this, status)
    }

    private fun onSessionEnded(ended: VpnSession, status: VpnStatus) {
        if (session !== ended) return
        session = null
        container.vpnController.detach(ended)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        if (status == VpnStatus.CONSENT_REQUIRED) {
            Notifications.cancelVpnStatus(this)
            Notifications.showVpnConsentRequired(this)
        } else {
            Notifications.showVpnStatus(this, status)
        }
        stopSelf()
    }

    companion object {
        fun startIntent(context: Context) = Intent(context, AdAwayVpnService::class.java)
    }
}
