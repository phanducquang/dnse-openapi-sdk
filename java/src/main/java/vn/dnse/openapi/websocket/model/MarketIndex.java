package vn.dnse.openapi.websocket.model;

/**
 * Market-index snapshot/update including value, change, breadth and liquidity statistics.
 *
 * @param indexName index name such as VNINDEX, VN30, HNX or UPCOM
 * @param changedRatio percentage change versus reference
 * @param changedValue absolute index change versus reference
 * @param fluctuationSteadinessIssueCount number of unchanged constituents (can be null)
 * @param fluctuationDownIssueCount number of declining constituents (can be null)
 * @param fluctuationUpIssueCount number of advancing constituents (can be null)
 * @param fluctuationLowerLimitIssueCount number of floor-limit constituents (can be null)
 * @param fluctuationUpperLimitIssueCount number of ceiling-limit constituents (can be null)
 * @param fluctuationDownIssueVolume traded volume of declining constituents
 * @param fluctuationUpIssueVolume traded volume of advancing constituents
 * @param fluctuationSteadinessIssueVolume traded volume of unchanged constituents
 * @param currencyCode currency code, typically VND
 * @param indexTypeCode exchange index-type code
 * @param lowestValueIndexes session low index value
 * @param highestValueIndexes session high index value
 * @param priorValueIndexes reference/prior index value
 * @param valueIndexes current index value
 * @param contauctAccTrdVal accumulated matched-order trading value
 * @param contauctAccTrdVol accumulated matched-order trading volume
 * @param blkTrdAccTrdVal accumulated negotiated/block-trade value
 * @param blkTrdAccTrdVol accumulated negotiated/block-trade volume
 * @param grossTradeAmount total traded value for the day
 * @param totalVolumeTraded total traded volume for the day
 * @param marketIndexClass index classification code (e.g., "HSX")
 * @param marketId market identifier (e.g., "STO")
 * @param tradingSessionId current trading-session identifier (e.g., "40")
 * @param transactTime exchange transaction/update timestamp object
 * @param receivedAtEpochMillis local SDK receipt time
 */
public record MarketIndex(
        String indexName,
        Double changedRatio,
        Double changedValue,
        Long fluctuationSteadinessIssueCount,
        Long fluctuationDownIssueCount,
        Long fluctuationUpIssueCount,
        Long fluctuationLowerLimitIssueCount,
        Long fluctuationUpperLimitIssueCount,
        Long fluctuationDownIssueVolume,
        Long fluctuationUpIssueVolume,
        Long fluctuationSteadinessIssueVolume,
        String currencyCode,
        String indexTypeCode,
        Double lowestValueIndexes,
        Double highestValueIndexes,
        Double priorValueIndexes,
        Double valueIndexes,
        Double contauctAccTrdVal,
        Long contauctAccTrdVol,
        Double blkTrdAccTrdVal,
        Long blkTrdAccTrdVol,
        Double grossTradeAmount,
        Long totalVolumeTraded,
        String marketIndexClass,
        String marketId,
        String tradingSessionId,
        TransactTime transactTime,
        long receivedAtEpochMillis
) {

    public record TransactTime(
            Long Seconds,
            Integer Nanos
    ) {}
}
