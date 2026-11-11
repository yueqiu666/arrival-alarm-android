package com.example.arrivalalarm;

import java.util.Calendar;
import java.util.TimeZone;

public final class CoreLogicTest {
    private static int passed;

    public static void main(String[] args) {
        testDistance();
        testRadius();
        testArrivalTriggersOnFirstReliableFix();
        testOutsideRadiusDoesNotTrigger();
        testLowAccuracyRejected();
        testInvalidAndStaleFixesRejected();
        testJourneyBySpeed();
        testWalkingDoesNotPrompt();
        testJourneyRearms();
        testPlainCoordinates();
        testReversedPlainCoordinates();
        testAmapDestinationLink();
        testAmapMobileLink();
        testBaiduMarkerLink();
        testTencentMapLink();
        testUnknownCoordinateSourceKeepsRawValue();
        testBd09ToGcj02RoundTrip();
        testEncodedMapLink();
        testInvalidCoordinatesRejected();
        testTransitSignal();
        testUnrelatedNotificationRejected();
        testUnsupportedPackageRejected();
        testStationSignal();
        testBusStopRejected();
        testFixedAlarmLaterToday();
        testFixedAlarmSkipsWeekend();
        testEverydayAlarmAdvances();
        testLocationRecoveryPolicy();
        testReminderStateMachine();
        testReminderProviderRegistry();
        testAmapCoordinateConversion();
        testRejectsBrokenSystemPlace();
        testAcceptsRelevantPlaceSuggestion();
        testRecoversTruncatedSystemPlaceName();
        testRepeatedSuggestionStateRendersOnce();
        testSuggestionGateResets();
        testFastSearchDebounce();
        testAsyncResultGate();
        testPlaceSearchPipelineSeparatesNoMatchAndFailure();
        testPlaceSearchPipelineClassifiesTimeout();
        testPlaceInputLengthBound();
        testDestinationConfirmationDetails();
        System.out.println("CoreLogicTest: " + passed + " tests passed");
    }

    private static void testDistance() {
        double distance = CoreLogic.distanceMeters(0, 0, 1, 0);
        check(distance > 111_000 && distance < 112_000, "distance");
    }

    private static void testRadius() {
        check(CoreLogic.clampRadius(100) == 300, "min radius");
        check(CoreLogic.clampRadius(9000) == 5000, "max radius");
    }

    private static void testArrivalTriggersOnFirstReliableFix() {
        CoreLogic.ArrivalTracker tracker = new CoreLogic.ArrivalTracker(31, 121, 1000);
        CoreLogic.ArrivalResult result = tracker.update(new CoreLogic.LocationFix(31.001, 121, 20, 8, 1_000));
        check(result.triggered && "inside-radius".equals(result.reason), "arrival on first reliable fix");
    }

    private static void testOutsideRadiusDoesNotTrigger() {
        CoreLogic.ArrivalTracker tracker = new CoreLogic.ArrivalTracker(31, 121, 300);
        CoreLogic.ArrivalResult result = tracker.update(new CoreLogic.LocationFix(31.01, 121, 20, 8, 1_000));
        check(!result.triggered && "outside".equals(result.reason), "outside radius");
    }

    private static void testLowAccuracyRejected() {
        CoreLogic.ArrivalTracker tracker = new CoreLogic.ArrivalTracker(31, 121, 300);
        CoreLogic.ArrivalResult result = tracker.update(new CoreLogic.LocationFix(31, 121, 180, 0, 1_000));
        check(!result.triggered && "low-accuracy".equals(result.reason), "accuracy");
    }

    private static void testInvalidAndStaleFixesRejected() {
        CoreLogic.ArrivalTracker tracker = new CoreLogic.ArrivalTracker(31, 121, 300);
        check(!tracker.update(new CoreLogic.LocationFix(Double.NaN, 121, 10, 0, 0)).triggered,
                "non-finite coordinates cannot trigger");
        check(!tracker.update(new CoreLogic.LocationFix(31, 121, Float.NaN, 0, 0)).triggered,
                "non-finite accuracy cannot trigger");
        long now = 400_000_000_000L;
        check(!CoreLogic.freshArrivalFix(1, 0, now), "old cached fix rejected");
        check(!CoreLogic.freshArrivalFix(now - 2, now - 1, now), "out-of-order fix rejected");
        check(!CoreLogic.freshArrivalFix(now + 1, 0, now), "future fix rejected");
        check(CoreLogic.freshArrivalFix(now - 1, 0, now), "recent fix accepted");
        check(tracker.update(new CoreLogic.LocationFix(31, 121, 10, 0, 0)).triggered,
                "reliable fix can still trigger after an invalid fix");
    }

