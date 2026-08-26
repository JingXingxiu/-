package com.bookdecision.application.userdataset;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Per-process fixed-window protection for the anonymous upload endpoint.
 *
 * <p>The key comes from {@code HttpServletRequest#getRemoteAddr()}, not from an
 * untrusted forwarding header. A public reverse proxy must therefore pass the
 * real peer address to the servlet container using its trusted-proxy support.</p>
 */
@Component
@ConditionalOnProperty(prefix = "book-decision.user-dataset", name = "enabled", havingValue = "true")
public final class UserDatasetUploadRateLimiter {

    private static final int CLEANUP_INTERVAL = 256;
    static final int MAX_TRACKED_CLIENTS = 10_000;

    private final int maxUploadsPerWindow;
    private final long windowMillis;
    private final int maxTrackedClients;
    private final Clock clock;
    private final Map<String, WindowCounter> counters = new HashMap<>();
    private long attempts;

    @Autowired
    public UserDatasetUploadRateLimiter(UserDatasetProperties properties, Clock clock) {
        this(properties, clock, MAX_TRACKED_CLIENTS);
    }

    UserDatasetUploadRateLimiter(
            UserDatasetProperties properties,
            Clock clock,
            int maxTrackedClients
    ) {
        Objects.requireNonNull(properties, "properties must not be null");
        UserDatasetProperties.UploadRateLimit config = properties.uploadRateLimit();
        this.maxUploadsPerWindow = config.maxUploadsPerWindow();
        this.windowMillis = Math.multiplyExact(config.windowSeconds(), 1_000L);
        if (maxTrackedClients < 1) {
            throw new IllegalArgumentException("maxTrackedClients must be positive");
        }
        this.maxTrackedClients = maxTrackedClients;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public synchronized void acquire(String remoteAddress) {
        long now = clock.millis();
        long windowStart = Math.floorDiv(now, windowMillis) * windowMillis;
        String clientKey = normalize(remoteAddress);
        attempts++;
        if (attempts % CLEANUP_INTERVAL == 0 || counters.size() >= maxTrackedClients) {
            counters.entrySet().removeIf(entry -> entry.getValue().windowStartMillis() < windowStart);
        }

        WindowCounter active = counters.get(clientKey);
        if (active == null && counters.size() >= maxTrackedClients) {
            throw rejected(now, windowStart, "匿名 CSV 上传来源过多，请稍后再试");
        }
        if (active == null || active.windowStartMillis() != windowStart) {
            active = new WindowCounter(windowStart, 0);
        }
        if (active.acceptedUploads() >= maxUploadsPerWindow) {
            throw rejected(now, windowStart, "匿名 CSV 上传过于频繁，请稍后再试");
        }
        counters.put(clientKey, new WindowCounter(windowStart, active.acceptedUploads() + 1));
    }

    private UserDatasetException rejected(long now, long windowStart, String message) {
        long remainingMillis = Math.max(1L, windowStart + windowMillis - now);
        long retryAfterSeconds = Math.max(1L, (remainingMillis + 999L) / 1_000L);
        return new UserDatasetException(
                UserDatasetErrorCode.UPLOAD_RATE_LIMIT_EXCEEDED,
                message,
                java.util.List.of(),
                Map.of(
                        "retryAfterSeconds", retryAfterSeconds,
                        "maxUploadsPerWindow", maxUploadsPerWindow,
                        "windowSeconds", windowMillis / 1_000L
                )
        );
    }

    private static String normalize(String remoteAddress) {
        if (remoteAddress == null || remoteAddress.isBlank()) {
            return "unknown";
        }
        String normalized = remoteAddress.strip().toLowerCase(Locale.ROOT);
        return normalized.length() <= 128 ? normalized : normalized.substring(0, 128);
    }

    private record WindowCounter(long windowStartMillis, int acceptedUploads) {
    }
}
