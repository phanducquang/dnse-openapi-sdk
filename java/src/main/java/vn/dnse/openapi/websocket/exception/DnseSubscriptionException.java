package vn.dnse.openapi.websocket.exception;

/** Raised when a subscription operation is invalid for the current client state. */
public class DnseSubscriptionException extends DnseWebSocketException {
    /** Creates a subscription error. */
    public DnseSubscriptionException(String message) { super(message); }
}