    private static void testJourneyBySpeed() {
        CoreLogic.JourneyDetector detector = new CoreLogic.JourneyDetector(31, 121);
        detector.update(new CoreLogic.LocationFix(31, 121.006, 30, 8, 1_000));
        boolean prompted = detector.update(new CoreLogic.LocationFix(31, 121.010, 30, 8, 51_000));
        check(prompted && CoreLogic.JourneyDetector.PROMOTED.equals(detector.state), "journey speed");
    }

    private static void testWalkingDoesNotPrompt() {
        CoreLogic.JourneyDetector detector = new CoreLogic.JourneyDetector(31, 121);
        detector.update(new CoreLogic.LocationFix(31, 121.006, 20, 1.2f, 1_000));
        boolean prompted = detector.update(new CoreLogic.LocationFix(31, 121.0065, 20, 1.2f, 51_000));
        check(!prompted, "walking");
    }

    private static void testJourneyRearms() {
        CoreLogic.JourneyDetector detector = new CoreLogic.JourneyDetector(31, 121);
        detector.state = CoreLogic.JourneyDetector.DISMISSED;
        detector.update(new CoreLogic.LocationFix(31.02, 121, 20, 0, 10_000));
        detector.update(new CoreLogic.LocationFix(31.02, 121, 20, 0, 10_000 + CoreLogic.RESET_STILL_MS));
        check(CoreLogic.JourneyDetector.ANCHORED.equals(detector.state), "rearm");
    }

    private static void testPlainCoordinates() {
        PlaceInputParser.Result result = PlaceInputParser.parse("上海站 31.2495,121.4557");
        check(result != null && result.hasCoordinates() && close(result.latitude, 31.2495) && "上海站".equals(result.name), "plain coordinates");
    }

    private static void testReversedPlainCoordinates() {
        PlaceInputParser.Result result = PlaceInputParser.parse("上海站 121.4557,31.2495");
        check(result != null && result.hasCoordinates() && close(result.latitude, 31.2495) && close(result.longitude, 121.4557), "reversed plain coordinates");
    }

    private static void testAmapDestinationLink() {
        PlaceInputParser.Result result = PlaceInputParser.parse("https://uri.amap.com/navigation?to=121.4557,31.2495,上海站");
        check(result != null && result.hasCoordinates() && "上海站".equals(result.name), "amap destination");
        check(result.coordinateSystemVerified, "amap coordinate system recognised");
        double[] backToGcj = CoordinateTransform.wgs84ToGcj02(result.latitude, result.longitude);
        check(Math.abs(backToGcj[0] - 31.2495) < 0.00002 && Math.abs(backToGcj[1] - 121.4557) < 0.00002,
                "amap link coordinates converted from GCJ-02");
    }

    private static void testAmapMobileLink() {
        PlaceInputParser.Result result = PlaceInputParser.parse("androidamap://viewMap?poiname=上海站&lat=31.2495&lon=121.4557&dev=1");
        check(result != null && result.hasCoordinates() && "上海站".equals(result.name), "amap mobile");
    }

    private static void testBaiduMarkerLink() {
        PlaceInputParser.Result result = PlaceInputParser.parse("baidumap://map/marker?location=31.2495,121.4557&title=上海站");
        check(result != null && result.hasCoordinates() && "上海站".equals(result.name), "baidu marker");
        check(result.coordinateSystemVerified, "baidu coordinate system recognised");
        double dLat = result.latitude - 31.2495;
        double dLng = result.longitude - 121.4557;
        double shift = Math.sqrt(dLat * dLat + dLng * dLng);
        check(shift > 0.003 && shift < 0.03, "baidu link coordinates converted away from BD-09");
    }

    private static void testTencentMapLink() {
        PlaceInputParser.Result result = PlaceInputParser.parse("https://map.qq.com/?location=31.2495,121.4557&title=上海站");
        check(result != null && result.hasCoordinates(), "tencent map coordinates");
        check(result.coordinateSystemVerified, "tencent coordinate system recognised");
        double[] backToGcj = CoordinateTransform.wgs84ToGcj02(result.latitude, result.longitude);
        check(Math.abs(backToGcj[0] - 31.2495) < 0.00002 && Math.abs(backToGcj[1] - 121.4557) < 0.00002,
                "tencent link coordinates converted from GCJ-02");
    }

