/*
 * Derived from dns66:
 * Copyright (C) 2016-2019 Julian Andres Klode <jak@jak-linux.org>
 *
 * Derived from AdBuster:
 * Copyright (C) 2016 Daniel Brodie <dbrodie@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * Contributions shall also be provided under any later versions of the
 * GPL.
 */
package org.adaway.vpn;

import static android.Manifest.permission.POST_NOTIFICATIONS;
import static android.app.NotificationManager.IMPORTANCE_LOW;
import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED;
import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static android.net.NetworkCapabilities.TRANSPORT_VPN;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;
import static org.adaway.broadcast.Command.START;
import static org.adaway.broadcast.Command.STOP;
import static org.adaway.broadcast.CommandReceiver.SEND_COMMAND_ACTION;
import static org.adaway.helper.NotificationHelper.VPN_RESUME_SERVICE_NOTIFICATION_ID;
import static org.adaway.helper.NotificationHelper.VPN_RUNNING_SERVICE_NOTIFICATION_ID;
import static org.adaway.helper.NotificationHelper.VPN_SERVICE_NOTIFICATION_CHANNEL;
import static org.adaway.vpn.VpnService.NetworkType.CELLULAR;
import static org.adaway.vpn.VpnService.NetworkType.WIFI;
import static org.adaway.vpn.VpnStatus.RECONNECTING;
import static org.adaway.vpn.VpnStatus.RUNNING;
import static org.adaway.vpn.VpnStatus.STARTING;
import static org.adaway.vpn.VpnStatus.STOPPED;
import static org.adaway.vpn.VpnStatus.WAITING_FOR_NETWORK;
import static java.util.Objects.requireNonNull;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.adaway.R;
import org.adaway.broadcast.Command;
import org.adaway.broadcast.CommandReceiver;
import org.adaway.helper.PreferenceHelper;
import org.adaway.ui.home.HomeActivity;
import org.adaway.util.log.SystemRestrictionsLog;
import org.adaway.vpn.dns.DnsServerMapper;
import org.adaway.vpn.worker.VpnWorker;

import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.util.HashSet;
import java.util.List;

import timber.log.Timber;

