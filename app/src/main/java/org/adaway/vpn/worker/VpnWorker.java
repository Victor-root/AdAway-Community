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
package org.adaway.vpn.worker;

import static android.system.OsConstants.ENETUNREACH;
import static android.system.OsConstants.EPERM;
import static android.system.OsConstants.POLLIN;
import static android.system.OsConstants.POLLOUT;
import static org.adaway.vpn.VpnStatus.RECONNECTING_NETWORK_ERROR;
import static org.adaway.vpn.VpnStatus.RUNNING;
import static org.adaway.vpn.VpnStatus.STARTING;
import static org.adaway.vpn.VpnStatus.STOPPED;
import static org.adaway.vpn.VpnStatus.STOPPING;
import static org.adaway.vpn.worker.VpnBuilder.establish;

import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.system.Os;
import android.system.StructPollfd;

import org.adaway.helper.PreferenceHelper;
import org.adaway.vpn.VpnService;
import org.adaway.vpn.dns.DnsPacketProxy;
import org.adaway.vpn.dns.DnsQueryQueue;
import org.adaway.vpn.dns.DnsServerMapper;
import org.pcap4j.packet.IpPacket;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import timber.log.Timber;

// TODO Write document
// TODO It is thread safe
// TODO Rework status notification
// TODO Improve exception handling in work()
public class VpnWorker implements DnsPacketProxy.EventLoop {
    /**
     * Maximum packet size is constrained by the MTU, which is given as a signed short.
     */
    private static final int MAX_PACKET_SIZE = Short.MAX_VALUE;

    /**
     * The VPN service, also used as {@link android.content.Context}.
     */
    private final VpnService vpnService;
    /**
     * The queue of packets to send to the device. Concurrent for the same reason the DNS query
     * queue is synchronized: the worker being replaced on a network change can still be running.
     */
    private final Queue<byte[]> deviceWrites;
    /**
     * The queue of DNS queries.
     */
    private final DnsQueryQueue dnsQueryQueue;
    // The mapping between fake and real dns addresses
    private final DnsServerMapper dnsServerMapper;
    // The object where we actually handle packets.
    private final DnsPacketProxy dnsPacketProxy;

    // TODO Comment
    private final VpnConnectionThrottler connectionThrottler;
    private final VpnConnectionMonitor connectionMonitor;

    // Watch dog that checks our connection is alive.
    private final VpnWatchdog vpnWatchDog;

    /**
     * The VPN worker executor (<code>null</code> if not started).
     */
    private final AtomicReference<ExecutorService> executor;
    /**
     * The VPN network interface, (<code>null</code> if not established).
     */
    private final AtomicReference<ParcelFileDescriptor> vpnNetworkInterface;
    /**
     * Whether an intentional stop is in progress. Set by {@link #stop()} before the tunnel
     * is force-closed so the worker loop can tell the resulting read failure (EBADF on the
     * blocking device read) apart from a genuine network error: on a stop it must exit
     * cleanly instead of reconnecting. Without this, pausing the VPN closed the tunnel, the
     * blocked native read threw EBADF, and the loop "reconnected", bringing the VPN back up
     * right after the user paused it (so the first tap appeared to do nothing).
     */
    private final AtomicBoolean stopping;
    /**
     * The number of the current run, bumped by every {@link #start()}.
     * <p>
     * A worker whose number is no longer the current one retires instead of reconnecting.
     * Shutting an executor down only interrupts its threads, and a worker sitting in the native
     * {@code Os.poll()} does not answer an interrupt: without this the worker being replaced on
     * a network change kept looping alongside its replacement, and the two of them corrupted the
     * queues they share beyond repair.
     */
    private final AtomicInteger runNumber;

    /**
     * Constructor.
     *
     * @param vpnService The VPN service, also used as {@link android.content.Context}.
     */
    public VpnWorker(VpnService vpnService) {
        this.vpnService = vpnService;
        this.deviceWrites = new ConcurrentLinkedQueue<>();
        this.dnsQueryQueue = new DnsQueryQueue();
        this.dnsServerMapper = new DnsServerMapper();
        this.dnsPacketProxy = new DnsPacketProxy(this, this.dnsServerMapper);
        this.connectionThrottler = new VpnConnectionThrottler();
        this.connectionMonitor = new VpnConnectionMonitor(this.vpnService);
        this.vpnWatchDog = new VpnWatchdog();
        this.executor = new AtomicReference<>(null);
        this.vpnNetworkInterface = new AtomicReference<>(null);
        this.stopping = new AtomicBoolean(false);
        this.runNumber = new AtomicInteger(0);
    }

