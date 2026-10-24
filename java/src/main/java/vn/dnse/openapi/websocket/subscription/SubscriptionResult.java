package vn.dnse.openapi.websocket.subscription;

import java.time.Instant;

/** Result of one asynchronous subscription send. */
public record SubscriptionResult(
        Subscription subscription,
        SubscriptionConfirmation confirmation,
        Instant completedAt
) {
}
