package org.adaway.vpn.dns;

import android.system.StructPollfd;

import java.net.DatagramSocket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.function.Consumer;

import timber.log.Timber;

/**
 * This class represents the running DNS queries queue.<br>
 * This queue is time and space bound.
 *
 * @author Bruce BUJON
 */
public class DnsQueryQueue {
    /**
     * The maximum number of responses to wait for.
     */
    private static final int DNS_MAXIMUM_WAITING = 1024;
    /**
     * The maximum time to wait for the response (in seconds).
     */
    private static final long DNS_TIMEOUT_SEC = 10;
    /**
     * The number of consecutive unanswered queries after which the resolver is considered
     * unresponsive (see {@link #isResolverUnresponsive()}). Deliberately more than one: an
     * occasional dropped UDP packet is normal and must not trigger a reconnect on its own, only a
     * resolver that answers nothing at all across several distinct queries.
     */
    private static final int MAX_CONSECUTIVE_TIMEOUTS = 5;
    /**
     * The packet queue (older packets first, in the queue head).
     * <p>
     * Every access is synchronized on the queue itself. The worker thread that owns this queue
     * can be outlived for a moment by the one it replaces on a network change, because a thread
     * sitting in the native {@code Os.poll()} does not answer an interrupt. Two threads
     * structurally modifying a {@link LinkedList} do not merely race: their unsynchronized
     * removals drive its internal size counter below zero, and from that point every iteration
     * throws {@link IndexOutOfBoundsException}. That state survives reconnections, so the tunnel
     * could be established and then immediately die, over and over, until the app was restarted.
     */
    private final Queue<DnsQuery> queries;
    /**
     * The number of queries that timed out with no reply since the last one that did get a reply
     * (or since the queue was last {@link #clear()}ed). Reset to <code>0</code> as soon as any
     * query is answered, so a resolver that works at all keeps this at <code>0</code> regardless
     * of how quiet the tunnel is otherwise; only a resolver that answers nothing across several
     * distinct queries grows it. Guarded by the same lock as {@link #queries}.
     */
    private int consecutiveTimeouts;

    /**
     * Constructor.
     */
    public DnsQueryQueue() {
        this.queries = new LinkedList<>();
        this.consecutiveTimeouts = 0;
    }

    /**
     * Add DNS query to the queue.
     *
     * @param socket   The socket used to query DNS server.
     * @param callback The callback to call with the query response data.
     */
    public void addQuery(DatagramSocket socket, Consumer<byte[]> callback) {
        DnsQuery query = new DnsQuery(socket, callback);
        synchronized (this.queries) {
            // Apply time constraint by removing timed out queries
            clearTimedOutQueries();
            // Apply space constraint by removing older packet if queue is full
            ensureFreeSpace();
            // Add query to the queue
            this.queries.add(query);
        }
    }

    private void ensureFreeSpace() {
        // Called before the new query is added, so the test is against the bound itself: with a
        // strict comparison the queue settled one query above its stated maximum forever.
        if (this.queries.size() >= DNS_MAXIMUM_WAITING) {
            DnsQuery oldestQuery = this.queries.remove();
            Timber.d("Dropping query due to space constraints: %s.", oldestQuery);
            oldestQuery.close();
        }
    }

    private void clearTimedOutQueries() {
        long now = System.currentTimeMillis() / 1000;
        while (!this.queries.isEmpty() && this.queries.element().isOlderThan(now - DNS_TIMEOUT_SEC)) {
            DnsQuery timedOutQuery = this.queries.remove();
            Timber.d("Query %s timed out.", timedOutQuery);
            timedOutQuery.close();
            this.consecutiveTimeouts++;
        }
    }

    /**
     * Check whether the resolver looks unresponsive: {@link #MAX_CONSECUTIVE_TIMEOUTS} or more
     * distinct queries timed out in a row with no reply to any of them in between.
     * <p>
     * Unlike {@link org.adaway.vpn.worker.VpnWatchdog}'s idle keep-alive (which only proves the
     * local send path still works, since its empty probe is never meant to be answered), this
     * looks at queries real apps actually made and waited on, so it stays quiet on a tunnel that
     * is merely idle and only fires when traffic is being attempted and genuinely going
     * unanswered.
     *
     * @return <code>true</code> if the resolver appears unresponsive, <code>false</code> otherwise.
     */
    public boolean isResolverUnresponsive() {
        synchronized (this.queries) {
            return this.consecutiveTimeouts >= MAX_CONSECUTIVE_TIMEOUTS;
        }
    }

    /**
     * Get the number of pending DNS queries.
     *
     * @return The number of pending DNS queries.
     */
    public int size() {
        synchronized (this.queries) {
            return this.queries.size();
        }
    }

    /**
     * Get the query pollfds.
     *
     * @return The query pollfds.
     */
    public StructPollfd[] getQueryFds() {
        synchronized (this.queries) {
            return this.queries.stream()
                    .map(DnsQuery::getPollfd)
                    .toArray(StructPollfd[]::new);
        }
    }

    /**
     * Handle any responded query.
     */
    public void handleResponses() {
        List<DnsQuery> answered = new ArrayList<>();
        synchronized (this.queries) {
            Iterator<DnsQuery> iterator = this.queries.iterator();
            while (iterator.hasNext()) {
                DnsQuery query = iterator.next();
                if (query.isAnswered()) {
                    iterator.remove();
                    answered.add(query);
                }
            }
            // Proof the resolver is actually answering: whatever run of timeouts came before
            // this does not matter any more.
            if (!answered.isEmpty()) {
                this.consecutiveTimeouts = 0;
            }
        }
        // Reading the datagram and handing it to the packet proxy happens outside the lock: it
        // is the only part of this class that does I/O, and nothing it calls comes back here.
        for (DnsQuery query : answered) {
            query.handleResponse();
        }
    }

    /**
     * Drop every pending query and close its socket.
     * <p>
     * Called when a tunnel is established. The queries still waiting belong to the previous
     * tunnel, were sent over a network that no longer carries their answers, and would
     * otherwise sit here holding a socket open until they time out.
     */
    public void clear() {
        List<DnsQuery> pending;
        synchronized (this.queries) {
            pending = new ArrayList<>(this.queries);
            this.queries.clear();
            // A fresh tunnel deserves a fresh count: any run of timeouts on the previous tunnel
            // says nothing about whether the new one's resolver will answer.
            this.consecutiveTimeouts = 0;
        }
        if (!pending.isEmpty()) {
            Timber.d("Dropping %d query(ies) left from the previous tunnel.", pending.size());
        }
        for (DnsQuery query : pending) {
            query.close();
        }
    }
}
