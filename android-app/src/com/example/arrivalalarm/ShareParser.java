package com.example.arrivalalarm;

import android.content.Intent;
import android.net.Uri;

public final class ShareParser {
    private ShareParser() {}

    public static boolean isInputTooLong(Intent intent) {
        if (intent == null) return false;
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            return intent.getData() != null && PlaceSearchLimits.inputTooLong(intent.getData().toString());
        }
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            return PlaceSearchLimits.inputTooLong(intent.getStringExtra(Intent.EXTRA_TEXT));
        }
        if (Intent.ACTION_PROCESS_TEXT.equals(intent.getAction())) {
            return PlaceSearchLimits.inputTooLong(intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT));
        }
        return false;
    }

    public static ParsedShare fromIntent(Intent intent) {
        if (intent == null) return null;
        if (isInputTooLong(intent)) return null;
        if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            Uri data = intent.getData();
            if ("arrivalalarm".equalsIgnoreCase(data.getScheme())) {
                String name = safe(data.getQueryParameter("name"));
                Double lat = number(data.getQueryParameter("lat"));
                Double lng = number(data.getQueryParameter("lng"));
                // arrivalalarm:// 由本应用定义，坐标一律视为 WGS-84。
                if (lat != null && lng != null && valid(lat, lng)) return new ParsedShare(name, lat, lng, "应用深链", true);
                return name.isEmpty() ? null : new ParsedShare(name, null, null, "应用深链", false);
            }
            return parseText(data.toString());
        }
        if (Intent.ACTION_SEND.equals(intent.getAction()) && (intent.getType() == null || "text/plain".equals(intent.getType()))) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            return parseText(text);
        }
        if (Intent.ACTION_PROCESS_TEXT.equals(intent.getAction())) {
            CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
            return parseText(text == null ? null : text.toString());
        }
        return null;
    }

    public static ParsedShare parseText(String text) {
        PlaceInputParser.Result parsed = PlaceInputParser.parse(text);
        if (parsed == null) return null;
        return new ParsedShare(parsed.name, parsed.latitude, parsed.longitude, parsed.source, parsed.coordinateSystemVerified);
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }

    private static Double number(String value) {
        try { return value == null ? null : Double.valueOf(value); }
        catch (NumberFormatException ignored) { return null; }
    }

    private static boolean valid(Double lat, Double lng) {
        return lat != null && lng != null && lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180;
    }

    public static final class ParsedShare {
        public final String name;
        public final Double latitude;
        public final Double longitude;
        public final String source;
        /** 坐标是否来自可识别坐标系并已转换到 WGS-84。 */
        public final boolean coordinateSystemVerified;

        ParsedShare(String name, Double latitude, Double longitude, String source, boolean coordinateSystemVerified) {
            this.name = name == null ? "" : name;
            this.latitude = latitude;
            this.longitude = longitude;
            this.source = source == null ? "手动输入" : source;
            this.coordinateSystemVerified = coordinateSystemVerified;
        }

        public boolean hasCoordinates() { return latitude != null && longitude != null; }
    }
}
