package vn.dnse.openapi.websocket.model;

import java.math.BigDecimal;

/**
 * Market-index snapshot/update including value, change, breadth and liquidity statistics.
 *
 * @param indexName index name such as VNINDEX, VN30, HNX or UPCOM
 * @param changedRatio percentage change versus reference
 * @param changedValue absolute index change versus reference
 * @param fluctuationSteadinessIssueCount number of unchanged constituents
 * @param fluctuationDownIssueCount number of declining constituents
 * @param fluctuationUpIssueCount number of advancing constituents
 * @param fluctuationLowerLimitIssueCount number of floor-limit constituents
 * @param fluctuationUpperLimitIssueCount number of ceiling-limit constituents
 * @param fluctuationDownIssueVolume traded volume of declining constituents
 * @param fluctuationUpIssueVolume traded volume of advancing constituents
 * @param fluctuationSteadinessIssueVolume traded volume of unchanged constituents
 * @param currencyCode currency code, typically VND
 * @param indexTypeCode exchange index-type code
 * @param lowestValueIndexes session low index value
 * @param highestValueIndexes session high index value
 * @param priorValueIndexes reference/prior index value
 * @param valueIndexes current index value
 * @param contauctAccTrdVal accumulated matched-order trading value (field spelling follows upstream payload/Python SDK)
 * @param contauctAccTrdVol accumulated matched-order trading volume
 * @param blkTrdAccTrdVal accumulated negotiated/block-trade value
 * @param blkTrdAccTrdVol accumulated negotiated/block-trade volume
 * @param grossTradeAmount total traded value for the day
 * @param totalVolumeTraded total traded volume for the day
 * @param marketIndexClass index classification code
 * @param marketId market identifier as represented by the current Python-compatible model; current DNSE docs show textual examples
 * @param tradingSessionId current trading-session identifier
 * @param transactTime exchange transaction/update timestamp
 * @param receivedAtEpochMillis local SDK receipt time
 */
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
