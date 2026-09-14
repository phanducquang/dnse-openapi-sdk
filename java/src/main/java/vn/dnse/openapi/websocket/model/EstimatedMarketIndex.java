package vn.dnse.openapi.websocket.model;

import java.math.BigDecimal;

public record EstimatedMarketIndex(
        String indexName,
        BigDecimal changedRatio, BigDecimal changedValue,
        BigDecimal fluctuationSteadinessIssueCount,
        BigDecimal fluctuationDownIssueCount,
        BigDecimal fluctuationUpIssueCount,
        BigDecimal valueIndexes, BigDecimal grossTradeAmount,
        BigDecimal totalVolumeTraded, String time,
        long receivedAtEpochMillis
) {}
