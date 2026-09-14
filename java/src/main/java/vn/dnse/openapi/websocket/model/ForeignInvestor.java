package vn.dnse.openapi.websocket.model;

public record ForeignInvestor(
        String marketId, String boardId, String tradingSessionId, String symbol,
        String transactTime, String foreignInvestorTypeCode,
        long sellVolume, long sellTradedAmount, long buyVolume, long buyTradedAmount,
        long totalSellVolume, long totalSellTradedAmount,
        long totalBuyVolume, long totalBuyTradedAmount,
        long foreignerOrderLimitQuantity, long foreignerBuyPossibleQuantity,
        long receivedAtEpochMillis
) {}
