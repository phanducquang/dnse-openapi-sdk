package vn.dnse.openapi.websocket.model;

import java.math.BigDecimal;

/**
 * Indicative matching information used during periodic ATO/ATC sessions.
 *
 * @param marketId market/product identifier
 * @param boardId trading board identifier
 * @param isin international security identifier
 * @param symbol uppercase trading symbol
 * @param closePrice current closing/reference close value supplied by the feed
 * @param expectedTradePrice indicative price expected to match at the current calculation time
 * @param expectedTradeQuantity indicative quantity expected to match
 * @param time exchange event timestamp
 * @param receivedAtEpochMillis local SDK receipt time
 */
public record ExpectedPrice(
        String marketId, String boardId, String isin, String symbol,
        BigDecimal closePrice, BigDecimal expectedTradePrice,
        long expectedTradeQuantity, String time, long receivedAtEpochMillis
) {}
