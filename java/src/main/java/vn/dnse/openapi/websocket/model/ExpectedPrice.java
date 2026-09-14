package vn.dnse.openapi.websocket.model;

import java.math.BigDecimal;

public record ExpectedPrice(
        String marketId, String boardId, String isin, String symbol,
        BigDecimal closePrice, BigDecimal expectedTradePrice,
        long expectedTradeQuantity, String time, long receivedAtEpochMillis
) {}
