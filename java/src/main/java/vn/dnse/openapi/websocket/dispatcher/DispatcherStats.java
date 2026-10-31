package vn.dnse.openapi.websocket.dispatcher;

import java.time.Duration;

/** Aggregate snapshot of the striped callback dispatcher. */
public record DispatcherStats(
        int workerCount,
        long queuedEvents,
        long totalQueueCapacity,
        int activeWorkers,
        int maxQueueSize,
        double utilization,
        long blockedSubmissions,
        Duration totalBlockedTime
) {
}
