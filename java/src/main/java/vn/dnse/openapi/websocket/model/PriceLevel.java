package vn.dnse.openapi.websocket.model;

import java.math.BigDecimal;

public record PriceLevel(BigDecimal price, long quantity) {}
