package vn.dnse.openapi.websocket;

/**
 * Controls how {@link DnseWebSocketClient#connect()} behaves when the initial gateway connection
 * cannot be established.
 */
public enum InitialConnectionPolicy {
    /** Fail the returned connect future immediately. No background reconnect is started. */
    FAIL_FAST,

    /** Retry the initial connection using {@link ReconnectPolicy} retry count and backoff delays. */
    RETRY
}
