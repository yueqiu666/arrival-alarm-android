package com.example.arrivalalarm;

/** Keep complete provider text visible before the user commits a new destination. */
public final class DestinationConfirmation {
    private DestinationConfirmation() { }

    public static String details(String name, String city, String address, double lat, double lng, boolean inferred) {
        return name + "\n城市/区域：" + (city.isEmpty() ? "服务未提供，请核对坐标" : city)
                + "\n地址：" + (address.isEmpty() ? "服务未提供，请核对坐标" : address)
                + String.format(java.util.Locale.CHINA, "\n坐标（WGS-84）：%.6f, %.6f", lat, lng)
                + (inferred ? "\n名称由输入关键词恢复，坐标来自手机系统地点服务。" : "")
                + "\n\n请确认这是你要提醒的目的地。";
    }
}
