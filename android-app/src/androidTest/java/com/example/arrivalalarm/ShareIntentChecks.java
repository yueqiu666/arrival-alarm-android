package com.example.arrivalalarm;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

final class ShareIntentChecks {
    private ShareIntentChecks() { }

    static void run(Context context) {
        Uri opaque = Uri.parse("arrivalalarm:set?name=Test&lat=31.25&lng=121.45");
        check(!opaque.isHierarchical(), "fixture must be opaque");
        Intent explicitView = new Intent(Intent.ACTION_VIEW, opaque).setClass(context, MainActivity.class);
        check(ShareParser.fromIntent(explicitView) == null, "opaque app link must be rejected");
        check(ShareParser.fromIntent(view("arrivalalarm:/set?lat=31.25&lng=121.45")) == null,
                "app link without host must be rejected");
        check(ShareParser.fromIntent(view("arrivalalarm://other?lat=31.25&lng=121.45")) == null,
                "unknown app link host must be rejected");

        ShareParser.ParsedShare deepLink = requireDestination(
                view("arrivalalarm://set?name=Deep%20link&lat=31.25&lng=121.45"), "Deep link");
        check(deepLink.coordinateSystemVerified, "app link coordinates must be marked WGS-84");
        ShareParser.ParsedShare nameOnly = ShareParser.fromIntent(view("arrivalalarm://set?name=Station"));
        check(nameOnly != null && !nameOnly.hasCoordinates() && "Station".equals(nameOnly.name),
                "name-only app link must still be accepted for search");

        requireDestination(new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "Shared place 31.25,121.45"), "Shared place");
        requireDestination(new Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
                .putExtra(Intent.EXTRA_PROCESS_TEXT, (CharSequence) "Selected place 31.25,121.45"), "Selected place");
        requireDestination(view("geo:0,0?q=31.25,121.45(Geo%20place)"), "Geo place");
    }

    private static Intent view(String uri) {
        return new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
    }

    private static ShareParser.ParsedShare requireDestination(Intent intent, String name) {
        ShareParser.ParsedShare result = ShareParser.fromIntent(intent);
        check(result != null && result.hasCoordinates() && name.equals(result.name)
                        && result.latitude == 31.25 && result.longitude == 121.45,
                "unexpected parsed destination for " + name);
        return result;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
