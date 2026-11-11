package com.example.arrivalalarm;

public final class CoordinateTransform {
    private static final double PI = Math.PI;
    private static final double A = 6378245.0;
    private static final double EE = 0.00669342162296594323;

    private CoordinateTransform() { }

    public static double[] gcj02ToWgs84(double latitude, double longitude) {
        if (outsideChina(latitude, longitude)) return new double[]{latitude, longitude};
        double[] gcj = wgs84ToGcj02(latitude, longitude);
        return new double[]{latitude * 2 - gcj[0], longitude * 2 - gcj[1]};
    }

    public static double[] wgs84ToGcj02(double latitude, double longitude) {
        if (outsideChina(latitude, longitude)) return new double[]{latitude, longitude};
        double dLat = transformLat(longitude - 105.0, latitude - 35.0);
        double dLng = transformLng(longitude - 105.0, latitude - 35.0);
        double radLat = latitude / 180.0 * PI;
        double magic = Math.sin(radLat);
        magic = 1 - EE * magic * magic;
        double sqrtMagic = Math.sqrt(magic);
        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI);
        dLng = (dLng * 180.0) / (A / sqrtMagic * Math.cos(radLat) * PI);
        return new double[]{latitude + dLat, longitude + dLng};
    }

    /** 百度地图 BD-09 -> WGS-84。百度分享链接里的坐标属于 BD-09。 */
    public static double[] bd09ToWgs84(double latitude, double longitude) {
        double[] gcj = bd09ToGcj02(latitude, longitude);
        return gcj02ToWgs84(gcj[0], gcj[1]);
    }

    /** 百度地图 BD-09 -> GCJ-02。 */
    public static double[] bd09ToGcj02(double latitude, double longitude) {
        if (outsideChina(latitude, longitude)) return new double[]{latitude, longitude};
        double x = longitude - 0.0065;
        double y = latitude - 0.006;
        double z = Math.sqrt(x * x + y * y) - 0.00002 * Math.sin(y * PI * 3000.0 / 180.0);
        double theta = Math.atan2(y, x) - 0.000003 * Math.cos(x * PI * 3000.0 / 180.0);
        return new double[]{z * Math.sin(theta), z * Math.cos(theta)};
    }

    private static boolean outsideChina(double latitude, double longitude) {
        return longitude < 72.004 || longitude > 137.8347 || latitude < 0.8293 || latitude > 55.8271;
    }

    private static double transformLat(double x, double y) {
        double result = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y
                + 0.2 * Math.sqrt(Math.abs(x));
        result += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0;
        result += (20.0 * Math.sin(y * PI) + 40.0 * Math.sin(y / 3.0 * PI)) * 2.0 / 3.0;
        result += (160.0 * Math.sin(y / 12.0 * PI) + 320 * Math.sin(y * PI / 30.0)) * 2.0 / 3.0;
        return result;
    }

    private static double transformLng(double x, double y) {
        double result = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y
                + 0.1 * Math.sqrt(Math.abs(x));
        result += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0;
        result += (20.0 * Math.sin(x * PI) + 40.0 * Math.sin(x / 3.0 * PI)) * 2.0 / 3.0;
        result += (150.0 * Math.sin(x / 12.0 * PI) + 300.0 * Math.sin(x / 30.0 * PI)) * 2.0 / 3.0;
        return result;
    }
}
