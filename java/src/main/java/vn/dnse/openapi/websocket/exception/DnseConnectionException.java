package vn.dnse.openapi.websocket.exception;

public class DnseConnectionException extends DnseWebSocketException {
    public DnseConnectionException(String message) { super(message); }
    public DnseConnectionException(String message, Throwable cause) { super(message, cause); }
}
