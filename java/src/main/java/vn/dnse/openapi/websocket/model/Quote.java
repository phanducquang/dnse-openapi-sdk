package vn.dnse.openapi.websocket.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

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
    public Optional<PriceLevel> bestBid() { return bid == null || bid.isEmpty() ? Optional.empty() : Optional.of(bid.get(0)); }
    public Optional<PriceLevel> bestAsk() { return offer == null || offer.isEmpty() ? Optional.empty() : Optional.of(offer.get(0)); }
    public Optional<BigDecimal> spread() {
        return bestBid().flatMap(b -> bestAsk().map(a -> a.price().subtract(b.price())));
    }
}
