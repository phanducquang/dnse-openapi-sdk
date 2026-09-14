package vn.dnse.openapi.websocket;

public enum ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    AUTHENTICATING,
    AUTHENTICATED,
    RECONNECTING,
    CLOSING,
    CLOSED
}
