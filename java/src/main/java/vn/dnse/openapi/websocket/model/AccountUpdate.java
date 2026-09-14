package vn.dnse.openapi.websocket.model;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountUpdate(
        BigDecimal cash,
        BigDecimal buyingPower,
        BigDecimal portfolioValue,
        BigDecimal equity,
        Instant timestamp,
        long receivedAtEpochMillis
) {}
