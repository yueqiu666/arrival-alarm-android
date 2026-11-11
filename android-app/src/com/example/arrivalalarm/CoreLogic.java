package com.example.arrivalalarm;

public final class CoreLogic {
    public static final int MIN_RADIUS_METERS = 300;
    public static final int MAX_RADIUS_METERS = 5000;
    public static final int DEFAULT_RADIUS_METERS = 1000;
    public static final double VEHICLE_SPEED_MPS = 12.0 / 3.6;
    public static final long CONFIRM_WINDOW_MS = 60_000L;
    public static final long RESET_STILL_MS = 20L * 60L * 1000L;

    private static final double EARTH_RADIUS_METERS = 6_371_008.8;

    private CoreLogic() {}

    public static int clampRadius(int value) {
        int rounded = Math.round(value / 100f) * 100;
        return Math.max(MIN_RADIUS_METERS, Math.min(MAX_RADIUS_METERS, rounded));
    }

    public static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double firstLat = Math.toRadians(lat1);
        double secondLat = Math.toRadians(lat2);
        double deltaLat = secondLat - firstLat;
        double deltaLng = Math.toRadians(lng2 - lng1);
        double sinLat = Math.sin(deltaLat / 2.0);
        double sinLng = Math.sin(deltaLng / 2.0);
        double h = sinLat * sinLat + Math.cos(firstLat) * Math.cos(secondLat) * sinLng * sinLng;
        return 2.0 * EARTH_RADIUS_METERS * Math.asin(Math.min(1.0, Math.sqrt(h)));
    }

    public static double accuracyLimit(int radiusMeters) {
        return Math.min(200.0, clampRadius(radiusMeters) / 2.0);
    }

    static boolean validCoordinates(double latitude, double longitude) {
        return latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180;
    }

    static boolean usableArrivalFix(LocationFix fix, int radiusMeters) {
        return validCoordinates(fix.latitude, fix.longitude)
                && fix.accuracy >= 0 && fix.accuracy <= accuracyLimit(radiusMeters);
    }

    static boolean freshArrivalFix(long fixNanos, long lastAcceptedNanos, long nowNanos) {
        return fixNanos > lastAcceptedNanos && fixNanos <= nowNanos
                && nowNanos - fixNanos <= ReminderRuntime.LOCATION_TIMEOUT_MS * 1_000_000L;
    }

    public static final class LocationFix {
        public final double latitude;
        public final double longitude;
        public final float accuracy;
        public final float speed;
        public final long timestamp;

        public LocationFix(double latitude, double longitude, float accuracy, float speed, long timestamp) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.accuracy = accuracy;
            this.speed = speed;
            this.timestamp = timestamp;
        }
    }

    public static final class ArrivalResult {
        public final boolean triggered;
        public final String reason;
        public final double distanceMeters;

        ArrivalResult(boolean triggered, String reason, double distanceMeters) {
            this.triggered = triggered;
            this.reason = reason;
            this.distanceMeters = distanceMeters;
        }
    }

    public static final class ArrivalTracker {
        private final double destinationLat;
        private final double destinationLng;
        private final int radiusMeters;
        private boolean triggered;

        public ArrivalTracker(double destinationLat, double destinationLng, int radiusMeters) {
            this.destinationLat = destinationLat;
            this.destinationLng = destinationLng;
            this.radiusMeters = clampRadius(radiusMeters);
        }

        public ArrivalResult update(LocationFix fix) {
            if (!validCoordinates(fix.latitude, fix.longitude)) {
                return new ArrivalResult(false, "invalid-coordinates", Double.NaN);
            }
            double distance = distanceMeters(fix.latitude, fix.longitude, destinationLat, destinationLng);
            if (triggered) return new ArrivalResult(false, "already-triggered", distance);
            if (distance > radiusMeters) {
                return new ArrivalResult(false, "outside", distance);
            }
            if (!usableArrivalFix(fix, radiusMeters)) {
                return new ArrivalResult(false, "low-accuracy", distance);
            }
            triggered = true;
            return new ArrivalResult(true, "inside-radius", distance);
        }

        public ArrivalResult geofenceEntered() {
            if (triggered) return new ArrivalResult(false, "already-triggered", Double.NaN);
            triggered = true;
            return new ArrivalResult(true, "geofence", Double.NaN);
        }
    }

    public static final class JourneyDetector {
        public static final String DISABLED = "disabled";
        public static final String ANCHORED = "anchored";
        public static final String CONFIRMING = "confirming";
        public static final String PROMOTED = "promoted";
        public static final String DISMISSED = "dismissed";
        public static final String WAITING = "waitingForNextTrip";

        public double anchorLat;
        public double anchorLng;
        public String state = ANCHORED;
        public LocationFix confirmStart;
        public long stillSince = -1L;

        public JourneyDetector(double anchorLat, double anchorLng) {
            this.anchorLat = anchorLat;
            this.anchorLng = anchorLng;
        }

        public boolean anchorExited(LocationFix fix) {
            if (!ANCHORED.equals(state)) return false;
            if (distanceMeters(anchorLat, anchorLng, fix.latitude, fix.longitude) < 500.0) return false;
            state = CONFIRMING;
            confirmStart = fix;
            return true;
        }

        public boolean vehicleActivity() {
            if (!CONFIRMING.equals(state)) return false;
            state = PROMOTED;
            return true;
        }

        public boolean update(LocationFix fix) {
            if (ANCHORED.equals(state)) {
                anchorExited(fix);
                return false;
            }
            if (CONFIRMING.equals(state)) {
                if (confirmStart == null || fix.timestamp - confirmStart.timestamp > CONFIRM_WINDOW_MS) {
                    confirmStart = fix;
                    return false;
                }
                double displacement = distanceMeters(confirmStart.latitude, confirmStart.longitude, fix.latitude, fix.longitude);
                double elapsedSeconds = Math.max(1.0, (fix.timestamp - confirmStart.timestamp) / 1000.0);
                double derivedSpeed = displacement / elapsedSeconds;
                double speed = Math.max(fix.speed, derivedSpeed);
                if (speed >= VEHICLE_SPEED_MPS && displacement >= 300.0) {
                    state = PROMOTED;
                    return true;
                }
                return false;
            }

            if (PROMOTED.equals(state) || DISMISSED.equals(state) || WAITING.equals(state)) {
                if (fix.speed >= 0f && fix.speed < 0.5f) {
                    if (stillSince < 0L) stillSince = fix.timestamp;
                } else {
                    stillSince = -1L;
                }
                double moved = distanceMeters(anchorLat, anchorLng, fix.latitude, fix.longitude);
                if (stillSince >= 0L && fix.timestamp - stillSince >= RESET_STILL_MS && moved >= 1000.0) {
                    anchorLat = fix.latitude;
                    anchorLng = fix.longitude;
                    state = ANCHORED;
                    confirmStart = null;
                    stillSince = -1L;
                }
            }
            return false;
        }
    }
}
