package vn.dnse.openapi.websocket;

import java.time.Instant;

/**
 * Immutable connection lifecycle transition emitted by {@link DnseWebSocketClient}.
 *
 * @param previous previous high-level state
 * @param current new high-level state
 * @param sessionId latest DNSE session id, or {@code null} before the welcome message
 * @param timestamp local transition time
 * @param cause optional error that caused the transition
 */
public record ConnectionStateEvent(
        ConnectionState previous,
        ConnectionState current,
        String sessionId,
        Instant timestamp,
        Throwable cause
) {
}
