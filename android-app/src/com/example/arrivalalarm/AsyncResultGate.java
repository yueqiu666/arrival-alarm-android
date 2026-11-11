package com.example.arrivalalarm;

/**
 * Monotonic request token used to reject stale async results.
 *
 * <p>Cancelling a Future is not a guarantee that a result has not already
 * been queued. Callers therefore invalidate this gate when work is cancelled
 * or superseded, and check the returned token again before committing UI or
 * business state.</p>
 */
final class AsyncResultGate {
    private long generation;

    synchronized long begin() {
        return ++generation;
    }

    synchronized void invalidate() {
        generation++;
    }

    synchronized boolean isCurrent(long token) {
        return token != 0L && token == generation;
    }
}