    /**
     * Start the VPN worker.
     * Kill the current worker and restart it if already running.
     */
    public void start() {
        Timber.d("Starting VPN thread…");
        // Retire the previous run before creating its replacement. Both share this worker's
        // packet queues, so they must not overlap; closing the tunnel unblocks whichever
        // native call the old one is sitting in, and the run number tells it to stop rather
        // than reconnect once it gets there.
        int runNumber = this.runNumber.incrementAndGet();
        forceCloseTunnel();
        setExecutor(null);
        // Clear any pending stop flag so a fresh worker reconnects normally on errors.
        this.stopping.set(false);
        // Re-arm the monitor: if a previous monitor cycle called stop() (running=false) before
        // triggering this restart via VpnServiceControls.start(), the new monitor task would
        // exit immediately on the while(running.get()) check without this reset.
        this.connectionMonitor.activate();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        executor.submit(() -> work(runNumber));
        executor.submit(this.connectionMonitor::monitor);
        setExecutor(executor);
        Timber.i("VPN thread started.");
    }

    /**
     * Reset the connection throttler so that the next {@link #start()} establishes the
     * tunnel without delay. Use this on explicit user actions (tile, notification,
     * home toggle, autostart at boot) so the throttler, which is meant to damp
     * automatic reconnection storms, does not punish the user with a 60-128s wait.
     */
    public void resetThrottle() {
        this.connectionThrottler.reset();
    }

    /**
     * Check whether the running tunnel forwards to the public fallback DNS server rather than to a
     * resolver the network reported, which happens when it was established during the gap where the
     * network is up but has not published its DNS servers yet.
     *
     * @return <code>true</code> if the tunnel runs on the fallback resolver, <code>false</code>
     * otherwise.
     */
    public boolean isTunnelUsingFallbackDnsServer() {
        return this.dnsServerMapper.isUsingFallbackDnsServer();
    }

    /**
     * Stop the VPN worker.
     */
    public void stop() {
        Timber.d("Stopping VPN thread.");
        // Mark the stop BEFORE closing the tunnel so the worker loop treats the resulting
        // read failure as an intentional shutdown and does not reconnect.
        this.stopping.set(true);
        this.connectionMonitor.reset();
        forceCloseTunnel();
        setExecutor(null);
        Timber.i("VPN thread stopped.");
    }

    /**
     * Keep track of the worker executor.<br>
     * Shut the previous one down in exists.
     *
     * @param executor The new worker executor, <code>null</code> if no executor any more.
     */
    private void setExecutor(ExecutorService executor) {
        ExecutorService oldExecutor = this.executor.getAndSet(executor);
        if (oldExecutor != null) {
            Timber.d("Shutting down VPN executor…");
            oldExecutor.shutdownNow();
            Timber.d("VPN executor shut down.");
        }
    }

    /**
     * Force close the tunnel connection.
     */
    private void forceCloseTunnel() {
        ParcelFileDescriptor networkInterface = this.vpnNetworkInterface.get();
        if (networkInterface != null) {
            try {
                networkInterface.close();
            } catch (IOException e) {
                Timber.tag("Failed to close VPN network interface.").w(e);
            }
        }
    }

    /**
     * Check whether this run has been replaced by a newer one and should therefore stop.
     *
     * @param runNumber The number of the run asking.
     * @return <code>true</code> if a newer run has taken over.
     */
    private boolean isSuperseded(int runNumber) {
        if (this.runNumber.get() == runNumber) {
            return false;
        }
        Timber.d("VPN run %d has been replaced, exiting.", runNumber);
        return true;
    }

