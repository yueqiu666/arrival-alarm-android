package com.example.arrivalalarm;

import android.location.Location;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class PhotonPlaceSearch {
    private static final String ENDPOINT = "https://photon.komoot.io/api/";
    private static long nextRequestAt;

    private PhotonPlaceSearch() { }

    public static List<AppPrefs.Destination> search(String query, Location nearby, int maxResults) throws IOException {
        if (query == null || query.trim().isEmpty()) return new ArrayList<>();
        waitForResponsibleRate();
        StringBuilder url = new StringBuilder(ENDPOINT)
                .append("?q=").append(encode(query.trim()))
                .append("&limit=").append(Math.max(1, Math.min(10, maxResults)));
        if (nearby != null) {
            url.append("&lat=").append(String.format(Locale.US, "%.6f", nearby.getLatitude()))
                    .append("&lon=").append(String.format(Locale.US, "%.6f", nearby.getLongitude()))
                    .append("&zoom=11&location_bias_scale=0.25");
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(url.toString()).openConnection();
        connection.setConnectTimeout(1200);
        connection.setReadTimeout(1600);
        connection.setUseCaches(true);
        connection.setRequestProperty("Accept", "application/geo+json, application/json");
        connection.setRequestProperty("User-Agent", "ArrivalAlarm/" + BuildConfig.VERSION_NAME + " Android place-search");
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new IOException("Photon HTTP " + status);
            JSONObject root = new JSONObject(readUtf8(connection.getInputStream()));
            JSONArray features = root.optJSONArray("features");
            List<AppPrefs.Destination> results = new ArrayList<>();
            if (features == null) return results;
            for (int i = 0; i < features.length(); i++) {
                JSONObject feature = features.optJSONObject(i);
                if (feature == null) continue;
                JSONObject properties = feature.optJSONObject("properties");
                JSONObject geometry = feature.optJSONObject("geometry");
                JSONArray coordinates = geometry == null ? null : geometry.optJSONArray("coordinates");
                if (properties == null || coordinates == null || coordinates.length() < 2) continue;
                String name = clean(properties.opt("name"));
                if (name.isEmpty()) continue;
                double longitude = coordinates.optDouble(0, Double.NaN);
                double latitude = coordinates.optDouble(1, Double.NaN);
                if (Double.isNaN(latitude) || Double.isNaN(longitude)
                        || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) continue;
                String city = firstNonEmpty(properties, "city", "town", "county", "state");
                String district = firstNonEmpty(properties, "district", "locality");
                String street = firstNonEmpty(properties, "street");
                String house = firstNonEmpty(properties, "housenumber");
                String address = join(" · ", district, join(" ", street, house));
                if (!PlaceQuality.isRelevant(query, name, city, address)) continue;
                String osmType = clean(properties.opt("osm_type"));
                String osmId = clean(properties.opt("osm_id"));
                String id = "photon-" + (osmType + osmId).trim();
                if (id.equals("photon-")) id += Integer.toHexString((name + latitude + longitude).hashCode());
                results.add(new AppPrefs.Destination(id, name, address, city, latitude, longitude));
            }
            Collections.sort(results, new Comparator<AppPrefs.Destination>() {
                @Override public int compare(AppPrefs.Destination first, AppPrefs.Destination second) {
                    return Integer.compare(PlaceQuality.score(query, second.name, second.city, second.address),
                            PlaceQuality.score(query, first.name, first.city, first.address));
                }
            });
            return results.size() <= maxResults ? results : new ArrayList<>(results.subList(0, maxResults));
        } catch (JSONException | RuntimeException parseError) {
            throw new IOException("Photon response parse failed", parseError);
        } finally {
            connection.disconnect();
        }
    }

    private static void waitForResponsibleRate() throws IOException {
        long delay;
        synchronized (PhotonPlaceSearch.class) {
            long now = System.currentTimeMillis();
            long slot = Math.max(now, nextRequestAt);
            nextRequestAt = slot + 350L;
            delay = slot - now;
        }
        if (delay <= 0) return;
        try { Thread.sleep(delay); }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Photon request cancelled", interrupted);
        }
    }

    private static String firstNonEmpty(JSONObject object, String... keys) {
        for (String key : keys) {
            String value = clean(object.opt(key));
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private static String clean(Object value) {
        if (value == null || value == JSONObject.NULL || value instanceof JSONArray || value instanceof JSONObject) return "";
        return String.valueOf(value).trim();
    }

    private static String join(String separator, String first, String second) {
        if (first == null || first.isEmpty()) return second == null ? "" : second;
        if (second == null || second.isEmpty()) return first;
        return first + separator + second;
    }

    private static String encode(String value) throws IOException { return URLEncoder.encode(value, "UTF-8"); }

    private static String readUtf8(InputStream stream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
        StringBuilder result = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) result.append(line);
        reader.close();
        return result.toString();
    }
}