    private static void testUnknownCoordinateSourceKeepsRawValue() {
        PlaceInputParser.Result result = PlaceInputParser.parse("https://maps.google.com/?q=31.2495,121.4557");
        check(result != null && result.hasCoordinates() && !result.coordinateSystemVerified, "unknown source flagged");
        check(close(result.latitude, 31.2495) && close(result.longitude, 121.4557), "unknown source not converted");
    }

    private static void testBd09ToGcj02RoundTrip() {
        double gcjLat = 39.915119;
        double gcjLng = 116.403963;
        double z = Math.sqrt(gcjLng * gcjLng + gcjLat * gcjLat)
                + 0.00002 * Math.sin(gcjLat * Math.PI * 3000.0 / 180.0);
        double theta = Math.atan2(gcjLat, gcjLng)
                + 0.000003 * Math.cos(gcjLng * Math.PI * 3000.0 / 180.0);
        double bdLat = z * Math.sin(theta) + 0.006;
        double bdLng = z * Math.cos(theta) + 0.0065;
        double[] gcj = CoordinateTransform.bd09ToGcj02(bdLat, bdLng);
        check(Math.abs(gcj[0] - gcjLat) < 0.00002 && Math.abs(gcj[1] - gcjLng) < 0.00002, "bd09 to gcj02 round trip");
    }

    private static void testEncodedMapLink() {
        PlaceInputParser.Result result = PlaceInputParser.parse("https://example.test/?lat=31.2495%26lng=121.4557%26name=%E4%B8%8A%E6%B5%B7%E7%AB%99");
        check(result != null && result.hasCoordinates() && "上海站".equals(result.name), "encoded map link");
    }

    private static void testInvalidCoordinatesRejected() {
        PlaceInputParser.Result result = PlaceInputParser.parse("未知位置 191.2,95.1");
        check(result != null && !result.hasCoordinates(), "invalid coordinates");
    }

    private static void testTransitSignal() {
        check(TransitSignalMatcher.matches("com.MobileTicket", "铁路12306", "G1次列车即将检票进站"), "transit notification");
    }

    private static void testUnrelatedNotificationRejected() {
        check(!TransitSignalMatcher.matches("com.eg.android.AlipayGphone", "到账提醒", "收到转账 20 元"), "unrelated notification");
    }

    private static void testUnsupportedPackageRejected() {
        check(!TransitSignalMatcher.matches("com.example.news", "地铁新闻", "列车正在进站"), "unsupported notification package");
    }

    private static void testStationSignal() {
        check("上海虹桥站".equals(StationSignalMatcher.findStation("中国上海市闵行区上海虹桥站")), "rail station signal");
        check("人民广场地铁站".equals(StationSignalMatcher.findStation("人民广场地铁站 1号口")), "metro station signal");
    }

    private static void testBusStopRejected() {
        check(StationSignalMatcher.findStation("人民路公交站") == null, "bus stop rejected");
    }

    private static void testFixedAlarmLaterToday() {
        TimeZone zone = TimeZone.getTimeZone("Asia/Shanghai");
        Calendar now = date(zone, 2026, Calendar.AUGUST, 24, 7, 30);
        long next = ScheduleLogic.nextTriggerMillis(8, 0, ScheduleLogic.WORKDAYS_MASK, now.getTimeInMillis(), zone);
        Calendar result = Calendar.getInstance(zone);
        result.setTimeInMillis(next);
        check(result.get(Calendar.DAY_OF_MONTH) == 24 && result.get(Calendar.HOUR_OF_DAY) == 8, "fixed alarm later today");
    }

    private static void testFixedAlarmSkipsWeekend() {
        TimeZone zone = TimeZone.getTimeZone("Asia/Shanghai");
        Calendar friday = date(zone, 2026, Calendar.AUGUST, 28, 19, 0);
        long next = ScheduleLogic.nextTriggerMillis(8, 0, ScheduleLogic.WORKDAYS_MASK, friday.getTimeInMillis(), zone);
        Calendar result = Calendar.getInstance(zone);
        result.setTimeInMillis(next);
        check(result.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY && result.get(Calendar.DAY_OF_MONTH) == 31, "fixed alarm skips weekend");
    }

    private static void testEverydayAlarmAdvances() {
        TimeZone zone = TimeZone.getTimeZone("Asia/Shanghai");
        Calendar now = date(zone, 2026, Calendar.AUGUST, 24, 8, 0);
        long next = ScheduleLogic.nextTriggerMillis(8, 0, ScheduleLogic.EVERY_DAY_MASK, now.getTimeInMillis(), zone);
        Calendar result = Calendar.getInstance(zone);
        result.setTimeInMillis(next);
        check(result.get(Calendar.DAY_OF_MONTH) == 25 && result.get(Calendar.HOUR_OF_DAY) == 8, "everyday advances");
    }

