package com.example.arrivalalarm;

/** Bounded retry policy for transient location updates stopping unexpectedly. */
final class LocationRecoveryPolicy {
    static final int MAX_ATTEMPTS = 2;
    static final long FIRST_RETRY_DELAY_MS = 10_000L;
    static final long SECOND_RETRY_DELAY_MS = 30_000L;

    private LocationRecoveryPolicy() {}

    static boolean hasAttemptsRemaining(int attempts) {
        return attempts < MAX_ATTEMPTS;
    }

    static long delayAfterAttempt(int attempt) {
        if (attempt <= 0) throw new IllegalArgumentException("attempt must be positive");
        return attempt == 1 ? FIRST_RETRY_DELAY_MS : SECOND_RETRY_DELAY_MS;
    }
}
