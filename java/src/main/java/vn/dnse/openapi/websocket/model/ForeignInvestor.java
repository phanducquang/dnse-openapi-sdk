package vn.dnse.openapi.websocket.model;

/**
 * Foreign-investor trading and ownership-room update for one symbol.
 *
 * @param marketId market identifier
 * @param boardId trading board identifier
 * @param tradingSessionId current trading-session identifier
 * @param symbol uppercase trading symbol
 * @param transactTime exchange update time as provided by the feed
 * @param foreignInvestorTypeCode foreign-investor category/type code
 * @param sellVolume sell volume on the current board/day
 * @param sellTradedAmount sell traded value on the current board/day
 * @param buyVolume buy volume on the current board/day
 * @param buyTradedAmount buy traded value on the current board/day
 * @param totalSellVolume accumulated sell volume across the day
 * @param totalSellTradedAmount accumulated sell traded value across the day
 * @param totalBuyVolume accumulated buy volume across the day
 * @param totalBuyTradedAmount accumulated buy traded value across the day
 * @param foreignerOrderLimitQuantity maximum foreign ownership/order limit quantity reported by the feed
 * @param foreignerBuyPossibleQuantity remaining quantity foreign investors can still buy (foreign room)
 * @param receivedAtEpochMillis local SDK receipt time
 */
public record ForeignInvestor(
        String marketId, String boardId, String tradingSessionId, String symbol,
        String transactTime, String foreignInvestorTypeCode,
        long sellVolume, long sellTradedAmount, long buyVolume, long buyTradedAmount,
        long totalSellVolume, long totalSellTradedAmount,
        long totalBuyVolume, long totalBuyTradedAmount,
        long foreignerOrderLimitQuantity, long foreignerBuyPossibleQuantity,
        long receivedAtEpochMillis
) {}
