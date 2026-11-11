package com.example.arrivalalarm;

import com.amap.api.services.core.AMapException;
import com.amap.api.services.core.LatLonPoint;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class AmapAndroidPlaceSearchTest {
    @Test
    public void mapsPlaceAndConvertsCoordinatesForReminder() {
        double lat = 30.658;
        double lon = 104.066;
        double[] gcj = CoordinateTransform.wgs84ToGcj02(lat, lon);
        AppPrefs.Destination place = AmapAndroidPlaceSearch.toDestination("poi-1", "天府广场站 A 口", "成都市",
                "人民南路一段", new LatLonPoint(gcj[0], gcj[1]));
        assertEquals("android-amap-poi-1", place.id);
        assertEquals("天府广场站 A 口", place.name);
        assertEquals("成都市", place.city);
        assertEquals("人民南路一段", place.address);
        assertEquals(lat, place.latitude, 0.00005);
        assertEquals(lon, place.longitude, 0.00005);
    }

    @Test
    public void omitsSuggestionsWithoutUsableCoordinates() {
        assertNull(AmapAndroidPlaceSearch.toDestination("id", "天府广场", "", "", null));
        assertNull(AmapAndroidPlaceSearch.toDestination("id", "天府广场", "", "", new LatLonPoint(0, 0)));
        assertNull(AmapAndroidPlaceSearch.toDestination("id", "", "", "", new LatLonPoint(30, 104)));
    }

    @Test
    public void keepsSdkFailuresDistinct() {
        assertEquals(PlaceSearchPipeline.Failure.CONFIGURATION,
                AmapAndroidPlaceSearch.classify(AMapException.CODE_AMAP_INVALID_USER_SCODE));
        assertEquals(PlaceSearchPipeline.Failure.RATE_LIMIT,
                AmapAndroidPlaceSearch.classify(AMapException.CODE_AMAP_ACCESS_TOO_FREQUENT));
        assertEquals(PlaceSearchPipeline.Failure.TIMEOUT,
                AmapAndroidPlaceSearch.classify(AMapException.CODE_AMAP_CLIENT_SOCKET_TIMEOUT_EXCEPTION));
    }
}
