package test.filter.impl;

import test.filter.api.Filter;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Simple implementation of {@link Filter} that uses {@link ArrayBlockingQueue}.
 *
 * This implementation aims to correctly implement "sliding window" rate limiting approach
 * so at any given fixed size interval there is no more than specified number of allowed signals.
 *
 * @author Alexei Osipov
 */
public class ArrayBlockingQueueFilter implements Filter {
    private final long windowSizeMs;

    // Contains timestamps of accepted signals. Not guaranteed to be in strict order due to multithreaded access.
    private final ArrayBlockingQueue<Long> timestampQueue;

    private final ReentrantLock cleanupLock = new ReentrantLock();

    /**
     * Creates filter that allows {@code limit} requests per 1 minute.
     * @param limit allowed number requests per minute
     */
    public ArrayBlockingQueueFilter(int limit) {
        this(limit, 1, TimeUnit.MINUTES);
    }

    public ArrayBlockingQueueFilter(int limit, int windowLength, TimeUnit windowLengthTimeUnit) {
        this.windowSizeMs = windowLengthTimeUnit.toMillis(windowLength);
        this.timestampQueue = new ArrayBlockingQueue<Long>(limit);
    }

    @Override
    public boolean isSignalAllowed() {
        long currentTimestamp = System.currentTimeMillis();


        boolean added = timestampQueue.offer(currentTimestamp);
        if (added) {
            return true;
        }

        boolean gotFreePlace = tryCleanUp(currentTimestamp);
        if (gotFreePlace) {
            // We got some free place in queue so we can try to add pending element to the queue

            // Updates timestamp because cleanup process Note: that not really necessary but should not hurt.
            currentTimestamp = System.currentTimeMillis();
            return timestampQueue.offer(currentTimestamp);
        }

        return false;
    }

    private boolean tryCleanUp(long currentTimestamp) {
        // If we failed to get this lock then we can just skip clean up attempt. There is no reason to wait if the lock is already taken by other thread.
        boolean gotLock = cleanupLock.tryLock();
        if (gotLock) {
            long oldTimestamp = currentTimestamp - windowSizeMs;
            boolean gotFreePlace = false;
            try {
                while (true) {
                    Long peek = timestampQueue.peek();
                    if (peek == null) {
                        // Drained all queue
                        return true;
                    }
                    if (peek < oldTimestamp) {
                        timestampQueue.remove();
                        gotFreePlace = true;
                    } else {
                        return gotFreePlace;
                    }
                }
            } finally {
                cleanupLock.unlock();
            }
        }

        return false;
    }
}
