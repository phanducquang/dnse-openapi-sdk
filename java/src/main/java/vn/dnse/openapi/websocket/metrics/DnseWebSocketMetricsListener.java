package vn.dnse.openapi.websocket.metrics;

import vn.dnse.openapi.websocket.dispatcher.BackpressureEvent;

/**
 * Framework-neutral observability hook for integrating the SDK with Micrometer, OpenTelemetry or custom metrics.
 * Implementations must return quickly because callbacks may run on WebSocket or dispatcher threads.
 */
public interface DnseWebSocketMetricsListener {
    default void onMessageReceived(String eventName) {}

    default void onMessageDispatched(String eventName) {}

    default void onReconnect(int attempt) {}

    default void onSubscriptionAdded(String channel, int symbolCount) {}

    default void onSubscriptionRemoved(String channel, int symbolCount) {}

    default void onBackpressure(BackpressureEvent event) {}
}
