package vn.dnse.openapi.websocket.exception;

/** Raised when JSON or MessagePack payload serialization/deserialization fails. */
public class DnseEncodingException extends DnseWebSocketException {
    /** Creates an encoding error preserving the codec/Jackson cause. */
    public DnseEncodingException(String message, Throwable cause) { super(message, cause); }
}