    private static void testReminderStateMachine() {
        AppPrefs.Destination destination = new AppPrefs.Destination("task-3", "杭州东站", "", "", 30.3065, 120.2048);
        AppPrefs.ReminderTask task = new AppPrefs.ReminderTask("task-3", destination, 1000,
                AppPrefs.ReminderTask.STATUS_ACTIVE, 1_000L);
        ReminderRuntime.Snapshot starting = ReminderRuntime.Snapshot.starting(120_000L);
        ReminderRuntime.Snapshot ready = ReminderRuntime.Snapshot.locationReady(1, 120_000L, 120_000L, 0L);
        ReminderRuntime.Snapshot located = ready.locationReceived(120_001L);
        ReminderRuntime.Snapshot stale = located.heartbeat(250_000L - ReminderRuntime.HEARTBEAT_TIMEOUT_MS - 1L);
        check(ReminderStateLogic.classify(null, ReminderRuntime.Snapshot.stopped(), 120_000L) == ReminderStateLogic.State.EMPTY,
                "empty reminder state");
        check(ReminderStateLogic.classify(task, ReminderRuntime.Snapshot.stopped(), 120_000L) == ReminderStateLogic.State.NEEDS_RESUME,
                "active task without service needs immediate resume");
        check(ReminderStateLogic.classify(task, starting, 120_001L) == ReminderStateLogic.State.STARTING,
                "start request is not monitoring");
        task.status = AppPrefs.ReminderTask.STATUS_STARTING;
        check(ReminderStateLogic.classify(task, ReminderRuntime.Snapshot.stopped(), 120_001L)
                        == ReminderStateLogic.State.NEEDS_RESUME,
                "starting task without service needs recovery");
        check(ReminderStateLogic.classify(task, located, 120_001L)
                        == ReminderStateLogic.State.STARTING,
                "starting task tolerates location-ready handshake without claiming monitoring");
        check(ReminderStateLogic.classify(task, ReminderRuntime.Snapshot.foreground(120_000L, 120_000L), 120_001L)
                        == ReminderStateLogic.State.STARTING,
                "starting task tolerates foreground handshake");
        task.status = AppPrefs.ReminderTask.STATUS_ACTIVE;
        check(ReminderStateLogic.classify(task, ready, 120_001L) == ReminderStateLogic.State.WAITING_LOCATION,
                "registered listener waits for first location");
        check(ReminderStateLogic.classify(task, ReminderRuntime.Snapshot.locationReady(0, 120_000L, 120_000L, 0L), 120_001L)
                        == ReminderStateLogic.State.NEEDS_RESUME,
                "no registered provider needs resume");
        check(ReminderStateLogic.classify(task, ReminderRuntime.Snapshot.foreground(120_000L, 120_000L),
                        120_001L) == ReminderStateLogic.State.WAITING_LOCATION,
                "foreground startup waits briefly");
        check(ReminderStateLogic.classify(task, ReminderRuntime.Snapshot.foreground(120_000L, 120_000L),
                        120_000L + ReminderRuntime.START_TIMEOUT_MS + 1L) == ReminderStateLogic.State.NEEDS_RESUME,
                "stalled foreground startup needs resume");
        check(ReminderStateLogic.classify(task, located, 120_001L) == ReminderStateLogic.State.MONITORING,
                "location-ready service is monitoring");
        ReminderRuntime.Snapshot recovering = ReminderRuntime.Snapshot.recovering(
                2, 1, 121_000L, 121_000L, 120_001L);
        check(ReminderStateLogic.classify(task, recovering, 121_001L) == ReminderStateLogic.State.RECOVERING,
                "bounded location recovery is visible without claiming monitoring");
        check(ReminderStateLogic.classify(task, recovering,
                        121_001L + ReminderRuntime.HEARTBEAT_TIMEOUT_MS + 1L)
                        == ReminderStateLogic.State.NEEDS_RESUME,
                "stale recovery service needs resume");
        check(ReminderStateLogic.classify(task, ready.heartbeat(120_000L + ReminderRuntime.LOCATION_TIMEOUT_MS),
                        120_000L + ReminderRuntime.LOCATION_TIMEOUT_MS)
                        == ReminderStateLogic.State.WAITING_LOCATION,
                "first location is allowed within location timeout");
        check(ReminderStateLogic.classify(task, ready.heartbeat(120_000L + ReminderRuntime.LOCATION_TIMEOUT_MS + 1L),
                        120_000L + ReminderRuntime.LOCATION_TIMEOUT_MS + 1L)
                        == ReminderStateLogic.State.NEEDS_RESUME,
                "missing first location eventually needs resume");
        check(ReminderStateLogic.classify(task, located.heartbeat(
                        120_001L + ReminderRuntime.LOCATION_TIMEOUT_MS + 1L)
                        , 120_001L + ReminderRuntime.LOCATION_TIMEOUT_MS + 1L)
                        == ReminderStateLogic.State.NEEDS_RESUME,
                "stale location eventually needs resume");
        check(ReminderStateLogic.classify(task, stale, 250_000L) == ReminderStateLogic.State.NEEDS_RESUME,
                "stale service heartbeat needs resume");
        task.markInterrupted();
        check(ReminderStateLogic.classify(task, located, 250_000L) == ReminderStateLogic.State.NEEDS_RESUME,
                "interrupted task stays resumable");
        task.resume();
        check(task.isStarting() && ReminderStateLogic.classify(task,
                        ReminderRuntime.Snapshot.starting(300_000L), 300_001L) == ReminderStateLogic.State.STARTING,
                "resumed task starts before monitoring");
        task.markActive();
        check(task.isActive(), "service confirms active task");
        task.status = AppPrefs.ReminderTask.STATUS_ALERTING;
        check(ReminderStateLogic.classify(task, ReminderRuntime.Snapshot.stopped(), 300_001L) == ReminderStateLogic.State.ALERTING,
                "alerting task remains alerting");
    }

