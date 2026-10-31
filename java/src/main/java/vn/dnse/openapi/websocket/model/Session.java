package vn.dnse.openapi.websocket.model;

/**
 * Exchange trading-session status event.
 *
 * @param marketId market identifier
 * @param boardId trading board identifier
 * @param eventId event code indicating a session-state transition
 * @param tradingSessionId current trading-session identifier
 * @param tscProdGrpId product-group identifier such as STO, STX, UPX or FIO
 * @param time event timestamp; the current mapper follows the Python SDK's {@code sendingTime} source field
 * @param receivedAtEpochMillis local SDK receipt time
 */
public record Session(
        String marketId, String boardId, String eventId,
        int tradingSessionId, String tscProdGrpId,
        String time, long receivedAtEpochMillis
) {}
