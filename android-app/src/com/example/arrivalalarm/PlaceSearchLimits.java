package com.example.arrivalalarm;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Local resource limits, not provider or paper specifications. */
public final class PlaceSearchLimits {
    public static final int MAX_INPUT_CHARS = 2048;
    public static final int MAX_QUERY_CHARS = 200;
    public static final int MAX_RESPONSE_BYTES = 256 * 1024;

    private PlaceSearchLimits() { }

    public static boolean inputTooLong(CharSequence input) {
        return input != null && input.length() > MAX_INPUT_CHARS;
    }

    public static boolean validQuery(String query) {
        return query != null && !inputTooLong(query) && !query.trim().isEmpty()
                && query.trim().length() <= MAX_QUERY_CHARS;
    }

    public static String readUtf8(InputStream stream) throws IOException {
        // Read at most the cap + one byte; even a single huge JSON line stays bounded.
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[4096];
            int count;
            while (true) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new PlaceSearchPipeline.ProviderException(PlaceSearchPipeline.Failure.CANCELLED);
                }
                count = input.read(chunk, 0, Math.min(chunk.length, MAX_RESPONSE_BYTES - output.size() + 1));
                if (count == -1) return new String(output.toByteArray(), StandardCharsets.UTF_8);
                if (count > MAX_RESPONSE_BYTES - output.size()) {
                    throw new PlaceSearchPipeline.ProviderException(PlaceSearchPipeline.Failure.INVALID_RESPONSE);
                }
                output.write(chunk, 0, count);
            }
        }
    }
}
