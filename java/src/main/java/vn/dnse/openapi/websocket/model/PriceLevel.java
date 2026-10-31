package vn.dnse.openapi.websocket.model;

import java.math.BigDecimal;

/**
 * One bid/offer level in a market-depth quote.
 *
 * @param price quoted price at this level
 * @param quantity total quoted quantity available at this price level
 */
public record PriceLevel(BigDecimal price, long quantity) {}
