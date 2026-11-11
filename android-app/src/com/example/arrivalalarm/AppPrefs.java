package com.example.arrivalalarm;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class AppPrefs {
    private static final String FILE = "arrival_alarm_private";
    static final String TASK = "task";
    private static final String RECENTS = "recents";
    private static final String FIXED_ALARMS = "fixed_alarms";
    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_RECENTS = 10;
    private static final String QUARANTINE = "quarantine_";
    private static final String RADIUS = "radius";
    static final String SHOW_DESTINATION = "show_destination_in_notifications";
    private static final String JOURNEY_ENABLED = "journey_enabled";
    private static final String JOURNEY_SEEN = "journey_seen";
    private static final String APP_WATCH_ENABLED = "app_watch_enabled";
    private static final String TRANSIT_WATCH_ENABLED = "transit_watch_enabled";
    private static final String STATION_WATCH_ENABLED = "station_watch_enabled";
    private static final String LAST_AUTO_PROMPT_AT = "last_auto_prompt_at";
    private static final long AUTO_PROMPT_COOLDOWN_MS = 3L * 60L * 60L * 1000L;
    private static final String JOURNEY_STATE = "journey_state";
    private static final String ANCHOR_LAT = "anchor_lat";
    private static final String ANCHOR_LNG = "anchor_lng";
    private static final String CONFIRM_LAT = "confirm_lat";
    private static final String CONFIRM_LNG = "confirm_lng";
    private static final String CONFIRM_TIME = "confirm_time";
    private static final String STILL_SINCE = "still_since";

    private final SharedPreferences prefs;

    public AppPrefs(Context context) {
        prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public int getRadius() { return CoreLogic.clampRadius(prefs.getInt(RADIUS, CoreLogic.DEFAULT_RADIUS_METERS)); }
    public void setRadius(int radius) { prefs.edit().putInt(RADIUS, CoreLogic.clampRadius(radius)).apply(); }

    public boolean showDestinationInNotifications() { return prefs.getBoolean(SHOW_DESTINATION, false); }
    public void setShowDestinationInNotifications(boolean show) { prefs.edit().putBoolean(SHOW_DESTINATION, show).apply(); }
    public void registerPreferenceListener(SharedPreferences.OnSharedPreferenceChangeListener listener) { prefs.registerOnSharedPreferenceChangeListener(listener); }
    public void unregisterPreferenceListener(SharedPreferences.OnSharedPreferenceChangeListener listener) { prefs.unregisterOnSharedPreferenceChangeListener(listener); }

    public void saveTask(ReminderTask task) {
        synchronized (prefs) {
            prefs.edit().putString(TASK, versioned(task.toJson()).toString()).apply();
        }
    }

    void saveTaskAndRecent(ReminderTask task) {
        synchronized (prefs) {
            prefs.edit().putString(TASK, versioned(task.toJson()).toString())
                    .putString(RECENTS, prependRecent(task.destination)).apply();
        }
    }

    public ReminderTask getTask() {
        synchronized (prefs) {
            String raw = readString(TASK);
            if (raw == null) return null;
            try {
                JSONObject record = new JSONObject(raw);
                ReminderTask task = supported(record) ? ReminderTask.fromJson(record) : null;
                if (task != null) return task;
            } catch (JSONException malformed) {
                // Preserve the original before treating it as unavailable.
            }
            quarantine(TASK, raw);
            return null;
        }
    }

    public void clearTask() {
        synchronized (prefs) { prefs.edit().remove(TASK).apply(); }
    }

    public List<Destination> getRecents() {
        return readList(RECENTS, MAX_RECENTS, Destination::fromJson);
    }

    public void addRecent(Destination destination) {
        synchronized (prefs) { prefs.edit().putString(RECENTS, prependRecent(destination)).apply(); }
    }

    private String prependRecent(Destination destination) {
        JSONArray array = new JSONArray();
        array.put(versioned(destination.toJson()));
        for (Destination item : getRecents()) {
            if (!item.id.equals(destination.id) && array.length() < MAX_RECENTS) array.put(versioned(item.toJson()));
        }
        return array.toString();
    }

    public List<FixedAlarm> getFixedAlarms() {
        return readList(FIXED_ALARMS, Integer.MAX_VALUE, FixedAlarm::fromJson);
    }

    private <T> List<T> readList(String key, int limit, Function<JSONObject, T> decoder) {
        synchronized (prefs) {
            List<T> result = new ArrayList<>();
            String raw = readString(key);
            if (raw == null) return result;
            try {
                JSONArray array = new JSONArray(raw);
                for (int i = 0; i < array.length() && result.size() < limit; i++) {
                    JSONObject entry = array.optJSONObject(i);
                    T item = entry != null && supported(entry) ? decoder.apply(entry) : null;
                    if (item == null) quarantine(key, raw);
                    else result.add(item);
                }
            } catch (JSONException malformed) {
                quarantine(key, raw);
            }
            return result;
        }
    }

    private String readString(String key) {
        try { return prefs.getString(key, null); }
        catch (ClassCastException wrongType) {
            quarantine(key, String.valueOf(prefs.getAll().get(key)));
            return null;
        }
    }

    private static JSONObject versioned(JSONObject record) {
        try { return record.put("schemaVersion", SCHEMA_VERSION); }
        catch (JSONException impossible) { throw new IllegalStateException(impossible); }
    }

    private static boolean supported(JSONObject record) {
        int version = record.optInt("schemaVersion", record.has("schemaVersion") ? -1 : 0);
        return version == 0 || version == SCHEMA_VERSION;
    }

    private void quarantine(String key, String raw) {
        String backup = QUARANTINE + key;
        if (!prefs.contains(backup)) prefs.edit().putString(backup, raw).apply();
    }

    boolean hasUnreadableData() {
        return prefs.contains(QUARANTINE + TASK) || prefs.contains(QUARANTINE + RECENTS)
                || prefs.contains(QUARANTINE + FIXED_ALARMS);
    }

    public FixedAlarm getFixedAlarm(String id) {
        if (id == null) return null;
        for (FixedAlarm alarm : getFixedAlarms()) if (id.equals(alarm.id)) return alarm;
        return null;
    }

    public void saveFixedAlarm(FixedAlarm alarm) {
        synchronized (prefs) {
            List<FixedAlarm> alarms = getFixedAlarms();
            JSONArray array = new JSONArray();
            boolean replaced = false;
            for (FixedAlarm item : alarms) {
                if (item.id.equals(alarm.id)) {
                    array.put(versioned(alarm.toJson()));
                    replaced = true;
                } else array.put(versioned(item.toJson()));
            }
            if (!replaced) array.put(versioned(alarm.toJson()));
            prefs.edit().putString(FIXED_ALARMS, array.toString()).apply();
        }
    }

    public void removeFixedAlarm(String id) {
        synchronized (prefs) {
            JSONArray array = new JSONArray();
            for (FixedAlarm alarm : getFixedAlarms()) {
                if (!alarm.id.equals(id)) array.put(versioned(alarm.toJson()));
            }
            prefs.edit().putString(FIXED_ALARMS, array.toString()).apply();
        }
    }

    public boolean isJourneyEnabled() { return prefs.getBoolean(JOURNEY_ENABLED, false); }
    public void disableDeferredAutomation() {
        prefs.edit()
                .putBoolean(JOURNEY_ENABLED, false)
                .putBoolean(JOURNEY_SEEN, true)
                .putBoolean(APP_WATCH_ENABLED, false)
                .putBoolean(TRANSIT_WATCH_ENABLED, false)
                .putBoolean(STATION_WATCH_ENABLED, false)
                .putString(JOURNEY_STATE, CoreLogic.JourneyDetector.DISABLED)
                .remove(ANCHOR_LAT)
                .remove(ANCHOR_LNG)
                .remove(CONFIRM_LAT)
                .remove(CONFIRM_LNG)
                .remove(CONFIRM_TIME)
                .remove(STILL_SINCE)
                .apply();
    }
    public void setJourneyEnabled(boolean enabled) {
        SharedPreferences.Editor editor = prefs.edit().putBoolean(JOURNEY_ENABLED, enabled);
        if (!enabled) editor.putString(JOURNEY_STATE, CoreLogic.JourneyDetector.DISABLED).remove(ANCHOR_LAT).remove(ANCHOR_LNG);
        editor.apply();
    }
    public boolean hasSeenJourneyPrompt() { return prefs.getBoolean(JOURNEY_SEEN, false); }
    public void setJourneyPromptSeen() { prefs.edit().putBoolean(JOURNEY_SEEN, true).apply(); }
    public boolean isAppWatchEnabled() { return prefs.getBoolean(APP_WATCH_ENABLED, false); }
    public void setAppWatchEnabled(boolean enabled) { prefs.edit().putBoolean(APP_WATCH_ENABLED, enabled).apply(); }
    public boolean isTransitWatchEnabled() { return prefs.getBoolean(TRANSIT_WATCH_ENABLED, false); }
    public void setTransitWatchEnabled(boolean enabled) { prefs.edit().putBoolean(TRANSIT_WATCH_ENABLED, enabled).apply(); }
    public boolean isStationWatchEnabled() { return prefs.getBoolean(STATION_WATCH_ENABLED, false); }
    public void setStationWatchEnabled(boolean enabled) { prefs.edit().putBoolean(STATION_WATCH_ENABLED, enabled).apply(); }
    public boolean canShowAutoPrompt(long now) { return now - prefs.getLong(LAST_AUTO_PROMPT_AT, 0L) >= AUTO_PROMPT_COOLDOWN_MS; }
    public void markAutoPrompt(long now) { prefs.edit().putLong(LAST_AUTO_PROMPT_AT, now).apply(); }
    public String getJourneyState() { return prefs.getString(JOURNEY_STATE, isJourneyEnabled() ? CoreLogic.JourneyDetector.ANCHORED : CoreLogic.JourneyDetector.DISABLED); }
    public void setJourneyState(String state) { prefs.edit().putString(JOURNEY_STATE, state).apply(); }
    public boolean hasAnchor() { return prefs.contains(ANCHOR_LAT) && prefs.contains(ANCHOR_LNG); }
    public double getAnchorLat() { return Double.longBitsToDouble(prefs.getLong(ANCHOR_LAT, 0L)); }
    public double getAnchorLng() { return Double.longBitsToDouble(prefs.getLong(ANCHOR_LNG, 0L)); }
    public void setAnchor(double lat, double lng) {
        prefs.edit().putLong(ANCHOR_LAT, Double.doubleToRawLongBits(lat)).putLong(ANCHOR_LNG, Double.doubleToRawLongBits(lng)).apply();
    }
    public void setJourneyRuntime(CoreLogic.JourneyDetector detector) {
        SharedPreferences.Editor editor = prefs.edit()
                .putString(JOURNEY_STATE, detector.state)
                .putLong(ANCHOR_LAT, Double.doubleToRawLongBits(detector.anchorLat))
                .putLong(ANCHOR_LNG, Double.doubleToRawLongBits(detector.anchorLng))
                .putLong(STILL_SINCE, detector.stillSince);
        if (detector.confirmStart != null) {
            editor.putLong(CONFIRM_LAT, Double.doubleToRawLongBits(detector.confirmStart.latitude))
                    .putLong(CONFIRM_LNG, Double.doubleToRawLongBits(detector.confirmStart.longitude))
                    .putLong(CONFIRM_TIME, detector.confirmStart.timestamp);
        } else {
            editor.remove(CONFIRM_LAT).remove(CONFIRM_LNG).remove(CONFIRM_TIME);
        }
        editor.apply();
    }
    public CoreLogic.JourneyDetector getJourneyDetector() {
        if (!hasAnchor()) return null;
        CoreLogic.JourneyDetector detector = new CoreLogic.JourneyDetector(getAnchorLat(), getAnchorLng());
        detector.state = getJourneyState();
        detector.stillSince = prefs.getLong(STILL_SINCE, -1L);
        if (prefs.contains(CONFIRM_LAT) && prefs.contains(CONFIRM_LNG)) {
            detector.confirmStart = new CoreLogic.LocationFix(
                    Double.longBitsToDouble(prefs.getLong(CONFIRM_LAT, 0L)),
                    Double.longBitsToDouble(prefs.getLong(CONFIRM_LNG, 0L)),
                    50f, -1f, prefs.getLong(CONFIRM_TIME, 0L));
        }
        return detector;
    }

    public void clearAll() {
        synchronized (prefs) { prefs.edit().clear().apply(); }
    }

    public static final class Destination {
        public final String id;
        public final String name;
        public final String address;
        public final String city;
        public final double latitude;
        public final double longitude;

        public Destination(String id, String name, String address, String city, double latitude, double longitude) {
            this.id = id;
            this.name = name;
            this.address = address == null ? "" : address;
            this.city = city == null ? "" : city;
            this.latitude = latitude;
            this.longitude = longitude;
        }

        JSONObject toJson() {
            try {
                return new JSONObject().put("id", id).put("name", name).put("address", address).put("city", city)
                        .put("latitude", latitude).put("longitude", longitude);
            } catch (JSONException impossible) { throw new IllegalStateException(impossible); }
        }

        static Destination fromJson(JSONObject json) {
            try {
                double latitude = json.getDouble("latitude");
                double longitude = json.getDouble("longitude");
                if (!CoreLogic.validCoordinates(latitude, longitude)) return null;
                return new Destination(json.getString("id"), json.getString("name"), json.optString("address"), json.optString("city"),
                        latitude, longitude);
            } catch (JSONException ignored) { return null; }
        }
    }

    public static final class ReminderTask {
        public static final String STATUS_STARTING = "starting";
        public static final String STATUS_ACTIVE = "active";
        public static final String STATUS_ALERTING = "alerting";
        public static final String STATUS_INTERRUPTED = "interrupted";

        public final String id;
        public final Destination destination;
        public final int radiusMeters;
        public String status;
        public final long startedAt;
        public long triggeredAt;
        public double lastDistanceMeters;

        public ReminderTask(String id, Destination destination, int radiusMeters, String status, long startedAt) {
            this.id = id;
            this.destination = destination;
            this.radiusMeters = CoreLogic.clampRadius(radiusMeters);
            this.status = status;
            this.startedAt = startedAt;
            this.lastDistanceMeters = Double.NaN;
        }

        public boolean isActive() { return STATUS_ACTIVE.equals(status); }
        public boolean isStarting() { return STATUS_STARTING.equals(status); }
        public boolean isAlerting() { return STATUS_ALERTING.equals(status); }
        public boolean isInterrupted() { return STATUS_INTERRUPTED.equals(status); }
        public void markInterrupted() {
            if (isStarting() || isActive()) status = STATUS_INTERRUPTED;
        }
        public void resume() {
            status = STATUS_STARTING;
        }
        public void markActive() {
            if (isStarting()) status = STATUS_ACTIVE;
        }

        JSONObject toJson() {
            try {
                return new JSONObject().put("id", id).put("destination", destination.toJson()).put("radiusMeters", radiusMeters)
                        .put("status", status).put("startedAt", startedAt).put("triggeredAt", triggeredAt);
            } catch (JSONException impossible) { throw new IllegalStateException(impossible); }
        }

        static ReminderTask fromJson(JSONObject json) throws JSONException {
            Destination destination = Destination.fromJson(json.getJSONObject("destination"));
            if (destination == null) return null;
            ReminderTask task = new ReminderTask(json.getString("id"), destination, json.getInt("radiusMeters"), json.getString("status"), json.getLong("startedAt"));
            task.triggeredAt = json.optLong("triggeredAt", 0L);
            return task;
        }
    }

    public static final class FixedAlarm {
        public final String id;
        public final Destination destination;
        public final int hour;
        public final int minute;
        public final int daysMask;
        public final boolean enabled;

        public FixedAlarm(String id, Destination destination, int hour, int minute, int daysMask, boolean enabled) {
            this.id = id;
            this.destination = destination;
            this.hour = Math.max(0, Math.min(23, hour));
            this.minute = Math.max(0, Math.min(59, minute));
            this.daysMask = daysMask;
            this.enabled = enabled;
        }

        JSONObject toJson() {
            try {
                return new JSONObject().put("id", id).put("destination", destination.toJson())
                        .put("hour", hour).put("minute", minute).put("daysMask", daysMask).put("enabled", enabled);
            } catch (JSONException impossible) { throw new IllegalStateException(impossible); }
        }

        static FixedAlarm fromJson(JSONObject json) {
            try {
                Destination destination = Destination.fromJson(json.getJSONObject("destination"));
                if (destination == null) return null;
                return new FixedAlarm(json.getString("id"), destination, json.getInt("hour"), json.getInt("minute"),
                        json.getInt("daysMask"), json.optBoolean("enabled", true));
            } catch (JSONException ignored) { return null; }
        }
    }
}
