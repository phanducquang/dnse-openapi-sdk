package vn.dnse.openapi.websocket.model;

import java.math.BigDecimal;

/**
 * OHLC candle used by both forming {@code ohlc.*} and finalized {@code ohlc_closed.*} events.
 *
 * @param symbol security, derivative symbol type, or market index represented by this candle
 * @param resolution candle interval such as 1, 3, 5, 15, 30, 1H, 1D or 1W
 * @param open first traded/index value in the candle
 * @param high highest value in the candle
 * @param low lowest value in the candle
 * @param close latest value (forming candle) or final closing value (closed candle)
 * @param volume traded volume accumulated in the candle
 * @param time Unix timestamp for the candle start
 * @param lastUpdated Unix timestamp of the latest candle update
 * @param type market group such as STOCK, DERIVATIVE or INDEX
 * @param receivedAtEpochMillis local SDK receipt time
 */
public record Ohlc(
        String symbol,
        String resolution,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume,
        long time,
        long lastUpdated,
        String type,
        long receivedAtEpochMillis
) {}
