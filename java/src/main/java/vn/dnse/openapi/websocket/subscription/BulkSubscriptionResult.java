package vn.dnse.openapi.websocket.subscription;

import java.util.List;

/** Summary returned after a bulk subscription operation has been accepted by the transport. */
public record BulkSubscriptionResult(
        int requestedSymbols,
        int subscribedSymbols,
        int subscriptionCount,
        List<Subscription> subscriptions
) {
    public BulkSubscriptionResult {
        subscriptions = List.copyOf(subscriptions);
    }
}
