package vn.dnse.openapi.websocket.model;

import java.math.BigDecimal;

/**
 * Contribution of one security to an index-influence event.
 *
 * @param time update time for this contribution row
 * @param symbol constituent symbol
 * @param influence absolute contribution to index movement
 * @param influenceRatio contribution ratio reported by the feed
 * @param proportion constituent proportion/weight reported by the feed
 * @param changeRatio constituent percentage price change
 * @param changeValue constituent absolute price change
 * @param price current constituent price
 * @param grossTradeAmount constituent traded value
 * @param totalVolumeTraded constituent traded volume
 */
public record IndexInfluenceItem(
        String time, String symbol,
        BigDecimal influence, BigDecimal influenceRatio, BigDecimal proportion,
        BigDecimal changeRatio, BigDecimal changeValue, BigDecimal price,
        BigDecimal grossTradeAmount, BigDecimal totalVolumeTraded
) {}
