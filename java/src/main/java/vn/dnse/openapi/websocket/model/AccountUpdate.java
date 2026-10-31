package vn.dnse.openapi.websocket.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Private account-value update from the Python SDK's account channel.
 *
 * @param cash available/reported cash balance
 * @param buyingPower current purchasing power
 * @param portfolioValue current portfolio market value
 * @param equity account equity value
 * @param timestamp event timestamp converted from DNSE epoch milliseconds
 * @param receivedAtEpochMillis local SDK receipt time
 */
public record AccountUpdate(
        BigDecimal cash,
        BigDecimal buyingPower,
        BigDecimal portfolioValue,
        BigDecimal equity,
        Instant timestamp,
        long receivedAtEpochMillis
) {}
