package com.example.arrivalalarm;

import android.location.Location;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AmapPlaceSearch {
    private static final String ENDPOINT = "https://restapi.amap.com/v3/assistant/inputtips";

    private AmapPlaceSearch() { }

    public static List<AppPrefs.Destination> search(String webKey, String query, Location nearby, int maxResults) throws IOException {
        List<AppPrefs.Destination> results = new ArrayList<>();
        if (webKey == null || webKey.trim().isEmpty() || query == null || query.trim().isEmpty()) return results;
        if (!PlaceSearchLimits.validQuery(query)) throw new IOException("Invalid place query length");
        StringBuilder url = new StringBuilder(ENDPOINT)
                .append("?output=JSON&datatype=all&citylimit=false")
                .append("&keywords=").append(encode(query.trim()))
                .append("&key=").append(encode(webKey.trim()));
        if (nearby != null) {
            double[] gcj = CoordinateTransform.wgs84ToGcj02(nearby.getLatitude(), nearby.getLongitude());
            url.append("&location=").append(String.format(Locale.US, "%.6f,%.6f", gcj[1], gcj[0]));
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(url.toString()).openConnection();
        connection.setConnectTimeout(900);
        connection.setReadTimeout(1100);
        connection.setUseCaches(true);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Accept-Charset", "UTF-8");
        try {
            int status = connection.getResponseCode();
            PlaceSearchPipeline.checkHttpStatus(status);
            JSONObject root = new JSONObject(PlaceSearchLimits.readUtf8(connection.getInputStream()));
            if (!"1".equals(root.optString("status"))) {
                throw new PlaceSearchPipeline.ProviderException(PlaceSearchPipeline.Failure.CONFIGURATION);
            }
            JSONArray tips = root.optJSONArray("tips");
            if (tips == null) throw new PlaceSearchPipeline.ProviderException(PlaceSearchPipeline.Failure.INVALID_RESPONSE);
            for (int i = 0; i < tips.length() && results.size() < maxResults; i++) {
                JSONObject tip = tips.optJSONObject(i);
                if (tip == null) continue;
                String location = clean(tip.opt("location"));
                String[] parts = location.split(",");
                if (parts.length != 2) continue;
                double gcjLng;
                double gcjLat;
                try {
                    gcjLng = Double.parseDouble(parts[0]);
                    gcjLat = Double.parseDouble(parts[1]);
                } catch (NumberFormatException ignored) { continue; }
                if (!Double.isFinite(gcjLat) || !Double.isFinite(gcjLng)
                        || gcjLat < -90 || gcjLat > 90 || gcjLng < -180 || gcjLng > 180) continue;
                String name = clean(tip.opt("name"));
                if (name.isEmpty()) continue;
                String district = clean(tip.opt("district"));
                String address = clean(tip.opt("address"));
                String id = clean(tip.opt("id"));
                double[] wgs = CoordinateTransform.gcj02ToWgs84(gcjLat, gcjLng);
                if (id.isEmpty()) id = Integer.toHexString((name + ":" + wgs[0] + ":" + wgs[1]).hashCode());
                results.add(new AppPrefs.Destination("amap-" + id, name, address, district, wgs[0], wgs[1]));
            }
            return results;
        } catch (JSONException | RuntimeException parseError) {
            throw new IOException("AMap response parse failed", parseError);
        } finally {
            connection.disconnect();
        }
    }

    private static String clean(Object value) {
        if (value == null || value == JSONObject.NULL || value instanceof JSONArray || value instanceof JSONObject) return "";
        String text = String.valueOf(value).trim();
        return "[]".equals(text) || "{}".equals(text) ? "" : text;
    }

    private static String encode(String value) throws IOException {
        return URLEncoder.encode(value, "UTF-8");
    }

}
