package vn.dnse.openapi.websocket.model;

import java.math.BigDecimal;

/**
 * Private trading-position event model ported from the Python SDK.
 *
 * @param id position identifier
 * @param accountNo owning account number
 * @param symbol security symbol
 * @param status current position status
 * @param loanPackageId associated margin/loan package identifier
 * @param side position side
 * @param accumulateQuantity accumulated position quantity
 * @param tradeQuantity quantity originating from current-day trades
 * @param closedQuantity quantity already closed
 * @param costPrice position cost basis
 * @param marketPrice latest market price
 * @param breakEvenPrice estimated break-even price
 * @param openQuantity quantity still open
 * @param overNightQuantity quantity carried overnight
 * @param averageClosePrice average close price for closed quantity
 * @param marketType market type, e.g. STOCK
 * @param createdDate position creation timestamp string
 * @param modifiedDate latest position modification timestamp string
 * @param receivedAtEpochMillis local SDK receipt time
 */
public record Position(
        long id, String accountNo, String symbol, String status, long loanPackageId, String side,
        long accumulateQuantity, long tradeQuantity, long closedQuantity,
        BigDecimal costPrice, BigDecimal marketPrice, BigDecimal breakEvenPrice,
        long openQuantity, long overNightQuantity, BigDecimal averageClosePrice,
        String marketType, String createdDate, String modifiedDate,
        long receivedAtEpochMillis
) {}
