package vn.dnse.openapi.websocket.model;

import java.math.BigDecimal;

public record TradeExtra(
        String marketId, String boardId, String isin, String symbol,
        BigDecimal price, long quantity, int side, BigDecimal avgPrice,
        long totalVolumeTraded, BigDecimal grossTradeAmount,
        BigDecimal highestPrice, BigDecimal lowestPrice, BigDecimal openPrice,
        int tradingSessionId, String time, long receivedAtEpochMillis
) {}
