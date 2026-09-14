package vn.dnse.openapi.websocket.model;

import java.math.BigDecimal;

public record SecurityDefinition(
        String marketId,
        String boardId,
        String symbol,
        String isin,
        String productGrpId,
        String securityGroupId,
        BigDecimal basicPrice,
        BigDecimal ceilingPrice,
        BigDecimal floorPrice,
        long openInterestQuantity,
        String securityStatus,
        String symbolAdminStatusCode,
        String symbolTradingMethodStatusCode,
        String symbolTradingSanctionStatusCode,
        String finalTradeDate,
        String listingDate,
        String time,
        long receivedAtEpochMillis
) {}
