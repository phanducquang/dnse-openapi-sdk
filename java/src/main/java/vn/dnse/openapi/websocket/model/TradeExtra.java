package vn.dnse.openapi.websocket.model;

import java.math.BigDecimal;

/**
 * Enhanced realtime trade update from {@code tick_extra.{boardId}.{encoding}}.
 *
 * <p>It contains the normal trade fields plus DNSE-derived active side and average matched price.</p>
 *
 * @param marketId market/product identifier
 * @param boardId trading board identifier
 * @param isin international security identifier
 * @param symbol uppercase trading symbol
 * @param price latest matched price
 * @param quantity latest matched quantity
 * @param side active buy/sell side code as represented by the current Python-compatible Java model;
 *             current DNSE documentation may expose textual values such as SELL
 * @param avgPrice average matched price calculated by DNSE
 * @param totalVolumeTraded accumulated matched volume for the day
 * @param grossTradeAmount accumulated traded value for the day
 * @param highestPrice highest matched price for the day
 * @param lowestPrice lowest matched price for the day
 * @param openPrice opening price
 * @param tradingSessionId current exchange trading-session identifier
 * @param time exchange event timestamp
 * @param receivedAtEpochMillis local SDK receipt time
 */
public record TradeExtra(
        String marketId, String boardId, String isin, String symbol,
        BigDecimal price, long quantity, int side, BigDecimal avgPrice,
        long totalVolumeTraded, BigDecimal grossTradeAmount,
        BigDecimal highestPrice, BigDecimal lowestPrice, BigDecimal openPrice,
        int tradingSessionId, String time, long receivedAtEpochMillis
) {}
