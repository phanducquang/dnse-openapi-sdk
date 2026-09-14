package vn.dnse.openapi.websocket.model;

import java.math.BigDecimal;

public record Ohlc(
        String symbol,
        String resolution,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume,
        long time,
        long lastUpdated,
        String type,
        long receivedAtEpochMillis
) {}