    private void work(int runNumber) {
        Timber.d("Starting work…");
        // Initialize context
        this.dnsPacketProxy.initialize(this.vpnService);
        // Initialize the watchdog
        this.vpnWatchDog.initialize(PreferenceHelper.getVpnWatchdogEnabled(this.vpnService));
        // Try connecting the vpn continuously
        boolean superseded = false;
        while (true) {
            if (isSuperseded(runNumber)) {
                superseded = true;
                break;
            }
            try {
                this.connectionThrottler.throttle();
                this.vpnService.notifyVpnStatus(STARTING);
                runVpn();
                if (isSuperseded(runNumber)) {
                    superseded = true;
                    break;
                }
                Timber.i("Told to stop");
                this.vpnService.notifyVpnStatus(STOPPING);
                break;
            } catch (InterruptedException e) {
                Timber.d(e, "Failed to wait for connexion throttling.");
                Thread.currentThread().interrupt();
                // Shutting the executor down to make room for a newer run interrupts here too.
                superseded = isSuperseded(runNumber);
                break;
            } catch (VpnNetworkException | IOException e) {
                // A newer run force-closed this tunnel to take over, which surfaces here as a
                // read failure. Leave without a word: the status belongs to that newer run.
                if (isSuperseded(runNumber)) {
                    superseded = true;
                    break;
                }
                // An intentional stop force-closes the tunnel, which unblocks the native
                // device read with EBADF. That is not a network error: exit cleanly instead
                // of reconnecting, otherwise the VPN comes straight back up after the user
                // paused it (requiring a second tap).
                if (this.stopping.get()) {
                    Timber.d("Tunnel closed by stop request, exiting VPN thread.");
                    break;
                }
                Timber.w(e, "Network exception in vpn thread, reconnecting…");
                // If an exception was thrown, notify status and try again
                this.vpnService.notifyVpnStatus(RECONNECTING_NETWORK_ERROR);
            } catch (RuntimeException e) {
                if (isSuperseded(runNumber)) {
                    superseded = true;
                    break;
                }
                // Anything unexpected used to escape this loop and kill the thread outright,
                // which left the tunnel down while the notification, the tile and the home
                // screen all kept reporting it as running. Stop deliberately instead, so the
                // STOPPED status below is actually sent and the state the user sees is true.
                // Deliberately not retried: an unknown failure is not something to loop on,
                // and the heartbeat restarts the VPN if the user still wants it on.
                Timber.e(e, "Unexpected error in vpn thread, stopping.");
                break;
            }
        }
        // A run that has been replaced stays quiet: announcing STOPPED here would contradict
        // the run that took over and leave the UI claiming the VPN is down while it is up.
        if (!superseded) {
            this.vpnService.notifyVpnStatus(STOPPED);
        }
        Timber.d("Exiting work.");
    }

    private void runVpn() throws IOException, VpnNetworkException {
        // Allocate the buffer for a single packet.
        byte[] packet = new byte[MAX_PACKET_SIZE];
        // Whatever is still queued was sent through the previous tunnel and will never be
        // answered through this one, so it goes rather than waiting out its timeout.
        this.dnsQueryQueue.clear();

        // Authenticate and configure the virtual network interface.
        try (ParcelFileDescriptor pfd = establish(this.vpnService, this.dnsServerMapper);
             // Read and write views of the tunnel device
             FileInputStream inputStream = new FileInputStream(pfd.getFileDescriptor());
             FileOutputStream outputStream = new FileOutputStream(pfd.getFileDescriptor())) {
            // Store reference to network interface to close it externally on demand
            this.vpnNetworkInterface.set(pfd);
            // Initialize connection monitor
            this.connectionMonitor.initialize();

            // Update address to ping with default DNS server
            this.vpnWatchDog.setTarget(this.dnsServerMapper.getDefaultDnsServerAddress());

            // Now we are connected. Set the flag and show the message.
            this.vpnService.notifyVpnStatus(RUNNING);

            // We keep forwarding packets till something goes wrong.
            boolean deviceOpened = true;
            while (deviceOpened) {
                deviceOpened = doOne(inputStream, outputStream, packet);
            }
        }
    }

