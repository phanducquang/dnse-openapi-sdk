package vn.dnse.openapi.websocket.subscription;

/**
 * Confirmation level for a subscription operation.
 *
 * <p>The current DNSE Python SDK does not consume a subscribe ACK/request id, so the Java SDK can
 * only confirm that the message was accepted by the WebSocket transport and recorded locally.</p>
 */
public enum SubscriptionConfirmation {
    TRANSPORT_ACCEPTED
}