    private static void testLocationRecoveryPolicy() {
        check(LocationRecoveryPolicy.MAX_ATTEMPTS == 2, "location recovery has a hard retry limit");
        check(LocationRecoveryPolicy.hasAttemptsRemaining(0), "initial recovery attempt is allowed");
        check(LocationRecoveryPolicy.hasAttemptsRemaining(1), "second recovery attempt is allowed");
        check(!LocationRecoveryPolicy.hasAttemptsRemaining(2), "recovery stops after two attempts");
        check(LocationRecoveryPolicy.delayAfterAttempt(1) == 10_000L, "first recovery retry uses 10 seconds");
        check(LocationRecoveryPolicy.delayAfterAttempt(2) == 30_000L, "second recovery retry uses 30 seconds");
    }

    private static void testAmapCoordinateConversion() {
        double wgsLat = 39.908823;
        double wgsLng = 116.397470;
        double[] gcj = CoordinateTransform.wgs84ToGcj02(wgsLat, wgsLng);
        double[] restored = CoordinateTransform.gcj02ToWgs84(gcj[0], gcj[1]);
        check(Math.abs(restored[0] - wgsLat) < 0.00002 && Math.abs(restored[1] - wgsLng) < 0.00002,
                "amap coordinate conversion");
    }

    private static void testReminderProviderRegistry() {
        ReminderProviderRegistry registry = new ReminderProviderRegistry();
        check(registry.add("gps") && registry.add("network"), "register providers");
        check(!registry.add("gps") && registry.count() == 2, "duplicate provider is idempotent");
        check(!registry.remove("passive") && registry.count() == 2, "unknown provider does not decrement");
        check(registry.remove("gps") && registry.count() == 1, "disable one provider");
        check(registry.remove("network") && registry.count() == 0, "disable last provider");
        registry.clear();
        check(registry.count() == 0, "clear providers");
    }

    private static void testRejectsBrokenSystemPlace() {
        check(!PlaceQuality.isRelevant("成都sm广场", "场", "成都市", "四川省成都市成华区场"),
                "reject broken system place");
    }

    private static void testAcceptsRelevantPlaceSuggestion() {
        check(PlaceQuality.isRelevant("成都sm广场", "成都SM广场", "成都市", "成华区二环路东二段"),
                "accept exact place");
        check(PlaceQuality.isRelevant("虹桥", "上海虹桥站", "上海市", "闵行区申贵路"),
                "accept partial place");
    }

    private static void testRecoversTruncatedSystemPlaceName() {
        check(PlaceQuality.shouldRecoverQueryName("成都sm广场", "场", "成都市", "四川省成都市成华区场"),
                "recover truncated system place name");
        check(!PlaceQuality.shouldRecoverQueryName("成都sm广场", "广场", "成都市", "成华区"),
                "do not recover normal place name");
    }

