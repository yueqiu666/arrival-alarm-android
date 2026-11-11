package com.example.arrivalalarm;

/** Runtime facts owned by the foreground service; never persisted as task state. */
public final class ReminderRuntime {
    public static final long START_TIMEOUT_MS = 15_000L;
    public static final long LOCATION_TIMEOUT_MS = 5L * 60L * 1000L;
    public static final long HEARTBEAT_TIMEOUT_MS = 3L * 60L * 1000L;

    private ReminderRuntime() {}

    public enum State {
        STOPPED,
        STARTING,
        FOREGROUND,
        LOCATION_READY,
        RECOVERING,
        FAILED
    }

    public enum Failure {
        NONE,
        NO_LOCATION_PROVIDER,
        LOCATION_PERMISSION,
        LOCATION_LISTENER,
        LOCATION_STALE,
        FOREGROUND_START
    }

    public static final class Snapshot {
        public final State state;
        public final Failure failure;
        public final int registeredProviderCount;
        public final long stateSinceElapsedRealtime;
        public final long lastHeartbeatElapsedRealtime;
        public final long lastLocationElapsedRealtime;
        public final int recoveryAttempt;
        public final long recoveryStartedElapsedRealtime;

        private Snapshot(State state, Failure failure, int registeredProviderCount,
                         long stateSinceElapsedRealtime, long lastHeartbeatElapsedRealtime,
                         long lastLocationElapsedRealtime, int recoveryAttempt,
                         long recoveryStartedElapsedRealtime) {
            this.state = state;
            this.failure = failure;
            this.registeredProviderCount = Math.max(0, registeredProviderCount);
            this.stateSinceElapsedRealtime = stateSinceElapsedRealtime;
            this.lastHeartbeatElapsedRealtime = lastHeartbeatElapsedRealtime;
            this.lastLocationElapsedRealtime = lastLocationElapsedRealtime;
            this.recoveryAttempt = Math.max(0, recoveryAttempt);
            this.recoveryStartedElapsedRealtime = recoveryStartedElapsedRealtime;
        }

        public static Snapshot stopped() {
            return new Snapshot(State.STOPPED, Failure.NONE, 0, 0L, 0L, 0L, 0, 0L);
        }

        public static Snapshot starting(long nowElapsedRealtime) {
            return new Snapshot(State.STARTING, Failure.NONE, 0, nowElapsedRealtime,
                    nowElapsedRealtime, 0L, 0, 0L);
        }

        public static Snapshot foreground(long nowElapsedRealtime, long heartbeatElapsedRealtime) {
            return new Snapshot(State.FOREGROUND, Failure.NONE, 0, nowElapsedRealtime,
                    heartbeatElapsedRealtime, 0L, 0, 0L);
        }

        public static Snapshot locationReady(int providerCount, long nowElapsedRealtime,
                                             long heartbeatElapsedRealtime, long locationElapsedRealtime) {
            return new Snapshot(State.LOCATION_READY, Failure.NONE, providerCount, nowElapsedRealtime,
                    heartbeatElapsedRealtime, locationElapsedRealtime, 0, 0L);
        }

        public static Snapshot recovering(int providerCount, int attempt, long nowElapsedRealtime,
                                          long heartbeatElapsedRealtime, long locationElapsedRealtime) {
            return new Snapshot(State.RECOVERING, Failure.NONE, providerCount, nowElapsedRealtime,
                    heartbeatElapsedRealtime, locationElapsedRealtime, attempt, nowElapsedRealtime);
        }

        public static Snapshot failed(Failure failure, long nowElapsedRealtime) {
            return new Snapshot(State.FAILED, failure, 0, nowElapsedRealtime,
                    nowElapsedRealtime, 0L, 0, 0L);
        }

        public Snapshot heartbeat(long nowElapsedRealtime) {
            return new Snapshot(state, failure, registeredProviderCount, stateSinceElapsedRealtime,
                    nowElapsedRealtime, lastLocationElapsedRealtime,
                    recoveryAttempt, recoveryStartedElapsedRealtime);
        }

        public Snapshot locationReceived(long nowElapsedRealtime) {
            return new Snapshot(State.LOCATION_READY, Failure.NONE, registeredProviderCount,
                    stateSinceElapsedRealtime, nowElapsedRealtime, nowElapsedRealtime, 0, 0L);
        }

        public boolean isHeartbeatFresh(long nowElapsedRealtime) {
            return lastHeartbeatElapsedRealtime > 0L
                    && nowElapsedRealtime >= lastHeartbeatElapsedRealtime
                    && nowElapsedRealtime - lastHeartbeatElapsedRealtime <= HEARTBEAT_TIMEOUT_MS;
        }

        public boolean isStartFresh(long nowElapsedRealtime) {
            return state == State.STARTING
                    && nowElapsedRealtime >= stateSinceElapsedRealtime
                    && nowElapsedRealtime - stateSinceElapsedRealtime <= START_TIMEOUT_MS;
        }

        public boolean isLocationFresh(long nowElapsedRealtime) {
            return lastLocationElapsedRealtime > 0L
                    && nowElapsedRealtime >= lastLocationElapsedRealtime
                    && nowElapsedRealtime - lastLocationElapsedRealtime <= LOCATION_TIMEOUT_MS;
        }

        public boolean isWaitingForFirstLocationWithinTimeout(long nowElapsedRealtime) {
            return state == State.LOCATION_READY
                    && lastLocationElapsedRealtime == 0L
                    && nowElapsedRealtime >= stateSinceElapsedRealtime
                    && nowElapsedRealtime - stateSinceElapsedRealtime <= LOCATION_TIMEOUT_MS;
        }
    }
}
