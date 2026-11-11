package com.example.arrivalalarm;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;

/** Framework-level device checks without AndroidX. */
public final class LifecycleInstrumentationRunner extends Instrumentation {
    private Bundle gateArguments;

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        gateArguments = arguments;
        start();
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        try {
            if (gateArguments != null && "true".equals(gateArguments.getString("amapLiveSearch"))) {
                checkAmapLiveSearch(result);
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (gateArguments != null && gateArguments.containsKey("api35ReminderGate")) {
                Api35ReminderGate.run(this, gateArguments, result);
                finish(Activity.RESULT_OK, result);
                return;
            }
            NotificationPrivacyChecks.run(getTargetContext());
            result.putString("notification_privacy", "PASS");
            Intent intent = new Intent()
                    .setComponent(new ComponentName(
                            getTargetContext().getPackageName(),
                            "com.example.arrivalalarm.MainActivity"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getTargetContext().startActivity(intent);
            Thread.sleep(500L);
            result.putString("lifecycle", "MainActivity start request completed");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("error", failure.toString());
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void checkAmapLiveSearch(Bundle result) {
        if (!BuildConfig.DEBUG || !AmapAndroidPlaceSearch.isAvailable()) {
            throw new IllegalStateException("Live Amap check requires a Debug APK with its test key");
        }
        PlaceSearchRepository repository = new PlaceSearchRepository(getTargetContext());
        String query = "成都市+天府广场";
        for (boolean suggestions : new boolean[]{true, false}) {
            String mode = suggestions ? "amap_suggestions" : "amap_poi";
            PlaceSearchPipeline.Result<AppPrefs.Destination> outcome = repository.query(query, 7, suggestions, true);
            boolean matched = outcome.places.stream().anyMatch(place ->
                    place.name.contains("天府广场") && place.latitude > 30.5 && place.latitude < 30.8
                            && place.longitude > 103.9 && place.longitude < 104.2);
            if (!matched) throw new AssertionError(mode + " did not return the fixture: " + outcome.status + " " + outcome.failures);
            result.putString(mode, "PASS");
        }
    }
}
