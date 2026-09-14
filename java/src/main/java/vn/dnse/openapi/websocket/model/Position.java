package vn.dnse.openapi.websocket.model;

import java.math.BigDecimal;

public record Position(
        long id, String accountNo, String symbol, String status, long loanPackageId, String side,
        long accumulateQuantity, long tradeQuantity, long closedQuantity,
        BigDecimal costPrice, BigDecimal marketPrice, BigDecimal breakEvenPrice,
        long openQuantity, long overNightQuantity, BigDecimal averageClosePrice,
        String marketType, String createdDate, String modifiedDate,
        long receivedAtEpochMillis
) {}
