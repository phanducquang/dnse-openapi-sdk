package vn.dnse.openapi.websocket.exception;

/** Raised when DNSE rejects authentication or returns an unexpected auth response. */
public class DnseAuthenticationException extends DnseWebSocketException {
    /** Creates an authentication error with the server/protocol description. */
    public DnseAuthenticationException(String message) { super(message); }
}
