package com.example.arrivalalarm;

/** Standalone regression check for the bounded location recovery contract. */
public final class LocationRecoveryPolicyTest {
    private LocationRecoveryPolicyTest() {}

    public static void main(String[] args) {
        check(LocationRecoveryPolicy.MAX_ATTEMPTS == 2, "two attempts");
        check(LocationRecoveryPolicy.hasAttemptsRemaining(0), "initial attempt allowed");
        check(LocationRecoveryPolicy.hasAttemptsRemaining(1), "second attempt allowed");
        check(!LocationRecoveryPolicy.hasAttemptsRemaining(2), "retry stops after two attempts");
        check(LocationRecoveryPolicy.delayAfterAttempt(1) == 10_000L, "first backoff");
        check(LocationRecoveryPolicy.delayAfterAttempt(2) == 30_000L, "second backoff");

        ReminderRuntime.Snapshot recovering = ReminderRuntime.Snapshot.recovering(
                2, 1, 1_000L, 1_000L, 0L);
        check(recovering.state == ReminderRuntime.State.RECOVERING, "recovering state");
        check(recovering.recoveryAttempt == 1, "attempt is exposed to runtime");
        ReminderRuntime.Snapshot recovered = recovering.locationReceived(1_100L);
        check(recovered.state == ReminderRuntime.State.LOCATION_READY, "location clears recovery");
        check(recovered.recoveryAttempt == 0, "location resets attempt");

        System.out.println("LocationRecoveryPolicyTest: PASS");
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
