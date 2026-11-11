package com.example.arrivalalarm;

import android.content.Context;

import com.amap.api.services.core.AMapException;
import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.core.PoiItemV2;
import com.amap.api.services.core.ServiceSettings;
import com.amap.api.services.help.Inputtips;
import com.amap.api.services.help.InputtipsQuery;
import com.amap.api.services.help.Tip;
import com.amap.api.services.poisearch.PoiResultV2;
import com.amap.api.services.poisearch.PoiSearchV2;
import com.amap.apis.utils.core.api.AMapUtilCoreApi;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.example.arrivalalarm.PlaceSearchPipeline.Failure;
import static com.example.arrivalalarm.PlaceSearchPipeline.ProviderException;

final class AmapAndroidPlaceSearch {
    private static boolean configured;

    private AmapAndroidPlaceSearch() { }

    static boolean isAvailable() { return !BuildConfig.AMAP_ANDROID_KEY.isEmpty(); }

    static List<AppPrefs.Destination> search(Context context, String query, int limit, boolean suggestions)
            throws IOException {
        if (!isAvailable()) throw new ProviderException(Failure.CONFIGURATION);
        Context app = context.getApplicationContext();
        // An explicit separator disambiguates cities without sending the user's location.
        int separator = query.indexOf('+');
        String city = separator > 0 ? query.substring(0, separator).trim() : "";
        String keyword = separator > 0 ? query.substring(separator + 1).trim() : query;
        if (keyword.isEmpty()) return new ArrayList<>();

        List<AppPrefs.Destination> places = new ArrayList<>();
        try {
            if (Thread.currentThread().isInterrupted()) throw new ProviderException(Failure.CANCELLED);
            configureAfterConsent(app);
            if (Thread.currentThread().isInterrupted()) throw new ProviderException(Failure.CANCELLED);
            if (suggestions) {
                InputtipsQuery request = new InputtipsQuery(keyword, city);
                request.setCityLimit(!city.isEmpty());
                List<Tip> tips = new Inputtips(app, request).requestInputtips();
                if (tips == null) throw new ProviderException(Failure.INVALID_RESPONSE);
                for (Tip tip : tips) {
                    if (tip != null) add(places, tip.getPoiID(), tip.getName(), tip.getDistrict(), tip.getAddress(), tip.getPoint());
                    if (places.size() == limit) break;
                }
            } else {
                PoiSearchV2.Query request = new PoiSearchV2.Query(keyword, "", city);
                request.setCityLimit(!city.isEmpty());
                request.setPageSize(limit);
                PoiResultV2 result = new PoiSearchV2(app, request).searchPOI();
                if (result == null || result.getPois() == null) throw new ProviderException(Failure.INVALID_RESPONSE);
                for (PoiItemV2 item : result.getPois()) {
                    if (item != null) add(places, item.getPoiId(), item.getTitle(), item.getCityName(), item.getSnippet(), item.getLatLonPoint());
                    if (places.size() == limit) break;
                }
            }
        } catch (AMapException error) {
            throw new ProviderException(classify(error.getErrorCode()));
        } catch (RuntimeException error) {
            throw new ProviderException(Failure.SERVICE);
        }
        return places;
    }

    private static synchronized void configureAfterConsent(Context context) {
        if (configured) return;
        ServiceSettings.updatePrivacyShow(context, true, true);
        ServiceSettings.updatePrivacyAgree(context, true);
        AMapUtilCoreApi.setCollectInfoEnable(false);
        ServiceSettings settings = ServiceSettings.getInstance();
        settings.setApiKey(BuildConfig.AMAP_ANDROID_KEY);
        settings.setProtocol(ServiceSettings.HTTPS);
        settings.setConnectionTimeOut(3000);
        settings.setSoTimeOut(4000);
        configured = true;
    }

    private static void add(List<AppPrefs.Destination> places, String id, String name, String city, String address,
                            LatLonPoint point) {
        AppPrefs.Destination place = toDestination(id, name, city, address, point);
        if (place != null) places.add(place);
    }

    static AppPrefs.Destination toDestination(String id, String name, String city, String address, LatLonPoint point) {
        if (point == null || name == null || name.trim().isEmpty()) return null;
        double lat = point.getLatitude();
        double lon = point.getLongitude();
        if (!Double.isFinite(lat) || !Double.isFinite(lon) || Math.abs(lat) > 90 || Math.abs(lon) > 180
                || (lat == 0 && lon == 0)) return null;
        double[] wgs = CoordinateTransform.gcj02ToWgs84(lat, lon);
        String poiId = id == null || id.isEmpty() ? name + ":" + lat + ":" + lon : id;
        return new AppPrefs.Destination("android-amap-" + poiId, name, address, city, wgs[0], wgs[1]);
    }

    static Failure classify(int code) {
        switch (code) {
            case AMapException.CODE_AMAP_SIGNATURE_ERROR:
            case AMapException.CODE_AMAP_INVALID_USER_KEY:
            case AMapException.CODE_AMAP_INVALID_USER_SCODE:
            case AMapException.CODE_AMAP_USERKEY_PLAT_NOMATCH:
            case AMapException.CODE_AMAP_INSUFFICIENT_PRIVILEGES:
            case AMapException.CODE_AMAP_USER_KEY_RECYCLED:
                return Failure.CONFIGURATION;
            case AMapException.CODE_AMAP_DAILY_QUERY_OVER_LIMIT:
            case AMapException.CODE_AMAP_ACCESS_TOO_FREQUENT:
            case AMapException.CODE_AMAP_IP_QUERY_OVER_LIMIT:
                return Failure.RATE_LIMIT;
            case AMapException.CODE_AMAP_ENGINE_CONNECT_TIMEOUT:
            case AMapException.CODE_AMAP_ENGINE_RETURN_TIMEOUT:
            case AMapException.CODE_AMAP_CLIENT_SOCKET_TIMEOUT_EXCEPTION:
                return Failure.TIMEOUT;
            case AMapException.CODE_AMAP_CLIENT_UNKNOWHOST_EXCEPTION:
            case AMapException.CODE_AMAP_CLIENT_NETWORK_EXCEPTION:
                return Failure.NETWORK;
            case AMapException.CODE_AMAP_ENGINE_RESPONSE_DATA_ERROR:
                return Failure.INVALID_RESPONSE;
            default:
                return Failure.SERVICE;
        }
    }
}
