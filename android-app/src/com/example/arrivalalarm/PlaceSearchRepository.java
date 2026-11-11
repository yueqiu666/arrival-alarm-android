package com.example.arrivalalarm;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class PlaceSearchRepository {
    private final Context context;

    PlaceSearchRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    PlaceSearchPipeline.Result<AppPrefs.Destination> query(String query, int maxResults, boolean suggestions, boolean amapAndroidSearchEnabled) {
        if (amapAndroidSearchEnabled) {
            return PlaceSearchPipeline.search(Collections.singletonList(
                    () -> AmapAndroidPlaceSearch.search(context, query, maxResults, suggestions)));
        }
        List<PlaceSearchPipeline.Provider<AppPrefs.Destination>> providers = new ArrayList<>();
        String amapKey = developerAmapKey();
        if (!amapKey.isEmpty()) {
            providers.add(() -> AmapPlaceSearch.search(amapKey, query, getLastKnownLocation(), maxResults));
        }
        providers.add(() -> PhotonPlaceSearch.search(query, getLastKnownLocation(), maxResults));
        if (Geocoder.isPresent()) {
            providers.add(() -> destinationsFromAddresses(query, findAddresses(query, maxResults)));
        }
        return PlaceSearchPipeline.search(providers);
    }

    static String developerAmapKey() {
        String key = DeveloperConfig.AMAP_WEB_KEY == null ? "" : DeveloperConfig.AMAP_WEB_KEY.trim();
        return key.startsWith("__") ? "" : key;
    }

    private List<Address> findAddresses(String query, int maxResults) throws IOException {
        Geocoder geocoder = new Geocoder(context, Locale.SIMPLIFIED_CHINESE);
        List<Address> found = new ArrayList<>();
        Location nearby = getLastKnownLocation();
        if (nearby != null) {
            List<Address> local = geocoder.getFromLocationName(query, maxResults,
                    Math.max(-90, nearby.getLatitude() - 1.2), Math.max(-180, nearby.getLongitude() - 1.5),
                    Math.min(90, nearby.getLatitude() + 1.2), Math.min(180, nearby.getLongitude() + 1.5));
            if (local != null) found.addAll(local);
        }
        if (found.isEmpty()) {
            List<Address> global = geocoder.getFromLocationName(query, maxResults);
            if (global != null) found.addAll(global);
        }
        return found;
    }

    private Location getLastKnownLocation() {
        if (!ReminderPermissions.hasLocation(context)) return null;
        LocationManager manager = context.getSystemService(LocationManager.class);
        if (manager == null) return null;
        try {
            Location gps = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location network = manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (gps == null) return network;
            if (network == null) return gps;
            return gps.getTime() >= network.getTime() ? gps : network;
        } catch (SecurityException ignored) { return null; }
    }

    private List<AppPrefs.Destination> destinationsFromAddresses(String query, List<Address> addresses) {
        List<AppPrefs.Destination> places = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Address address : addresses) {
            if (address == null || !address.hasLatitude() || !address.hasLongitude()) continue;
            double latitude = address.getLatitude();
            double longitude = address.getLongitude();
            if (!CoreLogic.validCoordinates(latitude, longitude)) continue;
            String name = address.getFeatureName();
            if (name == null || name.trim().isEmpty()) name = query;
            String line = address.getMaxAddressLineIndex() >= 0 ? address.getAddressLine(0) : "";
            String city = address.getLocality();
            if (city == null || city.isEmpty()) city = address.getAdminArea() == null ? "" : address.getAdminArea();
            boolean recoveredName = PlaceQuality.shouldRecoverQueryName(query, name, city, line);
            if (recoveredName) name = query;
            if (!PlaceQuality.isRelevant(query, name, city, line)) continue;
            String id = (recoveredName ? "inferred-" : "") + idFor(name, latitude, longitude);
            if (!seen.add(id)) continue;
            places.add(new AppPrefs.Destination(id, name, line, city, latitude, longitude));
        }
        Collections.sort(places, new Comparator<AppPrefs.Destination>() {
            @Override public int compare(AppPrefs.Destination first, AppPrefs.Destination second) {
                return Integer.compare(PlaceQuality.score(query, second.name, second.city, second.address),
                        PlaceQuality.score(query, first.name, first.city, first.address));
            }
        });
        return places;
    }

    String reverseAddress(double latitude, double longitude) {
        if (Geocoder.isPresent()) {
            try {
                List<Address> found = new Geocoder(context, Locale.SIMPLIFIED_CHINESE)
                        .getFromLocation(latitude, longitude, 1);
                Address first = found == null || found.isEmpty() ? null : found.get(0);
                if (first != null && first.getMaxAddressLineIndex() >= 0) {
                    String line = first.getAddressLine(0);
                    if (line != null) return line;
                }
            } catch (IOException | IllegalArgumentException ignored) { }
        }
        return "";
    }

    static String idFor(String name, double lat, double lng) { return Integer.toHexString((name + ":" + lat + ":" + lng).hashCode()); }
}
