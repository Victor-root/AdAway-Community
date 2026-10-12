package org.adaway.vpn;

import static android.content.Context.ACTIVITY_SERVICE;
import static java.lang.Integer.MAX_VALUE;
import static org.adaway.broadcast.Command.START;
import static org.adaway.broadcast.Command.STOP;
import static org.adaway.model.adblocking.AdBlockMethod.VPN;
import static org.adaway.vpn.VpnStatus.STOPPED;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;

import org.adaway.helper.PreferenceHelper;

import timber.log.Timber;

/**
 * This utility class allows controlling (start and stop) the AdAway VPN service.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public final class VpnServiceControls {
    /**
     * Private constructor.
     */
    private VpnServiceControls() {

    }

    /**
     * Check if the VPN service is currently running.
     *
     * @param context The application context.
     * @return {@code true} if the VPN service is currently running, {@code false} otherwise.
     */
    public static boolean isRunning(Context context) {
        VpnStatus status = PreferenceHelper.getVpnServiceStatus(context);
        if (status.isStarted() && !isVpnServiceAlive(context)) {
            // The process was killed externally (OEM battery manager force-stop, crash)
            // without a chance to persist the stop: the stored status lies. Fix it so the
            // UI shows the truth. The check targets OUR service specifically, not "any
            // network with VPN transport": the latter reported true whenever ANOTHER VPN
            // app (WireGuard…) held the slot, keeping the UI stuck on "running" after an
            // external kill.
            Timber.i("VPN status said %s but the service is dead (killed externally); marking stopped.", status);
            status = STOPPED;
            PreferenceHelper.setVpnServiceStatus(context, status);
        }
        return status.isStarted();
    }

    /**
     * Check whether this application's {@link VpnService} is actually alive, as seen by the
     * system, regardless of what the persisted status claims. Survives external process kills:
     * a fresh process asks the system, it does not trust its own memory.
     * <p>
     * {@link ActivityManager#getRunningServices(int)} is deprecated but still returns the
     * caller's own services, which is the only use here.
     */
    @SuppressWarnings("deprecation")
    public static boolean isVpnServiceAlive(Context context) {
        String serviceName = VpnService.class.getName();
        ActivityManager manager = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(MAX_VALUE)) {
            if (serviceName.equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Restart the VPN if it was killed behind the user's back (OEM battery manager
     * force-stop). A force-stopped app cannot self-heal: Android blocks its sticky
     * resurrection, WorkManager jobs (heartbeat) and broadcasts until the user opens the
     * app again. So the home screens call this on open, which is the first moment
     * recovery is possible again.
     * <p>
     * No-op when the ad-block method is not VPN, when the user has explicitly disabled
     * the VPN, or when the service is genuinely running.
     *
     * @param context The application context.
     */
    public static void resurrectIfKilledExternally(Context context) {
        if (PreferenceHelper.getAdBlockMethod(context) != VPN) {
            return;
        }
        boolean userEnabled = PreferenceHelper.getVpnServiceUserEnabled(context);
        if (!VpnStartDecision.mayBackgroundStart(userEnabled)) {
            return;
        }
        if (isVpnServiceAlive(context)) {
            return;
        }
        Timber.i("VPN was killed externally while user-enabled; restarting it.");
        start(context);
    }

    /**
     * Start the VPN service.
     *
     * @param context The application context.
     * @return {@code true} if the service is started, {@code false} otherwise.
     */
    public static boolean start(Context context) {
        // Check if VPN is already running
        if (isRunning(context)) {
            return true;
        }
        // Start the VPN service
        Intent intent = new Intent(context, VpnService.class);
        START.appendToIntent(intent);
        boolean started = context.startForegroundService(intent) != null;
        if (started) {
            // Start the heartbeat
            VpnServiceHeartbeat.start(context);
        }
        return started;
    }

    /**
     * Stop the VPN service.
     *
     * @param context The application context.
     */
    public static void stop(Context context) {
        // Stop the heartbeat
        VpnServiceHeartbeat.stop(context);
        // Stop the service
        Intent intent = new Intent(context, VpnService.class);
        STOP.appendToIntent(intent);
        context.startService(intent);
    }
}
