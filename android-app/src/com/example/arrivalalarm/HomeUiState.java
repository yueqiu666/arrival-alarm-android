package com.example.arrivalalarm;

import android.os.Bundle;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Small, transient state owned by the current home screen. */
final class HomeUiState {
    enum DialogKind { CHOICES, CONFIRM, REPLACE, PERMISSIONS }

    static final class Dialog {
        final DialogKind kind;
        final String title;
        final String message;
        final AppPrefs.Destination place;
        final List<AppPrefs.Destination> choices;

        Dialog(DialogKind kind, String title, String message, AppPrefs.Destination place, List<AppPrefs.Destination> choices) {
            this.kind = kind;
            this.title = title;
            this.message = message;
            this.place = place;
            this.choices = choices;
        }

        Bundle save() {
            Bundle state = new Bundle();
            state.putString("kind", kind.name());
            state.putString("title", title);
            state.putString("message", message);
            putPlace(state, "place", place);
            if (choices != null) {
                ArrayList<String> encoded = new ArrayList<>();
                for (AppPrefs.Destination candidate : choices) encoded.add(candidate.toJson().toString());
                state.putStringArrayList("choices", encoded);
            }
            return state;
        }

        static Dialog restore(Bundle state) {
            if (state == null) return null;
            List<AppPrefs.Destination> choices = new ArrayList<>();
            ArrayList<String> encoded = state.getStringArrayList("choices");
            if (encoded != null) {
                for (String value : encoded) {
                    AppPrefs.Destination place = decodePlace(value);
                    if (place != null) choices.add(place);
                }
            }
            DialogKind kind;
            try { kind = DialogKind.valueOf(state.getString("kind", "")); }
            catch (IllegalArgumentException unsupported) { return null; }
            AppPrefs.Destination place = getPlace(state, "place");
            if ((kind == DialogKind.CONFIRM || kind == DialogKind.REPLACE) && place == null) return null;
            if (kind == DialogKind.CHOICES && choices.isEmpty()) return null;
            return new Dialog(kind, state.getString("title"),
                    state.getString("message"), place, choices);
        }
    }

    private HomeUiState() { }

    static void putPlace(Bundle state, String key, AppPrefs.Destination place) {
        if (place != null) state.putString(key, place.toJson().toString());
    }

    static AppPrefs.Destination getPlace(Bundle state, String key) {
        return decodePlace(state.getString(key));
    }

    private static AppPrefs.Destination decodePlace(String value) {
        if (value == null) return null;
        try { return AppPrefs.Destination.fromJson(new JSONObject(value)); }
        catch (JSONException malformed) { return null; }
    }

    static Bundle saveShare(ShareParser.ParsedShare share) {
        Bundle state = new Bundle();
        state.putString("name", share.name);
        state.putDouble("lat", share.latitude);
        state.putDouble("lng", share.longitude);
        state.putString("source", share.source);
        state.putBoolean("verified", share.coordinateSystemVerified);
        return state;
    }

    static ShareParser.ParsedShare restoreShare(Bundle state) {
        return state == null ? null : new ShareParser.ParsedShare(state.getString("name"),
                state.getDouble("lat"), state.getDouble("lng"), state.getString("source"), state.getBoolean("verified"));
    }
}
