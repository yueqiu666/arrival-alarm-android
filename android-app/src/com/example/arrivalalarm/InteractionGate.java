package com.example.arrivalalarm;

/** Small, allocation-light gate that prevents identical UI states from being rebuilt. */
final class InteractionGate {
    private String lastKey = "";

    boolean shouldRender(String nextKey, boolean currentlyVisible, boolean shouldBeVisible) {
        if (nextKey.equals(lastKey) && currentlyVisible == shouldBeVisible) return false;
        lastKey = nextKey;
        return true;
    }

    void reset() { lastKey = ""; }

    static int searchDebounceMillis(boolean amapEnabled) { return amapEnabled ? 80 : 120; }
}
