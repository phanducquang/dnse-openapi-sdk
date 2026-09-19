package vn.dnse.openapi.websocket.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Realtime market-depth update from {@code top_price.{boardId}.{encoding}}.
 *
 * <p>DNSE documentation currently describes up to three levels for HOSE and up to ten levels for
 * HNX/UPCOM. The first bid is the best/highest bid and the first offer is the best/lowest ask.</p>
 *
 * @param marketId market/product identifier
 * @param boardId trading board identifier
 * @param symbol uppercase trading symbol
 * @param isin international security identifier
 * @param bid ordered bid levels, best bid first
 * @param offer ordered offer/ask levels, best ask first
 * @param totalOfferQtty total offered/sell quantity
 * @param totalBidQtty total bid/buy quantity
 * @param time exchange event timestamp
 * @param receivedAtEpochMillis local SDK receipt time
 */
public record Quote(
        String marketId,
        String boardId,
        String symbol,
        String isin,
        List<PriceLevel> bid,
        List<PriceLevel> offer,
        BigDecimal totalOfferQtty,
        BigDecimal totalBidQtty,
        String time,
        long receivedAtEpochMillis
) {
    /** @return best/highest bid level when at least one bid exists */
    public Optional<PriceLevel> bestBid() { return bid == null || bid.isEmpty() ? Optional.empty() : Optional.of(bid.get(0)); }

    /** @return best/lowest offer level when at least one offer exists */
    public Optional<PriceLevel> bestAsk() { return offer == null || offer.isEmpty() ? Optional.empty() : Optional.of(offer.get(0)); }

    /**
     * Computes best ask minus best bid.
     * @return spread when both sides of the book are available
     */
    public Optional<BigDecimal> spread() {
        return bestBid().flatMap(b -> bestAsk().map(a -> a.price().subtract(b.price())));
    }
}
