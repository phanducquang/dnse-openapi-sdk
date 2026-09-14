package vn.dnse.openapi.websocket.exception;

/** Base unchecked exception for errors raised by the DNSE WebSocket SDK. */
public class DnseWebSocketException extends RuntimeException {
    /** Creates an SDK exception with a human-readable message. */
    public DnseWebSocketException(String message) { super(message); }
    /** Creates an SDK exception preserving the original cause. */
    public DnseWebSocketException(String message, Throwable cause) { super(message, cause); }
}
