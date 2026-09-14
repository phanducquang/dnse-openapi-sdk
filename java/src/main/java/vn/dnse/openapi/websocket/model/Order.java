package vn.dnse.openapi.websocket.model;

import java.math.BigDecimal;

/**
 * Private order event model ported from the Python SDK.
 *
 * @param id order identifier
 * @param side order side (buy/sell code supplied by DNSE)
 * @param accountNo account number associated with the order
 * @param symbol security symbol
 * @param price submitted/order price
 * @param priceSecure secured/reference price supplied by the private feed
 * @param averagePrice average execution price of filled quantity
 * @param quantity original order quantity
 * @param fillQuantity quantity already filled
 * @param canceledQuantity quantity canceled
 * @param leaveQuantity quantity still open/unfilled
 * @param orderType DNSE order type
 * @param orderStatus current order status
 * @param loanPackageId margin/loan package identifier when applicable
 * @param marketType market type, e.g. STOCK
 * @param transDate transaction/trading date string from DNSE
 * @param createdDate order creation timestamp string
 * @param modifiedDate latest order modification timestamp string
 * @param receivedAtEpochMillis local SDK receipt time
 */
public record Order(
        String id, String side, String accountNo, String symbol,
        BigDecimal price, BigDecimal priceSecure, BigDecimal averagePrice,
        long quantity, long fillQuantity, long canceledQuantity, long leaveQuantity,
        String orderType, String orderStatus, long loanPackageId, String marketType,
        String transDate, String createdDate, String modifiedDate,
        long receivedAtEpochMillis
) {}
