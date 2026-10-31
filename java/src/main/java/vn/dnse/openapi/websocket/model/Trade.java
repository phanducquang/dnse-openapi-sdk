package vn.dnse.openapi.websocket.model;

import java.math.BigDecimal;

/**
 * Realtime matched-trade update from {@code tick.{boardId}.{encoding}}.
 *
 * @param marketId market/product identifier from the exchange feed
 * @param boardId trading board such as G1, G3 or G4
 * @param isin international security identifier
 * @param symbol uppercase trading symbol, e.g. FPT or HPG
 * @param price latest matched price ({@code matchPrice} in the wire payload)
 * @param quantity latest matched quantity ({@code matchQtty})
 * @param totalVolumeTraded accumulated matched volume for the trading day
 * @param grossTradeAmount accumulated traded value for the trading day
 * @param highestPrice highest matched price observed during the day
 * @param lowestPrice lowest matched price observed during the day
 * @param openPrice opening price
 * @param tradingSessionId current exchange trading-session identifier
 * @param time exchange event timestamp in the SDK's normalized string representation
 * @param receivedAtEpochMillis local time when the Java SDK received the message
 */
public record Trade(
        String marketId,
        String boardId,
        String isin,
        String symbol,
        BigDecimal price,
        long quantity,
        long totalVolumeTraded,
        BigDecimal grossTradeAmount,
        BigDecimal highestPrice,
        BigDecimal lowestPrice,
        BigDecimal openPrice,
        int tradingSessionId,
        String time,
        long receivedAtEpochMillis
) {}
