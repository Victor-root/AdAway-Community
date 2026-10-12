package org.adaway.model.source;

import static org.adaway.db.entity.ListType.ALLOWED;
import static org.adaway.db.entity.ListType.BLOCKED;
import static org.adaway.db.entity.ListType.REDIRECTED;
import static org.adaway.util.Constants.BOGUS_IPV4;
import static org.adaway.util.Constants.LOCALHOST_HOSTNAME;
import static org.adaway.util.Constants.LOCALHOST_IPV4;
import static org.adaway.util.Constants.LOCALHOST_IPV6;

import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.db.entity.ListType;
import org.adaway.util.RegexUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import timber.log.Timber;

/**
 * This class is an {@link HostsSource} loader.<br>
 * It parses a source and loads it to database.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
class SourceLoader {
    private static final String TAG = "SourceLoader";
    private static final String END_OF_QUEUE_MARKER = "#EndOfQueueMarker";
    private static final int INSERT_BATCH_SIZE = 100;
    /**
     * Bound on the reader and parser hand-off queues. Large enough that the stages never starve
     * each other on a normal source, small enough that an oversized one cannot be buffered whole
     * into memory.
     */
    private static final int QUEUE_CAPACITY = 10_000;
    /**
     * How long a stage waits to hand over its end-of-queue marker before giving up.
     */
    private static final long MARKER_TIMEOUT_SECONDS = 60;
    /**
     * Upper bound on a single source load, so a stalled stage fails the sync instead of leaving
     * the caller blocked on it forever.
     */
    private static final long LOAD_TIMEOUT_MINUTES = 30;
    private static final String HOSTS_PARSER = "^\\s*([^#\\s]+)\\s+([^#\\s]+).*$";
    static final Pattern HOSTS_PARSER_PATTERN = Pattern.compile(HOSTS_PARSER);

    private final HostsSource source;

    SourceLoader(HostsSource hostsSource) {
        this.source = hostsSource;
    }

    void parse(BufferedReader reader, HostListItemDao hostListItemDao) throws IOException {
        // Clear current hosts
        hostListItemDao.clearSourceHosts(this.source.getId());
        // Create batch
        int parserCount = 3;
        LinkedBlockingQueue<String> hostsLineQueue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
        LinkedBlockingQueue<HostListItem> hostsListItemQueue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
        SourceReader sourceReader = new SourceReader(reader, hostsLineQueue, parserCount);
        ItemInserter inserter = new ItemInserter(hostsListItemQueue, hostListItemDao, parserCount);
        ExecutorService executorService = Executors.newFixedThreadPool(
                parserCount + 2,
                r -> new Thread(r, TAG)
        );
        executorService.execute(sourceReader);
        for (int i = 0; i < parserCount; i++) {
            executorService.execute(new HostListItemParser(this.source, hostsLineQueue, hostsListItemQueue));
        }
        Future<Integer> inserterFuture = executorService.submit(inserter);
        boolean insertFailed = false;
        try {
            Integer inserted = inserterFuture.get(LOAD_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            Timber.i("%s host list items inserted.", inserted);
        } catch (ExecutionException e) {
            Timber.w(e, "Failed to parse hosts sources.");
            insertFailed = true;
        } catch (TimeoutException e) {
            inserterFuture.cancel(true);
            Timber.w(e, "Timed out loading hosts source.");
            insertFailed = true;
        } catch (InterruptedException e) {
            inserterFuture.cancel(true);
            Timber.w(e, "Interrupted while parsing sources.");
            Thread.currentThread().interrupt();
            insertFailed = true;
        }
        executorService.shutdown();
        // The read, or the insert, failing part way through used to be logged and forgotten:
        // the source was left holding whatever had already arrived, then stamped as
        // successfully synced, so a download cut short (a hostile network only has to reset
        // the connection) or an insert cut short (the OS reclaiming a background thread, a
        // WorkManager-cancelled sync, an unexpected database error) silently disabled that
        // source until something else forced a refresh. Report both instead, so the caller
        // treats it as the failed sync it is and retries later.
        //
        // The three catches above only logged a warning for the insert side, unlike the read
        // side just below: a failure there left ItemInserter.call() returning a normal,
        // untagged partial count indistinguishable from a complete one, which is how a
        // production device ended up with a source frozen at roughly half its real size,
        // and another at zero, both marked current.
        Throwable readFailure = sourceReader.failure;
        if (readFailure != null) {
            throw new IOException("Failed to read the whole hosts source.", readFailure);
        }
        if (insertFailed) {
            throw new IOException("Failed to insert the whole hosts source.");
        }
    }

    /**
     * Enqueue an end-of-queue marker, which the downstream stage needs in order to terminate.
     * Waits, because the queues are bounded, but never indefinitely: if the stage that should be
     * draining has already died there would be nobody to make room, and blocking forever would
     * hang the sync instead of failing it.
     *
     * @param queue The bounded queue to put into.
     * @param value The marker to enqueue.
     * @param <T>   The queue element type.
     */
    private static <T> void putMarker(BlockingQueue<T> queue, T value) {
        try {
            if (!queue.offer(value, MARKER_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                Timber.w("Timed out handing over the end of queue marker.");
            }
        } catch (InterruptedException e) {
            Timber.w(e, "Interrupted while handing over the end of queue marker.");
            Thread.currentThread().interrupt();
        }
    }

    private static class SourceReader implements Runnable {
        private final BufferedReader reader;
        private final BlockingQueue<String> queue;
        private final int parserCount;

        private SourceReader(BufferedReader reader, BlockingQueue<String> queue, int parserCount) {
            this.reader = reader;
            this.queue = queue;
            this.parserCount = parserCount;
        }

        /**
         * The failure that interrupted the read, if any. Recorded so {@link #parse} can refuse
         * to treat a partially read source as a complete one.
         */
        private volatile Throwable failure;

        @Override
        public void run() {
            try {
                // put() rather than add(): the queue is bounded now, so the reader waits for the
                // parsers instead of pulling the whole file into memory ahead of them. An
                // oversized source used to be materialised entirely as Strings, then again as
                // items in the next queue, which is an out-of-memory kill a hostile blocklist
                // host could trigger on its own.
                String line;
                while ((line = this.reader.readLine()) != null) {
                    this.queue.put(line);
                }
            } catch (InterruptedException e) {
                Timber.w(e, "Interrupted while reading hosts source.");
                this.failure = e;
                Thread.currentThread().interrupt();
            } catch (Throwable t) {
                Timber.w(t, "Failed to read hosts source.");
                this.failure = t;
            } finally {
                // Send end of queue marker to parsers
                for (int i = 0; i < this.parserCount; i++) {
                    putMarker(this.queue, END_OF_QUEUE_MARKER);
                }
            }
        }

    }

    private static class HostListItemParser implements Runnable {
        private final HostsSource source;
        private final BlockingQueue<String> lineQueue;
        private final BlockingQueue<HostListItem> itemQueue;

        private HostListItemParser(HostsSource source, BlockingQueue<String> lineQueue, BlockingQueue<HostListItem> itemQueue) {
            this.source = source;
            this.lineQueue = lineQueue;
            this.itemQueue = itemQueue;
        }

        @Override
        public void run() {
            boolean allowedList = this.source.isAllowEnabled();
            boolean endOfSource = false;
            while (!endOfSource) {
                try {
                    String line = this.lineQueue.take();
                    // Check end of queue marker
                    //noinspection StringEquality
                    if (line == END_OF_QUEUE_MARKER) {
                        endOfSource = true;
                        // Send end of queue marker to inserter
                        HostListItem endItem = new HostListItem();
                        endItem.setHost(line);
                        // Must reach the inserter even if this thread is interrupted, or the
                        // inserter waits forever for a completion marker that never arrives.
                        putMarker(this.itemQueue, endItem);
                    } // Check comments
                    else if (line.isEmpty() || line.charAt(0) == '#') {
                        Timber.d("Skip comment: %s.", line);
                    } else {
                        HostListItem item = allowedList ? parseAllowListItem(line) : parseHostListItem(line);
                        if (item != null && isRedirectionValid(item) && isHostValid(item)) {
                            // put(), not add(): the queue is bounded, so add() would throw once
                            // the inserter falls behind.
                            this.itemQueue.put(item);
                        }
                    }
                } catch (InterruptedException e) {
                    Timber.w(e, "Interrupted while parsing hosts list item.");
                    endOfSource = true;
                    Thread.currentThread().interrupt();
                }
            }
        }

        private HostListItem parseHostListItem(String line) {
            Matcher matcher = HOSTS_PARSER_PATTERN.matcher(line);
            if (!matcher.matches()) {
                Timber.d("Does not match: %s.", line);
                return null;
            }
            // Check IP address validity or while list entry (if allowed)
            String ip = matcher.group(1);
            String hostname = matcher.group(2);
            // Skip localhost name
            if (LOCALHOST_HOSTNAME.equals(hostname)) {
                return null;
            }
            // check if ip is 127.0.0.1 or 0.0.0.0
            ListType type;
            if (LOCALHOST_IPV4.equals(ip)
                    || BOGUS_IPV4.equals(ip)
                    || LOCALHOST_IPV6.equals(ip)) {
                type = BLOCKED;
            } else if (this.source.isRedirectEnabled()) {
                type = REDIRECTED;
            } else {
                return null;
            }
            HostListItem item = new HostListItem();
            item.setType(type);
            item.setHost(hostname);
            item.setEnabled(true);
            if (type == REDIRECTED) {
                item.setRedirection(ip);
            }
            item.setSourceId(this.source.getId());
            return item;
        }

        private HostListItem parseAllowListItem(String line) {
            // Extract hostname
            int indexOf = line.indexOf('#');
            if (indexOf != -1) {
                line = line.substring(0, indexOf);
            }
            line = line.trim();
            // Create item
            HostListItem item = new HostListItem();
            item.setType(ALLOWED);
            item.setHost(line);
            item.setEnabled(true);
            item.setSourceId(this.source.getId());
            return item;
        }

        private boolean isRedirectionValid(HostListItem item) {
            return item.getType() != REDIRECTED || RegexUtils.isValidIP(item.getRedirection());
        }

        private boolean isHostValid(HostListItem item) {
            String hostname = item.getHost();
            if (item.getType() == BLOCKED) {
                if (hostname.indexOf('?') != -1 || hostname.indexOf('*') != -1) {
                    return false;
                }
                return RegexUtils.isValidHostname(hostname);
            }
            return RegexUtils.isValidWildcardHostname(hostname);
        }
    }

    private static class ItemInserter implements Callable<Integer> {
        private final BlockingQueue<HostListItem> hostListItemQueue;
        private final HostListItemDao hostListItemDao;
        private final int parserCount;

        private ItemInserter(BlockingQueue<HostListItem> itemQueue, HostListItemDao hostListItemDao, int parserCount) {
            this.hostListItemQueue = itemQueue;
            this.hostListItemDao = hostListItemDao;
            this.parserCount = parserCount;
        }

        @Override
        public Integer call() {
            int inserted = 0;
            int workerStopped = 0;
            HostListItem[] batch = new HostListItem[INSERT_BATCH_SIZE];
            int cacheSize = 0;
            boolean queueEmptied = false;
            while (!queueEmptied) {
                try {
                    HostListItem item = this.hostListItemQueue.take();
                    // Check end of queue marker
                    //noinspection StringEquality
                    if (item.getHost() == END_OF_QUEUE_MARKER) {
                        workerStopped++;
                        if (workerStopped >= this.parserCount) {
                            queueEmptied = true;
                        }
                    } else {
                        batch[cacheSize++] = item;
                        if (cacheSize >= batch.length) {
                            this.hostListItemDao.insert(batch);
                            inserted += cacheSize;
                            cacheSize = 0;
                        }
                    }
                } catch (InterruptedException e) {
                    Timber.w(e, "Interrupted while inserted hosts list item.");
                    queueEmptied = true;
                    Thread.currentThread().interrupt();
                }
            }
            // Flush current batch
            HostListItem[] remaining = new HostListItem[cacheSize];
            System.arraycopy(batch, 0, remaining, 0, remaining.length);
            this.hostListItemDao.insert(remaining);
            inserted += cacheSize;
            // Return number of inserted items
            return inserted;
        }
    }
}
