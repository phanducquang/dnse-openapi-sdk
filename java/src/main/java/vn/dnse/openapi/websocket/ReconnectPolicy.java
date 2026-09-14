package vn.dnse.openapi.websocket;

import java.time.Duration;

public record ReconnectPolicy(
        boolean enabled,
        int maxRetries,
        Duration initialDelay,
        Duration maxDelay
) {
    public ReconnectPolicy {
        if (maxRetries < 0) throw new IllegalArgumentException("maxRetries must be >= 0");
        if (initialDelay.isNegative() || initialDelay.isZero()) throw new IllegalArgumentException("initialDelay must be > 0");
        if (maxDelay.compareTo(initialDelay) < 0) throw new IllegalArgumentException("maxDelay must be >= initialDelay");
    }

    public static ReconnectPolicy defaults() {
        return new ReconnectPolicy(true, 10, Duration.ofSeconds(1), Duration.ofSeconds(60));
    }

    public Duration delayForAttempt(int attempt) {
        if (attempt <= 1) return initialDelay;
        long multiplier = 1L << Math.min(attempt - 1, 30);
        Duration candidate = initialDelay.multipliedBy(multiplier);
        return candidate.compareTo(maxDelay) > 0 ? maxDelay : candidate;
    }
}
