package com.example.arrivalalarm;

import java.util.HashSet;
import java.util.Set;

/** Tracks the providers for which this service currently owns a listener. */
final class ReminderProviderRegistry {
    private final Set<String> providers = new HashSet<>();

    boolean add(String provider) {
        return provider != null && providers.add(provider);
    }

    boolean remove(String provider) {
        return provider != null && providers.remove(provider);
    }

    void clear() {
        providers.clear();
    }

    int count() {
        return providers.size();
    }
}