    private static void testRepeatedSuggestionStateRendersOnce() {
        InteractionGate gate = new InteractionGate();
        int renders = 0;
        boolean visible = false;
        for (int i = 0; i < 10_000; i++) {
            if (gate.shouldRender("message:地图正在智能联想…", visible, true)) {
                renders++;
                visible = true;
            }
        }
        check(renders == 1, "repeated suggestion state renders once");
    }

    private static void testSuggestionGateResets() {
        InteractionGate gate = new InteractionGate();
        check(gate.shouldRender("places:a|b|", false, true), "first suggestion render");
        check(!gate.shouldRender("places:a|b|", true, true), "same suggestion skipped");
        gate.reset();
        check(gate.shouldRender("places:a|b|", false, true), "suggestion render after reset");
    }

    private static void testFastSearchDebounce() {
        check(InteractionGate.searchDebounceMillis(false) == 120, "default search debounce");
        check(InteractionGate.searchDebounceMillis(true) == 80, "amap search debounce");
    }

    private static void testAsyncResultGate() {
        AsyncResultGate gate = new AsyncResultGate();
        long first = gate.begin();
        check(gate.isCurrent(first), "first async result is current");
        long second = gate.begin();
        check(!gate.isCurrent(first), "new async request invalidates the old result");
        check(gate.isCurrent(second), "new async result is current");
        gate.invalidate();
        check(!gate.isCurrent(second), "cancellation invalidates the active result");
    }

    private static void testDestinationConfirmationDetails() {
        String details = DestinationConfirmation.details("人民医院东院", "成都市", "测试路 18 号",
                30.123456, 104.123456, false);
        check(details.contains("人民医院东院\n城市/区域：成都市\n地址：测试路 18 号\n"), "complete destination details");

        String inferred = DestinationConfirmation.details("人民医院", "", "", 30.123456, 104.123456, true);
        check(inferred.contains("城市/区域：服务未提供") && inferred.contains("地址：服务未提供"),
                "missing city and address are explicit");
        check(inferred.contains("30.123456, 104.123456") && inferred.contains("名称由输入关键词恢复"),
                "inferred destination includes coordinates and source warning");
    }

    private static void testPlaceSearchPipelineSeparatesNoMatchAndFailure() {
        PlaceSearchPipeline.Result<String> noMatch = PlaceSearchPipeline.search(java.util.Arrays.asList(
                () -> java.util.Collections.emptyList()));
        check(noMatch.status == PlaceSearchPipeline.Status.NO_MATCH, "empty provider is no match");
        check(noMatch.cacheable(), "no match is cacheable");

        PlaceSearchPipeline.Result<String> unavailable = PlaceSearchPipeline.search(java.util.Arrays.asList(
                () -> { throw new PlaceSearchPipeline.ProviderException(PlaceSearchPipeline.Failure.RATE_LIMIT); }));
        check(unavailable.status == PlaceSearchPipeline.Status.UNAVAILABLE, "provider failure is unavailable");
        check(!unavailable.cacheable(), "provider failure is not cached as no match");
    }

    private static void testPlaceSearchPipelineClassifiesTimeout() {
        PlaceSearchPipeline.Result<String> result = PlaceSearchPipeline.search(java.util.Arrays.asList(
                () -> { throw new java.net.SocketTimeoutException("test"); }));
        check(result.status == PlaceSearchPipeline.Status.UNAVAILABLE, "timeout is unavailable");
        check(result.emptyMessage().contains("超时"), "timeout message is distinguishable");
    }

    private static void testPlaceInputLengthBound() {
        check(PlaceInputParser.parse(new String(new char[PlaceSearchLimits.MAX_INPUT_CHARS + 1]).replace('\0', 'a')) == null,
                "oversized external input rejected");
        check(PlaceSearchLimits.validQuery("上海站"), "normal query accepted");
        check(!PlaceSearchLimits.validQuery(new String(new char[PlaceSearchLimits.MAX_QUERY_CHARS + 1]).replace('\0', 'a')),
                "oversized search query rejected");
    }

    private static Calendar date(TimeZone zone, int year, int month, int day, int hour, int minute) {
        Calendar result = Calendar.getInstance(zone);
        result.set(year, month, day, hour, minute, 0);
        result.set(Calendar.MILLISECOND, 0);
        return result;
    }

    private static boolean close(double first, double second) { return Math.abs(first - second) < 0.000001; }

    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        passed++;
    }
}
