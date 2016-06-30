package test.filter.impl;

import test.filter.api.Filter;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This implementation focuses on fast fail when the window is full (via volatile variable)
 * and uses AtomicInteger to perform both locking and state versioning.
 *
 * <p>There is no any guarantee on fairness for this implementation. At high contention some threads may get biased results.
 *
 * <p>Note: this implementation will not work for dates before 1970/01/01 (Unix epoch).
 * Support of negative timestamps needs few adjustments for initial values.
 *
 * @author Alexei Osipov
 */
public class AtomicLockFilter implements Filter {
    // Indicates that PREVIOUS completed lock operation failed to add new signal to the window.
    private static final int FAIL_FLAG = 0b01;
    // Indicates if the lock is acquired.
    private static final int LOCKED_FLAG = 0b10;
    // Multiplier/divider for flags part of "versionAndFlags"
    private static final int FLAG_BASE = 10;

    private final int limit; // Max allowed number of signals in window
    private final long windowSizeMs; // Window size in milliseconds

    private final long[] timestamps; // All stored timestamps

    private volatile long nextFreeSlotTimestamp = 0; // Timestamp of next moment of time when free slot will be available.
    private int nextIndex = 0; // Index of next slot in array.

    // Contains complex value that represents 3 separate logic values:
    // * state version
    // * flag that indicates if lock is acquired now or not (see LOCKED_FLAG)
    // * flag that indicates if last lock operation resulted in window shifting or not (see FAIL_FLAG)
    // Value format: VERSION * FLAG_BASE + FLAGS
    // where FLAGS are combination of two binary flags: FAIL_FLAG and LOCKED_FLAG (see above)

    // Usage rules:
    // * Each time when changed version must be increased.
    // * If value has LOCKED_FLAG set then only the thread that actually set that flag may change value.
    // * If thread sets LOCKED_FLAG then it is responsible to clear LOCKED_FLAG as next action.
    // * If thread sets LOCKED_FLAG then it must leave FAIL_FLAG unchanged in that operation.
    // * If thread clears LOCKED_FLAG then it must update FAIL_FLAG according to result of attempt to add new value to the window.
    private final AtomicInteger versionAndFlags = new AtomicInteger(0);

    /**
     * Creates filter that allows {@code limit} requests per 1 minute.
     * @param limit allowed number requests per minute
     */
    public AtomicLockFilter(int limit) {
        this(limit, 1, TimeUnit.MINUTES);
    }

    public AtomicLockFilter(int limit, int windowLength, TimeUnit windowLengthTimeUnit) {
        this.windowSizeMs = windowLengthTimeUnit.toMillis(windowLength);
        this.limit = limit;
        this.timestamps = new long[this.limit];
    }
    @Override
    public boolean isSignalAllowed() {
        long currentTimestamp = System.currentTimeMillis();
        if (nextFreeSlotTimestamp > currentTimestamp) {
            // No free slots in window
            return false;
        }

        int initialVersionAndFlag = versionAndFlags.get();
        int currentVersionAndFlag = initialVersionAndFlag;
        while (true) {
            // If we see that state and version was changed and new state is marked as failed
            // => other thread already checked current situation and failed
            // => we don't have to try ourselves and we should just return false.
            if (currentVersionAndFlag != initialVersionAndFlag && isFail(currentVersionAndFlag)) {
                // Other thread had failed attempt.
                return false;
            }

            // Check if lock is acquired by other thread
            if (!isLocked(currentVersionAndFlag)) {
                // Not locked, attempt to lock
                int nextLockedVersion = buildNewLockedVersion(currentVersionAndFlag);
                if (versionAndFlags.compareAndSet(currentVersionAndFlag, nextLockedVersion)) {
                    // Got lock

                    // Check if we can push new value to the window
                    long oldValueInTheWindow = timestamps[nextIndex];
                    long windowLeaveTimestamp = oldValueInTheWindow + windowSizeMs + 1;
                    boolean signalAllowed = currentTimestamp >= windowLeaveTimestamp;
                    if (signalAllowed) {
                        timestamps[nextIndex] = currentTimestamp;
                        nextIndex = (nextIndex + 1) % limit;
                        nextFreeSlotTimestamp = timestamps[nextIndex];
                    } else {
                        nextFreeSlotTimestamp = windowLeaveTimestamp;
                    }

                    // Release lock
                    int nextUnlocked = buildNextUnlockedVersion(currentVersionAndFlag, signalAllowed);
                    boolean releaseSuccess = versionAndFlags.compareAndSet(nextLockedVersion, nextUnlocked);
                    if (!releaseSuccess) {
                        throw new IllegalStateException("Other thread changed state of locked lock");
                    }
                    return signalAllowed;
                }
            }
            currentVersionAndFlag = versionAndFlags.get();
        }
    }

    /**
     * Increments version and sets LOCKED_FLAG
     */
    private int buildNewLockedVersion(int versionAndFlags) {
        return incrementVersionPart(versionAndFlags) + LOCKED_FLAG + (versionAndFlags % FLAG_BASE & FAIL_FLAG);
    }

    /**
     * Increments version, clears LOCKED_FLAG and updates FAIL_FLAG according to the operation result.
     */
    private int buildNextUnlockedVersion(int versionAndFlags, boolean signalAllowed) {
        return incrementVersionPart(versionAndFlags) + (!signalAllowed ? FAIL_FLAG : 0);
    }

    /**
     * @return true if LOCKED_FLAG is set
     */
    private boolean isLocked(int versionAndFlag) {
        return (versionAndFlag % FLAG_BASE & LOCKED_FLAG) > 0;
    }

    /**
     * @return true if FAIL_FLAG is set
     */
    private boolean isFail(int versionAndFlag) {
        return (versionAndFlag % FLAG_BASE & FAIL_FLAG) > 0;
    }

    /**
     * Increments version part of "versionAndFlags" value.
     */
    private int incrementVersionPart(int versionAndFlags) {
        int newVersion = versionAndFlags / FLAG_BASE + 1;
        // Avoid MAX_INT overflow
        return (newVersion % 100000000) * FLAG_BASE;
    }
}
