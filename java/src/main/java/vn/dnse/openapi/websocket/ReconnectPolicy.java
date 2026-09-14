package vn.dnse.openapi.websocket;

import java.time.Duration;

/**
 * Controls automatic reconnect after an abnormal WebSocket disconnection.
 *
 * <p>The default policy mirrors the Python SDK: exponential backoff beginning at one second,
 * capped at sixty seconds, with at most ten retries.</p>
 *
 * @param enabled whether recoverable disconnects should trigger automatic reconnect
 * @param maxRetries maximum number of reconnect attempts before the client remains disconnected
 * @param initialDelay delay before the first reconnect attempt
 * @param maxDelay upper bound for the exponential backoff delay
 */
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

    /**
     * Creates the reconnect behavior used by the Python SDK.
     *
     * @return enabled policy with 10 retries and 1s..60s exponential backoff
     */
    public static ReconnectPolicy defaults() {
        return new ReconnectPolicy(true, 10, Duration.ofSeconds(1), Duration.ofSeconds(60));
    }

    /**
     * Calculates the backoff for a one-based reconnect attempt.
     *
     * @param attempt one-based attempt number
     * @return exponentially increasing delay capped by {@link #maxDelay()}
     */
    public Duration delayForAttempt(int attempt) {
        if (attempt <= 1) return initialDelay;
        long multiplier = 1L << Math.min(attempt - 1, 30);
        Duration candidate = initialDelay.multipliedBy(multiplier);
        return candidate.compareTo(maxDelay) > 0 ? maxDelay : candidate;
    }
}
