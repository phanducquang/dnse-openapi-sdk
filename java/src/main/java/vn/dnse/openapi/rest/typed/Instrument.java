package vn.dnse.openapi.rest.typed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * Typed subset of DNSE instrument/security metadata.
 *
 * <p>The model intentionally focuses on stable fields useful to client applications and all-symbol
 * subscription planning. Unknown response fields are ignored so the typed layer remains forward
 * compatible while the raw REST response remains available for callers that need every field.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Instrument(
        String symbol,
        String isin,
        String symbolName,
        String symbolEnglishName,
        String marketId,
        String boardId,
        String boardIdOriginal,
        String productGrpId,
        String securityGroupId,
        String securityStatus,
        String symbolType,
        String tradingSessionId,
        BigDecimal referencePrice,
        BigDecimal highLimitPrice,
        BigDecimal lowLimitPrice,
        BigDecimal matchPrice,
        Long totalVolumeTraded,
        String firstTradingDate,
        String lastTradingDate,
        String listingDate
) {}
