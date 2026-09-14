package vn.dnse.openapi.websocket.model;

import java.math.BigDecimal;

public record Trade(
        String marketId,
        String boardId,
        String isin,
        String symbol,
        BigDecimal price,
        long quantity,
        long totalVolumeTraded,
        BigDecimal grossTradeAmount,
        BigDecimal highestPrice,
        BigDecimal lowestPrice,
        BigDecimal openPrice,
        int tradingSessionId,
        String time,
        long receivedAtEpochMillis
) {}
