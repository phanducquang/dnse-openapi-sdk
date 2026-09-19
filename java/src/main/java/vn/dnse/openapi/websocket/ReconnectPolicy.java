package vn.dnse.openapi.websocket;

import java.time.Duration;

/**
 * Controls automatic reconnect after an abnormal WebSocket disconnection.
 *
 * <p>The default policy mirrors the Python SDK: exponential backoff beginning at one second,
 * capped at sixty seconds, with at most ten retries. Long-running services can explicitly opt
 * into unlimited retries through {@link #forever(Duration, Duration)}.</p>
 *
 * @param enabled whether recoverable disconnects should trigger automatic reconnect
 * @param maxRetries maximum number of reconnect attempts before the client remains disconnected;
 *                   {@link #UNLIMITED_RETRIES} means retry until the client is explicitly closed
 * @param initialDelay delay before the first reconnect attempt
 * @param maxDelay upper bound for the exponential backoff delay
 */
public record ReconnectPolicy(
        boolean enabled,
        int maxRetries,
        Duration initialDelay,
        Duration maxDelay
) {
    public static final int UNLIMITED_RETRIES = -1;

    public ReconnectPolicy {
        if (maxRetries < UNLIMITED_RETRIES) {
            throw new IllegalArgumentException("maxRetries must be >= 0 or UNLIMITED_RETRIES (-1)");
        }
        if (initialDelay == null || initialDelay.isNegative() || initialDelay.isZero()) {
            throw new IllegalArgumentException("initialDelay must be > 0");
        }
        if (maxDelay == null || maxDelay.compareTo(initialDelay) < 0) {
            throw new IllegalArgumentException("maxDelay must be >= initialDelay");
        }
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
     * Creates an unlimited reconnect policy for long-running ingestion services.
     *
     * @param initialDelay first retry delay
     * @param maxDelay maximum exponential-backoff delay
     * @return enabled policy that keeps retrying until the client is explicitly closed
     */
    public static ReconnectPolicy forever(Duration initialDelay, Duration maxDelay) {
        return new ReconnectPolicy(true, UNLIMITED_RETRIES, initialDelay, maxDelay);
    }

    public boolean unlimited() {
        return maxRetries == UNLIMITED_RETRIES;
    }

    /**
     * Returns whether another retry is allowed after {@code retriesUsed} failed retries.
     *
     * @param retriesUsed number of retries already consumed
     */
    public boolean canRetry(int retriesUsed) {
        return enabled && (unlimited() || retriesUsed < maxRetries);
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
