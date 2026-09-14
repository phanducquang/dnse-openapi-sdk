package vn.dnse.openapi.websocket.exception;

public class DnseWebSocketException extends RuntimeException {
    public DnseWebSocketException(String message) { super(message); }
    public DnseWebSocketException(String message, Throwable cause) { super(message, cause); }
}