    private boolean doOne(FileInputStream inputStream, FileOutputStream fileOutputStream, byte[] packet)
            throws IOException, VpnNetworkException {
        // Create poll FD on tunnel
        StructPollfd deviceFd = new StructPollfd();
        deviceFd.fd = inputStream.getFD();
        deviceFd.events = (short) POLLIN;
        if (!this.deviceWrites.isEmpty()) {
            deviceFd.events |= (short) POLLOUT;
        }
        // Create poll FD on each DNS query socket
        StructPollfd[] queryFds = this.dnsQueryQueue.getQueryFds();
        StructPollfd[] polls = new StructPollfd[1 + queryFds.length];
        polls[0] = deviceFd;
        System.arraycopy(queryFds, 0, polls, 1, queryFds.length);
        boolean deviceReadyToWrite;
        boolean deviceReadyToRead;
        try {
            Timber.d("doOne: Polling %d file descriptors.", polls.length);
            int numberOfEvents = Os.poll(polls, this.vpnWatchDog.getPollTimeout());
            // No events means the tunnel was idle for the whole poll timeout. That is a normal,
            // healthy state (no app is resolving anything right now), so the watchdog only sends a
            // keep-alive probe here; it no longer treats idle as a dead connection.
            if (numberOfEvents == 0) {
                this.vpnWatchDog.handleTimeout();
                return true;
            }
            deviceReadyToWrite = (deviceFd.revents & POLLOUT) != 0;
            deviceReadyToRead = (deviceFd.revents & POLLIN) != 0;
        } catch (ErrnoException e) {
            throw new IOException("Failed to wait for event on file descriptors. Error number: " + e.errno, e);
        }

        // Need to do this before reading from the device, otherwise a new insertion there could
        // invalidate one of the sockets we want to read from either due to size or time out
        // constraints
        this.dnsQueryQueue.handleResponses();
        if (this.dnsQueryQueue.isResolverUnresponsive()) {
            // The local send path is fine (nothing threw), but real queries keep going
            // unanswered: the tunnel looks up but is not actually working. Force a reconnect
            // through the same path a network error takes, which re-reads the DNS servers from
            // the active network from scratch instead of insisting on the one that stopped
            // answering.
            throw new VpnNetworkException("No DNS reply received for several consecutive queries; the resolver looks unresponsive.");
        }
        if (deviceReadyToWrite) {
            writeToDevice(fileOutputStream);
        }
        if (deviceReadyToRead) {
            return readPacketFromDevice(inputStream, packet) != -1;
        }
        return true;
    }

    private void writeToDevice(FileOutputStream fileOutputStream) throws IOException {
        try {
            // Drain on the value, not on isEmpty(): the queue is concurrent, so a poll() that
            // passed the emptiness check can still come back with nothing.
            byte[] ipPacketData;
            while ((ipPacketData = this.deviceWrites.poll()) != null) {
                fileOutputStream.write(ipPacketData);
            }
        } catch (IOException e) {
            throw new IOException("Failed to write to tunnel output stream.", e);
        }
    }

    private int readPacketFromDevice(FileInputStream inputStream, byte[] packet) throws IOException {
        Timber.d("Read a packet from device.");
        // Read the outgoing packet from the input stream.
        int length = inputStream.read(packet);
        if (length < 0) {
            // TODO Stream closed. Is there anything else to do?
            Timber.d("Tunnel input stream closed.");
        } else if (length == 0) {
            Timber.d("Read empty packet from tunnel.");
        } else {
            byte[] readPacket = Arrays.copyOf(packet, length);
            dnsPacketProxy.handleDnsRequest(readPacket);
        }
        return length;
    }

    @Override
    public void forwardPacket(DatagramPacket packet) throws IOException {
        try (DatagramSocket dnsSocket = new DatagramSocket()) {
            this.vpnService.protect(dnsSocket);
            dnsSocket.send(packet);
        } catch (IOException e) {
            throw new IOException("Failed to forward packet.", e);
        }
    }

    @Override
    public void forwardPacket(DatagramPacket outPacket, Consumer<byte[]> callback) throws IOException {
        DatagramSocket dnsSocket = null;
        try {
            dnsSocket = new DatagramSocket();
            // Packets to be sent to the real DNS server will need to be protected from the VPN
            this.vpnService.protect(dnsSocket);
            // Bind the socket to the resolver we are about to query, AFTER protect() so the route
            // lookup does not go through the tunnel. The socket is used for this single query and
            // then closed, so this costs nothing and makes the kernel drop any datagram coming
            // from another address: without it, receive() below accepts a reply from anyone who
            // can reach this ephemeral port, and the response is then handed back to the calling
            // app as if the real resolver had answered. connect() reports a failure as a
            // SocketException, which is an IOException, so it takes the exact same path as a
            // failing send() already did.
            dnsSocket.connect(outPacket.getSocketAddress());
            dnsSocket.send(outPacket);
            // Enqueue DNS query
            this.dnsQueryQueue.addQuery(dnsSocket, callback);
        } catch (IOException e) {
            if (dnsSocket != null) {
                dnsSocket.close();
            }
            if (e.getCause() instanceof ErrnoException) {
                ErrnoException errnoExc = (ErrnoException) e.getCause();
                if ((errnoExc.errno == ENETUNREACH) || (errnoExc.errno == EPERM)) {
                    throw new IOException("Cannot send message:", e);
                }
            }
            Timber.w(e, "handleDnsRequest: Could not send packet to upstream");
        }
    }

    @Override
    public void queueDeviceWrite(IpPacket ipOutPacket) {
        byte[] rawData = ipOutPacket.getRawData();
        // TODO Check why data could be null
        if (rawData != null) {
            this.deviceWrites.add(rawData);
        }
    }
}
