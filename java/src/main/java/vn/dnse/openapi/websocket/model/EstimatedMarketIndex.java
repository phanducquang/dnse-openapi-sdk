package vn.dnse.openapi.websocket.model;

import java.math.BigDecimal;

/**
 * Estimated market-index update; current DNSE market-data documentation describes estimated VN30.
 *
 * @param indexName estimated index name
 * @param changedRatio percentage change versus the reference index
 * @param changedValue absolute change versus the reference index
 * @param fluctuationSteadinessIssueCount number of unchanged constituents
 * @param fluctuationDownIssueCount number of declining constituents
 * @param fluctuationUpIssueCount number of advancing constituents
 * @param valueIndexes estimated current index value
 * @param grossTradeAmount total traded value of index constituents
 * @param totalVolumeTraded total traded volume of index constituents
 * @param time update timestamp supplied by DNSE
 * @param receivedAtEpochMillis local SDK receipt time
 */
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