/**
 * This class is the VPN platform service implementation.
 * <p>
 * it is in charge of:
 * <ul>
 * <li>Accepting service commands,</li>
 * <li>Starting / stopping the {@link VpnWorker} thread,</li>
 * <li>Publishing notifications and intent about the VPN state,</li>
 * <li>Reacting to network connectivity changes.</li>
 * </ul>
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class VpnService extends android.net.VpnService implements Handler.Callback {
    public static final String VPN_UPDATE_STATUS_INTENT = "org.jak_linux.dns66.VPN_UPDATE_STATUS";
    public static final String VPN_UPDATE_STATUS_EXTRA = "VPN_STATUS";
    /*
     * Notification intent related.
     */
    private static final int REQUEST_CODE_START = 43;
    private static final int REQUEST_CODE_PAUSE = 42;
    /*
     * Handler related.
     */
    private static final int VPN_STATUS_UPDATE_MESSAGE_TYPE = 0;

    private final MyHandler handler;
    private final NetworkTypeCallback wifiNetworkCallback;
    private final NetworkTypeCallback cellularNetworkCallback;
    /**
     * Whether a Wi-Fi network is currently available on the device.
     */
    private boolean wifiAvailable;
    /**
     * Whether the available Wi-Fi network has validated internet connectivity
     * ({@link android.net.NetworkCapabilities#NET_CAPABILITY_VALIDATED}). Android only promotes
     * Wi-Fi to the default (active) network, the one that actually carries the tunnel's traffic
     * and whose DNS the {@link org.adaway.vpn.dns.DnsServerMapper} resolves, once it is
     * validated. Switching the tunnel to Wi-Fi before then would bind it to a DNS server that is
     * not yet reachable.
     */
    private boolean wifiValidated;
    /**
     * Whether a cellular network is currently available on the device.
     */
    private boolean cellularAvailable;
    /**
     * The transport the running tunnel is currently built for, or <code>null</code> when the VPN
     * is stopped (no network). The tunnel is rebuilt only when the transport Android would route
     * through actually changes, not when a secondary network merely appears or disappears (e.g.
     * the cellular radio flickering on Oppo / ColorOS power-saving devices while Wi-Fi stays the
     * default). This is the single source of truth that keeps the tunnel's DNS in sync with the
     * network carrying its traffic.
     */
    private NetworkType currentTransport;
    /**
     * The <em>effective</em> DNS servers of the network currently carrying the tunnel
     * ({@link #currentTransport}), as last observed via
     * {@link NetworkTypeCallback#onLinkPropertiesChanged} and filtered through
     * {@link DnsServerMapper#getEffectiveDnsServers} to match what the tunnel actually forwards to,
     * or <code>null</code> when not yet observed for the current transport. Used to detect a
     * DNS-server change that happens <em>without</em> a transport change (e.g. a DHCP lease renewal
     * or a same-SSID roam), which {@link #reconcile()} cannot see, so the tunnel is rebuilt to pick
     * up the new resolver. Only ever touched from the callback handler thread, like the other
     * network-state fields.
     */
    private List<InetAddress> currentTransportDnsServers;
    /**
     * Whether the tunnel is down because it was explicitly stopped (user pause, or a revoke), as
     * opposed to being down while waiting for a network.
     * <p>
     * Stopping the service does not take its network callbacks down at once: {@link #stopSelf()}
     * only queues the destruction, and any callback already queued ahead of it still runs. Those
     * callbacks reach {@link #reconcile()}, which sees a tunnel that is down with a network
     * available and dutifully brings it back up, so a Wi-Fi drop landing in that window restarted
     * the VPN right after the user paused it. This tells the two situations apart.
     */
    private boolean stoppedOnPurpose;
    private final VpnWorker vpnWorker;

    /**
     * Constructor.
     */
    public VpnService() {
        this.handler = new MyHandler(this);
        this.wifiNetworkCallback = new NetworkTypeCallback(WIFI);
        this.cellularNetworkCallback = new NetworkTypeCallback(CELLULAR);
        this.wifiAvailable = false;
        this.wifiValidated = false;
        this.cellularAvailable = false;
        this.currentTransport = null;
        this.currentTransportDnsServers = null;
        this.stoppedOnPurpose = false;
        this.vpnWorker = new VpnWorker(this);
    }

    /**
     * Set the transport the tunnel is currently built for, resetting the observed DNS snapshot so
     * the next {@link NetworkTypeCallback#onLinkPropertiesChanged} for the new transport is recorded
     * fresh instead of being compared against the previous transport's (or a stale session's) DNS.
     *
     * @param transport The new transport, or <code>null</code> when the tunnel is down.
     */
    private void setCurrentTransport(NetworkType transport) {
        this.currentTransport = transport;
        this.currentTransportDnsServers = null;
    }

    /*
     * VPN Service.
     */

    @Override
    public void onCreate() {
        Timber.i("Creating VPN service…");
        registerNetworkCallback();
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        Timber.d("onStartCommand %s", intent == null ? "null intent" : intent);
        // Null intent means the system is resurrecting the service via START_STICKY.
        // https://developer.android.com/reference/android/app/Service#START_STICKY
        // If the user has explicitly disabled the VPN since we last ran, refuse the
        // resurrection; otherwise Android can silently bring the VPN back on its own
        // (see issues #4022 / #4234).
        if (intent == null) {
            boolean userEnabled = PreferenceHelper.getVpnServiceUserEnabled(this);
            if (!userEnabled) {
                Timber.i("Refusing sticky resurrection: user has disabled the VPN.");
                stopSelf(startId);
                return START_NOT_STICKY;
            }
            Timber.i("Sticky resurrection: the system restarted the VPN service after a kill.");
        }
        Command command = intent == null ?
                START :
                Command.readFromIntent(intent);
        switch (command) {
            case START:
                startVpn();
                return START_STICKY;
            case STOP:
                stopVpn();
                return START_NOT_STICKY;
            default:
                Timber.w("Unknown command: %s", command);
                return START_NOT_STICKY;
        }
    }

    @Override
    public void onDestroy() {
        Timber.i("Destroying VPN service…");
        unregisterNetworkCallback();
        Timber.d("Destroyed VPN service.");
    }

    @Override
    public void onRevoke() {
        // The system revoked the VPN. Two very different causes land here, told apart by
        // whether this app still holds the VPN consent:
        // - Consent lost (prepare() != null): the user granted the slot to another VPN
        //   app (WireGuard…). Record user intent OFF and disarm the recovery paths:
        //   fighting for the slot back would be hostile, and Android would refuse the
        //   silent re-grab anyway.
        // - Consent kept: the system revoked on its own (OEM battery manager /
        //   hibernation, seen on ColorOS). The user never asked for this, so keep the
        //   user intent ON: the heartbeat and app-open recovery bring the VPN back.
        // Either way, go through the full stop path: it stops the worker (the default
        // onRevoke was a bare stopSelf() that left the worker looping on a dead tunnel),
        // persists the stopped status and broadcasts it, so the UI and the in-memory
        // model reflect the real state instead of staying stuck on "running".
        boolean slotTakenByAnotherApp = prepare(this) != null;
        if (slotTakenByAnotherApp) {
            Timber.i("VPN revoked: another VPN app took the slot. Recording user intent OFF and stopping.");
            PreferenceHelper.setVpnServiceUserEnabled(this, false);
            VpnServiceHeartbeat.stop(this);
        } else {
            Timber.i("VPN revoked by the system while still authorized (OEM kill/policy). Stopping; recovery paths stay armed.");
        }
        stopVpn();
    }

    /*
     * Handler callback.
     */

    @Override
    public boolean handleMessage(@NonNull Message message) {
        if (message.what == VPN_STATUS_UPDATE_MESSAGE_TYPE) {
            updateVpnStatus(VpnStatus.fromCode(message.arg1));
        }
        return true;
    }

    /**
     * Notify a of the new VPN status.
     *
     * @param status The new VPN status.
     */
    public void notifyVpnStatus(VpnStatus status) {
        Message statusMessage = this.handler.obtainMessage(VPN_STATUS_UPDATE_MESSAGE_TYPE, status.toCode(), 0);
        this.handler.sendMessage(statusMessage);
    }

    private void startVpn() {
        Timber.d("Starting VPN service…");
        PreferenceHelper.setVpnServiceStatus(this, RUNNING);
        this.stoppedOnPurpose = false;
        updateVpnStatus(STARTING);
        // This path is reached only via an explicit START intent (user click, notif
        // action, autostart, sticky resurrection that survived the user-intent gate).
        // The throttler is meant to dampen reconnection storms, NOT to delay user
        // actions: reset it so the tunnel comes up immediately.
        this.vpnWorker.resetThrottle();
        // Record the transport the tunnel is being built for so the network reconciler only
        // rebuilds it when the default network actually changes transport.
        setCurrentTransport(computeDesiredTransport());
        this.vpnWorker.start();
        Timber.i("VPN service started.");
    }

    private void stopVpn() {
        Timber.d("Stopping VPN service…");
        PreferenceHelper.setVpnServiceStatus(this, STOPPED);
        this.stoppedOnPurpose = true;
        setCurrentTransport(null);
        this.vpnWorker.stop();
        // STOP_FOREGROUND_REMOVE is exactly what the deprecated stopForeground(true) did: leave the
        // foreground state and take the notification down with it.
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
        updateVpnStatus(STOPPED);
        Timber.i("VPN service stopped.");
    }

    private void waitForNetVpn() {
        this.vpnWorker.stop();
        updateVpnStatus(WAITING_FOR_NETWORK);
    }

    private void reconnect() {
        updateVpnStatus(RECONNECTING);
        this.vpnWorker.start();
    }

    private void updateVpnStatus(VpnStatus status) {
        Notification notification = getNotification(status);
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        switch (status) {
            case STARTING:
            case RUNNING:
                notificationManager.cancel(VPN_RESUME_SERVICE_NOTIFICATION_ID);
                try {
                    startForeground(VPN_RUNNING_SERVICE_NOTIFICATION_ID, notification);
                } catch (Exception e) {
                    // startForeground() can be denied by AppOps when the process is killed
                    // and restarted by the OEM system (e.g. battery manager). Force a clean
                    // stop so the UI reflects the real state instead of staying stuck on "pause".
                    Timber.e(e, "startForeground denied, forcing VPN stop.");
                    PreferenceHelper.setVpnServiceStatus(this, STOPPED);
                    Intent stoppedIntent = new Intent(VPN_UPDATE_STATUS_INTENT);
                    stoppedIntent.putExtra(VPN_UPDATE_STATUS_EXTRA, STOPPED);
                    LocalBroadcastManager.getInstance(this).sendBroadcast(stoppedIntent);
                    stopSelf();
                    return;
                }
                break;
            default:
                if (checkSelfPermission(POST_NOTIFICATIONS) == PERMISSION_GRANTED) {
                    notificationManager.notify(VPN_RESUME_SERVICE_NOTIFICATION_ID, notification);
                }
        }

        // TODO BUG - Nobody is listening to this intent
        // TODO BUG - VpnModel can lister to it to update the MainActivity according its current state
        Intent intent = new Intent(VPN_UPDATE_STATUS_INTENT);
        intent.putExtra(VPN_UPDATE_STATUS_EXTRA, status);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private Notification getNotification(VpnStatus status) {
        String title = getString(R.string.vpn_notification_title, getString(status.getTextResource()));

        Intent intent = new Intent(getApplicationContext(), HomeActivity.class);
        intent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent contentIntent = PendingIntent.getActivity(getApplicationContext(), 0, intent, FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, VPN_SERVICE_NOTIFICATION_CHANNEL)
                .setPriority(IMPORTANCE_LOW)
                .setContentIntent(contentIntent)
                .setSmallIcon(R.drawable.logo)
                .setColorized(true)
                .setColor(getColor(R.color.notification))
                .setContentTitle(title);
        switch (status) {
            case RUNNING:
                Intent stopIntent = new Intent(this, CommandReceiver.class)
                        .setAction(SEND_COMMAND_ACTION);
                STOP.appendToIntent(stopIntent);
                PendingIntent stopActionIntent = PendingIntent.getBroadcast(this, REQUEST_CODE_PAUSE, stopIntent, FLAG_IMMUTABLE);
                builder.addAction(
                        R.drawable.ic_pause_24dp,
                        getString(R.string.vpn_notification_action_pause),
                        stopActionIntent
                ).setOngoing(true);
                break;
            case STOPPED:
                Intent startIntent = new Intent(this, CommandReceiver.class)
                        .setAction(SEND_COMMAND_ACTION);
                START.appendToIntent(startIntent);
                PendingIntent startActionIntent = PendingIntent.getBroadcast(this, REQUEST_CODE_START, startIntent, FLAG_IMMUTABLE);
                builder.addAction(
                        0,
                        getString(R.string.vpn_notification_action_resume),
                        startActionIntent
                );
                break;
        }
        return builder.build();
    }

    private void registerNetworkCallback() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkRequest wifiNetworkRequest = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_WIFI)
                .build();
        NetworkRequest cellularNetworkRequest = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_CELLULAR)
                .build();
        initializeNetworkState(connectivityManager);
        connectivityManager.registerNetworkCallback(wifiNetworkRequest, this.wifiNetworkCallback, this.handler);
        connectivityManager.registerNetworkCallback(cellularNetworkRequest, this.cellularNetworkCallback, this.handler);

    }

    private void unregisterNetworkCallback() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        connectivityManager.unregisterNetworkCallback(this.wifiNetworkCallback);
        connectivityManager.unregisterNetworkCallback(this.cellularNetworkCallback);
    }

    // getAllNetworks() is deprecated in favour of the network callbacks, which is what the rest of
    // this class already uses. It is kept here for the one thing callbacks cannot do: report the
    // current state synchronously. The callbacks only fire after registration returns, so without
    // this seed startVpn() would run with everything still marked unavailable and build the tunnel
    // for the wrong transport. No non-deprecated API answers "which networks exist right now".
    @SuppressWarnings("deprecation")
    private void initializeNetworkState(ConnectivityManager connectivityManager) {
        this.wifiAvailable = false;
        this.wifiValidated = false;
        this.cellularAvailable = false;
        setCurrentTransport(null);
        // Seed the state synchronously from the current networks. The per-transport callbacks
        // fire right after registration and will refine this, but seeding first means startVpn()
        // (which runs before the callbacks get a chance) already knows the transport to build for.
        for (Network network : connectivityManager.getAllNetworks()) {
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
            if (capabilities == null || capabilities.hasTransport(TRANSPORT_VPN)) {
                continue;
            }
            if (capabilities.hasTransport(TRANSPORT_WIFI)) {
                this.wifiAvailable = true;
                this.wifiValidated = capabilities.hasCapability(NET_CAPABILITY_VALIDATED);
            } else if (capabilities.hasTransport(TRANSPORT_CELLULAR)) {
                this.cellularAvailable = true;
            }
        }
        Timber.i("Initial network state: wifi=%s (validated=%s), cellular=%s.",
                this.wifiAvailable, this.wifiValidated, this.cellularAvailable);
    }

    /**
     * Determine which transport the tunnel should be built for, mirroring how Android chooses the
     * default (active) network: Wi-Fi is preferred over cellular, but only once it has validated
     * internet connectivity. Before Wi-Fi validates, Android keeps routing through cellular, so
     * the tunnel must too; otherwise its DNS (resolved from the active network) would not match
     * the network actually carrying the traffic.
     *
     * @return The transport to build the tunnel for, or <code>null</code> if no network is available.
     */
    private NetworkType computeDesiredTransport() {
        if (this.wifiAvailable && this.cellularAvailable) {
            // Both present: follow Android, which only promotes Wi-Fi to the default once it is
            // validated. Until then cellular remains the network carrying the traffic.
            return this.wifiValidated ? WIFI : CELLULAR;
        }
        if (this.wifiAvailable) {
            return WIFI;
        }
        if (this.cellularAvailable) {
            return CELLULAR;
        }
        return null;
    }

    /**
     * Reconcile the tunnel with the current default network.
     * <p>
     * This is the single decision point for every network change. It rebuilds the tunnel only
     * when the transport Android routes through actually changes, never on a mere secondary
     * network appearing or disappearing (e.g. the cellular radio flickering on ColorOS / Oppo
     * power-saving devices while Wi-Fi stays the default), which keeps the tunnel stable and the
     * status-bar VPN icon from blinking.
     */
    private void reconcile() {
        NetworkType desired = computeDesiredTransport();
        if (desired == null) {
            // No usable network at all: stop the tunnel and wait. A later reconcile() restarts it
            // when connectivity returns.
            if (this.currentTransport != null) {
                setCurrentTransport(null);
                Timber.i("No network available, waiting for network…");
                waitForNetVpn();
            }
        } else if (this.currentTransport == null) {
            if (this.stoppedOnPurpose) {
                // The tunnel is down because it was stopped, not because it lost its network.
                // Bringing it back here would undo an explicit stop, which is the user's to undo.
                return;
            }
            // Connectivity (re)gained while the tunnel was down: bring it up now. Reset the
            // throttler as this is a real connectivity-restored event, not a reconnection storm.
            setCurrentTransport(desired);
            Timber.i("Network available (%s), connecting VPN.", desired);
            this.vpnWorker.resetThrottle();
            reconnect();
        } else if (desired != this.currentTransport) {
            // The default network switched transport (e.g. cellular ↔ Wi-Fi). The tunnel's DNS is
            // resolved from the active network, so rebuild it to match; otherwise it keeps
            // forwarding DNS to the old network and every query fails with ENETUNREACH. No
            // throttler reset here so rapid flapping is still damped.
            Timber.i("Default network changed from %s to %s, reconnecting VPN.", this.currentTransport, desired);
            setCurrentTransport(desired);
            reconnect();
        }
        // else: the tunnel is already on the right transport. Nothing to do.
    }

    private void setNetworkAvailable(NetworkType type, boolean available) {
        if (type == WIFI) {
            this.wifiAvailable = available;
            if (!available) {
                // A gone Wi-Fi network is no longer validated.
                this.wifiValidated = false;
            }
        } else {
            this.cellularAvailable = available;
        }
        reconcile();
    }

    private void setWifiValidated(boolean validated) {
        if (validated != this.wifiValidated) {
            this.wifiValidated = validated;
            Timber.d("Wi-Fi validation changed: %s", validated);
            reconcile();
        }
    }

    /**
     * React to the DNS servers of a network changing.
     * <p>
     * {@link #reconcile()} only rebuilds the tunnel when the <em>transport</em> changes, so a DNS
     * change on the same transport (a DHCP lease renewal pushing a new resolver, a same-SSID roam,
     * a captive-portal login swapping the DNS) would otherwise leave the tunnel forwarding to a
     * resolver that may have stopped answering. This detects that case and rebuilds the tunnel,
     * which re-reads the DNS from the active network.
     * <p>
     * It is deliberately conservative to avoid churn: it acts only for the transport actually
     * carrying the tunnel, ignores the empty lists seen mid-transition, merely records the first
     * observation unless the tunnel is stuck on the fallback resolver, and rebuilds only when the
     * <em>effective</em> DNS server set genuinely differs. The effective set is the raw list filtered through the same rule
     * {@link DnsServerMapper} uses at establish time (IPv6 resolvers are dropped when IPv6 is
     * disabled), so a routes/MTU-only change or an IPv6 churn the tunnel never forwards to leaves
     * the set equal and does not rebuild. Every rebuild goes through the worker's connection
     * throttler, so even a pathologically flapping resolver is damped rather than looping.
     *
     * @param type       The transport of the network whose link properties changed.
     * @param dnsServers The network's current DNS servers.
     */
    private void onDnsServersMaybeChanged(NetworkType type, List<InetAddress> dnsServers) {
        // Only the transport actually carrying the tunnel matters; ignore secondary networks and,
        // implicitly, everything while the tunnel is down (currentTransport is then null).
        if (type != this.currentTransport) {
            return;
        }
        // Ignore transient empty lists (seen mid-transition) so a flicker to "no DNS" and back does
        // not bounce the tunnel; a real resolver change reports a non-empty list.
        if (dnsServers.isEmpty()) {
            return;
        }
        // Compare the servers the tunnel would actually forward to, not the raw list. DnsServerMapper
        // drops IPv6 resolvers when IPv6 is disabled (the default), so an IPv6-only churn on a
        // dual-stack network must not rebuild an IPv4-only tunnel whose effective set is unchanged;
        // conversely, a drop to a list the tunnel cannot use maps to the public fallback here (never
        // empty), so losing the last usable resolver is still seen as a change and rebuilt.
        List<InetAddress> effectiveDnsServers = DnsServerMapper.getEffectiveDnsServers(this, dnsServers);
        if (this.currentTransportDnsServers == null) {
            // First observation for this transport: normally there is nothing to compare against, so
            // just record it. One case does need acting on though: the tunnel may have been
            // established while the network was still reporting no DNS server at all, a gap
            // routinely seen right after a transport switch, in which case it forwards to the public
            // fallback resolver instead of the network's own. These servers landing here are exactly
            // what it was missing, so rebuild on them now rather than leaving the tunnel on the
            // fallback until an app's query fails and forces the rebuild the hard way. Skipped when
            // these servers are themselves only the fallback, which would rebuild for nothing.
            this.currentTransportDnsServers = effectiveDnsServers;
            if (this.vpnWorker.isTunnelUsingFallbackDnsServer()
                    && !DnsServerMapper.isOnlyFallbackDnsServer(effectiveDnsServers)) {
                Timber.i("Tunnel is running on the fallback DNS server, rebuilding on %s DNS servers %s.",
                        type, effectiveDnsServers);
                reconnect();
            }
            return;
        }
        if (new HashSet<>(effectiveDnsServers).equals(new HashSet<>(this.currentTransportDnsServers))) {
            // Same effective DNS servers (order-independent): this LinkProperties change was
            // routes/MTU/etc. or a resolver the tunnel does not forward to.
            return;
        }
        Timber.i("Active %s DNS servers changed (%s -> %s), rebuilding tunnel.",
                type, this.currentTransportDnsServers, effectiveDnsServers);
        this.currentTransportDnsServers = effectiveDnsServers;
        reconnect();
    }

    /**
     * This class receives network change events to monitor network type available.
     *
     * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
     * @see <a href="https://developer.android.com/training/basics/network-ops/reading-network-state#listening-events">Android Developer Documentation</a>
     */
    private class NetworkTypeCallback extends NetworkCallback {
        private final NetworkType monitoredType;

        NetworkTypeCallback(NetworkType monitoredType) {
            this.monitoredType = monitoredType;
        }

        @Override
        public void onAvailable(@NonNull Network network) {
            Timber.d("Network available: %s", this.monitoredType);
            setNetworkAvailable(this.monitoredType, true);
        }

        @Override
        public void onLost(@NonNull Network network) {
            Timber.d("Network lost: %s", this.monitoredType);
            setNetworkAvailable(this.monitoredType, false);
        }

        @Override
        public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
            // Only Wi-Fi validation drives the tunnel decision: Android promotes Wi-Fi to the
            // default network once it validates. Cellular validation is irrelevant: cellular is
            // only ever the fallback when Wi-Fi is not usable.
            if (this.monitoredType == WIFI) {
                setWifiValidated(networkCapabilities.hasCapability(NET_CAPABILITY_VALIDATED));
            }
        }

        @Override
        public void onLinkPropertiesChanged(@NonNull Network network, @NonNull LinkProperties linkProperties) {
            // Catches a DNS-server change that keeps the same transport (DHCP renewal, same-SSID
            // roam, captive-portal login), which reconcile() cannot see. onDnsServersMaybeChanged()
            // filters this down to a genuine change on the tunnel's own transport before rebuilding.
            onDnsServersMaybeChanged(this.monitoredType, linkProperties.getDnsServers());
        }

        @Override
        public void onBlockedStatusChanged(@NonNull Network network, boolean blocked) {
            // Diagnostic only: the system reports it cut (or restored) this app's own access to
            // the network the tunnel forwards through, which turns every forwarded query into
            // EPERM while the network itself looks fine.
            Timber.i("%s network %s: access blocked for this app = %s.", this.monitoredType, network, blocked);
            if (blocked) {
                SystemRestrictionsLog.log(VpnService.this, "Network access blocked.");
            }
        }
    }

    enum NetworkType {
        CELLULAR,
        WIFI,
    }

    /* The handler may only keep a weak reference around, otherwise it leaks */
    private static class MyHandler extends Handler {

        private final WeakReference<Callback> callback;

        MyHandler(Callback callback) {
            super(requireNonNull(Looper.myLooper()));
            this.callback = new WeakReference<>(callback);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            Callback callback = this.callback.get();
            if (callback != null) {
                callback.handleMessage(msg);
            }
            super.handleMessage(msg);
        }
    }
}
