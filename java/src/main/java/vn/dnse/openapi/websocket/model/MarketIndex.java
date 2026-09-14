package vn.dnse.openapi.websocket.model;

import java.math.BigDecimal;

public record MarketIndex(
        String indexName,
        BigDecimal changedRatio, BigDecimal changedValue,
        long fluctuationSteadinessIssueCount, long fluctuationDownIssueCount,
        long fluctuationUpIssueCount, long fluctuationLowerLimitIssueCount,
        long fluctuationUpperLimitIssueCount, long fluctuationDownIssueVolume,
        long fluctuationUpIssueVolume, long fluctuationSteadinessIssueVolume,
        String currencyCode, String indexTypeCode,
        BigDecimal lowestValueIndexes, BigDecimal highestValueIndexes,
        BigDecimal priorValueIndexes, BigDecimal valueIndexes,
        BigDecimal contauctAccTrdVal, long contauctAccTrdVol,
        BigDecimal blkTrdAccTrdVal, long blkTrdAccTrdVol,
        BigDecimal grossTradeAmount, long totalVolumeTraded,
        int marketIndexClass, int marketId, int tradingSessionId,
        String transactTime, long receivedAtEpochMillis
) {}
