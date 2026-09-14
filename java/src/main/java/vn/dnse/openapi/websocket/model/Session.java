package vn.dnse.openapi.websocket.model;

public record Session(
        String marketId, String boardId, String eventId,
        int tradingSessionId, String tscProdGrpId,
        String time, long receivedAtEpochMillis
) {}
