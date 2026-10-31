package vn.dnse.openapi.websocket.dispatcher;

import java.time.Duration;

/**
 * Raised when an event-dispatch stripe is full and the producer has to block.
 *
 * @param workerIndex zero-based stripe index
 * @param queueSize queue size observed when backpressure started
 * @param queueCapacity configured capacity of the stripe
 * @param blockedFor time spent waiting for queue capacity
 */
public record BackpressureEvent(
        int workerIndex,
        int queueSize,
        int queueCapacity,
        Duration blockedFor
) {
}
