package com.example.arrivalalarm;

import java.net.URLDecoder;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses only text explicitly supplied to this app. It performs no network access. */
public final class PlaceInputParser {
    private static final String NUMBER = "([+-]?\\d{1,3}(?:\\.\\d+)?)";
    private static final Pattern LAT_PARAM = Pattern.compile("(?:[?&#;]|\\b)(?:dlat|lat|latitude)=" + NUMBER, Pattern.CASE_INSENSITIVE);
    private static final Pattern LNG_PARAM = Pattern.compile("(?:[?&#;]|\\b)(?:dlon|dlng|lng|lon|longitude)=" + NUMBER, Pattern.CASE_INSENSITIVE);
    private static final Pattern ROUTE_DESTINATION = Pattern.compile("[?&;](?:to|destination|dest)=([^&#;]*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROUTE_HINT = Pattern.compile(
            "[?&;](?:origin|saddr|slat|slon)=|[?&;](?:from|via)=" + NUMBER + "[,，]" + NUMBER
                    + "|://(?:[^?&#\\s]*/)?(?:direction|navigation|routeplan|route|dir)(?=[/?#\\s]|$)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ROUTE_POINT = Pattern.compile(NUMBER + "\\s*[,，]\\s*" + NUMBER + "(?:\\s*[,，]\\s*(.+))?");
    private static final Pattern DESTINATION_LAT = Pattern.compile("[?&;]dlat=([^&#;]*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DESTINATION_LNG = Pattern.compile("[?&;](?:dlon|dlng)=([^&#;]*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DESTINATION_NAME = Pattern.compile("[?&;]dname=([^&#;]*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern AMAP_CRS = Pattern.compile("[?&;]coordinate=([^&#;\\s]*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern AMAP_ANDROID_OFFSET = Pattern.compile("[?&;]dev=([^&#;\\s]*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BAIDU_CRS = Pattern.compile("[?&;]coord_type=([^&#;\\s]*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TENCENT_ROUTE = Pattern.compile(
            "\\b(?:qqmap://map|https?://apis\\.map\\.qq\\.com/uri/v1)/routeplan/?(?:\\?([^#\\s]*))?(?=[#\\s]|$)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TENCENT_ROUTE_NAME = Pattern.compile("(?:^|&)to=([^&]*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TENCENT_ROUTE_COORD = Pattern.compile("(?:^|&)tocoord=([^&]*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TENCENT_ROUTE_CRS = Pattern.compile("(?:^|&)coord_type=([^&]*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern POSITION_LNG_LAT = Pattern.compile("(?:position|center)=" + NUMBER + "[,，]" + NUMBER, Pattern.CASE_INSENSITIVE);
    private static final Pattern LOCATION_LAT_LNG = Pattern.compile("(?:location|coord)[:=]" + NUMBER + "[,，]" + NUMBER, Pattern.CASE_INSENSITIVE);
    private static final Pattern AT_LAT_LNG = Pattern.compile("@" + NUMBER + "[,，]" + NUMBER);
    private static final Pattern PAIR = Pattern.compile(NUMBER + "\\s*[,，]\\s*" + NUMBER);
    private static final Pattern URL = Pattern.compile("(?:https?|geo|amapuri|androidamap|baidumap|bdapp|qqmap)://\\S+", Pattern.CASE_INSENSITIVE);
    private static final Pattern NAME_PARAM = Pattern.compile("(?:[?&#;]|\\b)(?:dname|poiname|title|name|address|addr)[:=]([^&#;]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUERY_LABEL = Pattern.compile("(?:[?&#;]|\\b)q=" + NUMBER + "[,，]" + NUMBER + "\\(([^)]+)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern GEO_QUERY = Pattern.compile("\\bgeo:[^?\\s]+\\?(?:[^&#]*&)*?q=([^&#]*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern GEO_POINT = Pattern.compile(NUMBER + "[,，]" + NUMBER + "(?:\\(([^)]*)\\))?");

    private static final int CRS_UNKNOWN = 0;
    private static final int CRS_GCJ02 = 1;
    private static final int CRS_BD09 = 2;
    private static final int CRS_WGS84 = 3;
    private static final int CRS_UNSUPPORTED = 4;
    private static final String TENCENT_SOURCE = "腾讯地图分享";

    private PlaceInputParser() {}

    public static Result parse(String input) {
        if (PlaceSearchLimits.inputTooLong(input)) return null;
        String raw = input == null ? "" : input.trim();
        if (raw.isEmpty()) return null;
        Matcher tencentRoute = TENCENT_ROUTE.matcher(raw);
        if (tencentRoute.find()) return parseTencentRoute(tencentRoute.group(1));
        String decoded = decodeTwice(raw);
        String lower = decoded.toLowerCase(Locale.ROOT);
        String source = sourceLabel(lower);
        int crs = coordinateSystem(lower);

        String geoQuery = parameter(GEO_QUERY, decoded);
        if (geoQuery != null) {
            Matcher point = GEO_POINT.matcher(geoQuery);
            return point.matches()
                    ? result(cleanName(point.group(3)), number(point.group(1)), number(point.group(2)), "外部链接", CRS_WGS84)
                    : result(destinationName(geoQuery), null, null, "外部链接", CRS_UNKNOWN);
        }

        String endpoint = parameter(ROUTE_DESTINATION, decoded);
        if (endpoint != null || ROUTE_HINT.matcher(decoded).find()) {
            return parseRoute(decoded, lower, source, crs, endpoint);
        }

        Double lat = null;
        Double lng = null;

        Matcher latParam = LAT_PARAM.matcher(decoded);
        Matcher lngParam = LNG_PARAM.matcher(decoded);
        if (latParam.find() && lngParam.find()) {
            lat = number(latParam.group(1));
            lng = number(lngParam.group(1));
        } else {
            Matcher position = POSITION_LNG_LAT.matcher(decoded);
            Matcher location = LOCATION_LAT_LNG.matcher(decoded);
            Matcher at = AT_LAT_LNG.matcher(decoded);
            if (position.find()) {
                lng = number(position.group(1));
                lat = number(position.group(2));
            } else if (location.find()) {
                lat = number(location.group(1));
                lng = number(location.group(2));
            } else if (at.find()) {
                lat = number(at.group(1));
                lng = number(at.group(2));
            } else {
                Matcher pair = PAIR.matcher(decoded);
                if (pair.find()) {
                    double first = number(pair.group(1));
                    double second = number(pair.group(2));
                    if (valid(first, second)) {
                        lat = first;
                        lng = second;
                    } else if (valid(second, first)) {
                        lat = second;
                        lng = first;
                    }
                }
            }
        }
        String name = "";
        Matcher label = QUERY_LABEL.matcher(decoded);
        if (name.isEmpty() && label.find()) name = cleanName(label.group(3));
        Matcher named = NAME_PARAM.matcher(decoded);
        if (name.isEmpty() && named.find()) name = cleanName(named.group(1));
        if (name.isEmpty()) {
            name = URL.matcher(decoded).replaceAll(" ");
            name = PAIR.matcher(name).replaceAll(" ");
            name = cleanName(name);
        }

        return result(name, lat, lng, source, crs);
    }

    private static Result parseTencentRoute(String query) {
        if (query == null) return null;
        // Read fields before decoding them so encoded text cannot become a query delimiter.
        String rawName = parameter(TENCENT_ROUTE_NAME, query);
        String name = rawName == null ? "" : destinationName(decodeTwice(rawName));
        String coordinates = parameter(TENCENT_ROUTE_COORD, query);
        if (coordinates != null) {
            Matcher point = PAIR.matcher(decodeTwice(coordinates).trim());
            if (point.matches()) {
                String declared = parameter(TENCENT_ROUTE_CRS, query);
                if (declared != null) declared = decodeTwice(declared).trim();
                int crs = declared == null || "2".equals(declared) ? CRS_GCJ02
                        : "1".equals(declared) ? CRS_WGS84 : CRS_UNSUPPORTED;
                return result(name, number(point.group(1)), number(point.group(2)), TENCENT_SOURCE, crs);
            }
        }
        return result(name, null, null, TENCENT_SOURCE, CRS_UNKNOWN);
    }

    private static Result parseRoute(String decoded, String lower, String source, int crs, String endpoint) {
        // Once this is known to be a route, generic coordinates may belong to the origin or a waypoint.
        String name = destinationName(parameter(DESTINATION_NAME, decoded));
        if (endpoint == null || endpoint.isEmpty()) {
            return result(name, number(parameter(DESTINATION_LAT, decoded)),
                    number(parameter(DESTINATION_LNG, decoded)), source, crs);
        }
        boolean baidu = isBaidu(lower);
        String value = baidu && endpoint.regionMatches(true, 0, "latlng:", 0, 7)
                ? endpoint.substring(7).trim() : endpoint;
        Matcher point = ROUTE_POINT.matcher(value);
        if ((baidu || isAmap(lower)) && point.matches()) {
            Double first = number(point.group(1));
            Double second = number(point.group(2));
            if (name.isEmpty()) name = destinationName(point.group(3));
            return result(name, baidu ? first : second, baidu ? second : first, source, crs);
        }
        return result(name.isEmpty() ? destinationName(endpoint) : name, null, null, source, crs);
    }

    private static String destinationName(String value) {
        // A name-only result is passed back through the parser before a place search.
        if (value == null || value.indexOf('=') >= 0 || value.indexOf(':') >= 0
                || value.indexOf('|') >= 0 || PAIR.matcher(value).find()) return "";
        return cleanName(value);
    }

    private static String parameter(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private static Result result(String name, Double lat, Double lng, String source, int crs) {
        if (crs == CRS_UNSUPPORTED || !valid(lat, lng)) {
            return name.isEmpty() ? null : new Result(name, null, null, source, false);
        }
        if (crs == CRS_GCJ02) {
            double[] wgs = CoordinateTransform.gcj02ToWgs84(lat, lng);
            lat = wgs[0];
            lng = wgs[1];
        } else if (crs == CRS_BD09) {
            double[] wgs = CoordinateTransform.bd09ToWgs84(lat, lng);
            lat = wgs[0];
            lng = wgs[1];
        }
        return new Result(name, lat, lng, source, crs != CRS_UNKNOWN);
    }

    private static int coordinateSystem(String lower) {
        if (isBaidu(lower)) {
            String declared = parameter(BAIDU_CRS, lower);
            if (declared == null || "bd09ll".equals(declared)) return CRS_BD09;
            if ("gcj02".equals(declared)) return CRS_GCJ02;
            return "wgs84".equals(declared) ? CRS_WGS84 : CRS_UNSUPPORTED;
        }
        if (isAmap(lower)) {
            String declared = parameter(AMAP_CRS, lower);
            if (declared == null && (lower.contains("amapuri://") || lower.contains("androidamap://"))) {
                String offset = parameter(AMAP_ANDROID_OFFSET, lower);
                if ("1".equals(offset)) return CRS_WGS84;
                if (offset != null && !"0".equals(offset)) return CRS_UNSUPPORTED;
            }
            if (declared == null || "gaode".equals(declared) || "gcj02".equals(declared)) return CRS_GCJ02;
            return "wgs84".equals(declared) ? CRS_WGS84 : CRS_UNSUPPORTED;
        }
        if (lower.contains("map.qq.com") || lower.contains("qqmap://")) return CRS_GCJ02;
        return CRS_UNKNOWN;
    }

    private static boolean isAmap(String lower) {
        return lower.contains("amap.com") || lower.contains("amapuri://") || lower.contains("androidamap://");
    }

    private static boolean isBaidu(String lower) {
        return lower.contains("baidu.com") || lower.contains("baidumap://") || lower.contains("bdapp://");
    }

    private static String sourceLabel(String lower) {
        if (isAmap(lower)) return "高德地图分享";
        if (isBaidu(lower)) return "百度地图分享";
        if (lower.contains("map.qq.com") || lower.contains("qqmap://")) return TENCENT_SOURCE;
        if (lower.contains("didi") || lower.contains("didiglobal")) return "打车软件分享";
        if (lower.contains("://")) return "外部链接";
        return "手动输入";
    }

    private static String decodeTwice(String value) {
        String result = value;
        for (int i = 0; i < 2; i++) {
            try {
                String decoded = URLDecoder.decode(result, "UTF-8");
                if (decoded.equals(result)) break;
                result = decoded;
            } catch (Exception ignored) { break; }
        }
        return result;
    }

    private static String cleanName(String value) {
        if (value == null) return "";
        String result = value.replaceAll("[\\p{Cntrl}]", " ")
                .replaceAll("(?:复制|打开|查看|来自|分享)(?:链接|到)?", " ")
                .replaceAll("(?:高德|百度|腾讯)地图(?:APP)?", " ")
                .replaceAll("^[,，;；:：\\s]+|[,，;；:：\\s]+$", "")
                .replaceAll("\\s+", " ").trim();
        if (result.length() > 100) result = result.substring(0, 100).trim();
        return result;
    }

    private static Double number(String value) {
        try { return value == null ? null : Double.valueOf(value); }
        catch (NumberFormatException ignored) { return null; }
    }

    private static boolean valid(Double lat, Double lng) {
        return lat != null && lng != null && lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180;
    }

    public static final class Result {
        public final String name;
        public final Double latitude;
        public final Double longitude;
        public final String source;
        /** 坐标系已识别，返回值为 WGS-84。 */
        public final boolean coordinateSystemVerified;

        Result(String name, Double latitude, Double longitude, String source, boolean coordinateSystemVerified) {
            this.name = name == null ? "" : name;
            this.latitude = latitude;
            this.longitude = longitude;
            this.source = source == null ? "手动输入" : source;
            this.coordinateSystemVerified = coordinateSystemVerified;
        }

        public boolean hasCoordinates() { return latitude != null && longitude != null; }
    }
}
