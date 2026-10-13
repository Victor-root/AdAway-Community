package org.adaway.vpn;

import static androidx.work.ExistingPeriodicWorkPolicy.KEEP;
import static androidx.work.ListenableWorker.Result.success;
import static java.util.concurrent.TimeUnit.MINUTES;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.adaway.helper.PreferenceHelper;

import timber.log.Timber;

/**
 * This class is a worker to monitor the {@link VpnService} is still running.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class VpnServiceHeartbeat extends Worker {
    /**
     * The VPN service heartbeat unique worker name.
     */
    private static final String WORK_NAME = "vpnHeartbeat";

    /**
     * Constructor.
     *
     * @param context      The application context.
     * @param workerParams The worker parameters.
     */
    public VpnServiceHeartbeat(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        // Gate on the persisted user intent, not the runtime status: the runtime status is
        // self-healed to STOPPED by VpnServiceControls.isRunning() when the process was
        // killed externally, which is precisely the situation this heartbeat must recover
        // from. Gating on it would disable the recovery it exists for.
        boolean userEnabled = PreferenceHelper.getVpnServiceUserEnabled(context);
        if (VpnStartDecision.mayBackgroundStart(userEnabled)
                && !VpnServiceControls.isVpnServiceAlive()) {
            Timber.i("VPN service is not running while user-enabled. Starting VPN service…");
            VpnServiceControls.start(context);
            Timber.i("VPN service started.");
        }
        return success();
    }

    /**
     * Start the VPN service monitor.
     *
     * @param context The application context.
     */
    public static void start(Context context) {
        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(VpnServiceHeartbeat.class, 15, MINUTES)
                .addTag("VPN-heartbeat")
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, KEEP, workRequest);
    }

    /**
     * Stop the VPN service monitor.
     *
     * @param context The application context.
     */
    public static void stop(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);
    }
}
