package vn.dnse.openapi.websocket.model;

import java.math.BigDecimal;

public record Order(
        String id, String side, String accountNo, String symbol,
        BigDecimal price, BigDecimal priceSecure, BigDecimal averagePrice,
        long quantity, long fillQuantity, long canceledQuantity, long leaveQuantity,
        String orderType, String orderStatus, long loanPackageId, String marketType,
        String transDate, String createdDate, String modifiedDate,
        long receivedAtEpochMillis
) {}
