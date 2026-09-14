package vn.dnse.openapi.websocket.exception;

/** Raised for low-level WebSocket connection/send failures. */
public class DnseConnectionException extends DnseWebSocketException {
    /** Creates a connection error without a nested cause. */
    public DnseConnectionException(String message) { super(message); }
    /** Creates a connection error preserving the underlying I/O/transport failure. */
    public DnseConnectionException(String message, Throwable cause) { super(message, cause); }
}
