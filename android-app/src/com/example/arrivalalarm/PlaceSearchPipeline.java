package com.example.arrivalalarm;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Sequential fallback that preserves uncertainty when a provider fails. */
public final class PlaceSearchPipeline {
    public enum Failure { NETWORK, TIMEOUT, RATE_LIMIT, CONFIGURATION, INVALID_RESPONSE, SERVICE, CANCELLED }
    public enum Status { SUCCESS, NO_MATCH, UNAVAILABLE, CANCELLED }

    public interface Provider<T> { List<T> search() throws IOException; }

    public static final class ProviderException extends IOException {
        public final Failure failure;

        public ProviderException(Failure failure) {
            super(failure.name()); // Never include provider URLs, keys or user input.
            this.failure = failure;
        }
    }

    public static final class Result<T> {
        public final Status status;
        public final List<T> places;
        public final List<Failure> failures;

        private Result(Status status, List<T> places, List<Failure> failures) {
            this.status = status;
            this.places = Collections.unmodifiableList(new ArrayList<>(places));
            this.failures = Collections.unmodifiableList(new ArrayList<>(failures));
        }

        public boolean cacheable() { return status == Status.SUCCESS || status == Status.NO_MATCH; }

        public String emptyMessage() {
            if (status == Status.NO_MATCH) return "没有找到匹配地点，试试“城市 + 地点名”";
            if (status == Status.CANCELLED) return "地点搜索已取消";
            // A fallback may also have failed for another reason; do not imply a complete diagnosis.
            if (failures.contains(Failure.CONFIGURATION)) return "地点服务配置或额度异常，请稍后重试";
            if (failures.contains(Failure.RATE_LIMIT)) return "地点服务请求受限，请稍后重试";
            if (failures.contains(Failure.TIMEOUT)) return "地点搜索超时，请检查网络后重试";
            return "地点服务暂时不可用，请检查网络或稍后重试";
        }
    }

    private PlaceSearchPipeline() { }

    public static <T> Result<T> search(List<Provider<T>> providers) {
        List<Failure> failures = new ArrayList<>();
        for (Provider<T> provider : providers) {
            if (Thread.currentThread().isInterrupted()) return cancelled(failures);
            try {
                List<T> places = provider.search();
                if (Thread.currentThread().isInterrupted()) return cancelled(failures);
                if (places == null) throw new ProviderException(Failure.INVALID_RESPONSE);
                if (!places.isEmpty()) return new Result<>(Status.SUCCESS, places, failures);
            } catch (IOException error) {
                Failure failure = classify(error);
                if (failure == Failure.CANCELLED || Thread.currentThread().isInterrupted()) return cancelled(failures);
                failures.add(failure);
            }
        }
        return new Result<>(!providers.isEmpty() && failures.isEmpty() ? Status.NO_MATCH : Status.UNAVAILABLE,
                Collections.emptyList(), failures);
    }

    private static <T> Result<T> cancelled(List<Failure> failures) {
        failures.add(Failure.CANCELLED);
        return new Result<>(Status.CANCELLED, Collections.emptyList(), failures);
    }

    private static Failure classify(IOException error) {
        if (error instanceof ProviderException) return ((ProviderException) error).failure;
        if (error instanceof SocketTimeoutException) return Failure.TIMEOUT;
        if (error instanceof InterruptedIOException) return Failure.CANCELLED;
        return Failure.NETWORK;
    }

    public static void checkHttpStatus(int status) throws IOException {
        if (status >= 200 && status < 300) return;
        Failure failure = status == 429 ? Failure.RATE_LIMIT
                : status == 401 || status == 403 ? Failure.CONFIGURATION : Failure.SERVICE;
        throw new ProviderException(failure);
    }
}
