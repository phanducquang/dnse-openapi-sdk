package vn.dnse.openapi.websocket;

/**
 * Lifecycle state of a DNSE WebSocket client connection.
 *
 * <p>The state is intentionally more detailed than the underlying TCP/WebSocket state so callers
 * can distinguish a socket that is open from a session that has already completed DNSE authentication.</p>
 */
public enum ConnectionState {
    /** No active socket is available. */
    DISCONNECTED,
    /** A WebSocket handshake is currently being established. */
    CONNECTING,
    /** The WebSocket is open and the client is waiting for/processing the DNSE welcome message. */
    CONNECTED,
    /** The welcome message was received and the HMAC authentication request has been sent. */
    AUTHENTICATING,
    /** Authentication succeeded and subscriptions may be created. */
    AUTHENTICATED,
    /** A recoverable disconnect occurred and an automatic reconnect is pending/in progress. */
    RECONNECTING,
    /** The client is performing a graceful close handshake. */
    CLOSING,
    /** The client was intentionally closed and its resources have been released. */
    CLOSED
}
