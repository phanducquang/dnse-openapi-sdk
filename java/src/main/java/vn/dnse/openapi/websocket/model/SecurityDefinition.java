package vn.dnse.openapi.websocket.model;

import java.math.BigDecimal;

/**
 * Security reference/status snapshot delivered by the security-definition channel, typically around BOD/EOD.
 *
 * @param marketId market on which the security is listed
 * @param boardId trading board identifier
 * @param symbol trading symbol
 * @param isin international security identifier
 * @param productGrpId product group such as STO/FIO depending on market
 * @param securityGroupId security group/category
 * @param basicPrice reference/basic price for the trading day
 * @param ceilingPrice daily ceiling price
 * @param floorPrice daily floor price
 * @param openInterestQuantity overnight open interest, mainly relevant to derivatives
 * @param securityStatus current trading/halt status
 * @param symbolAdminStatusCode administrative status code of the symbol
 * @param symbolTradingMethodStatusCode trading-method status code
 * @param symbolTradingSanctionStatusCode trading-sanction/restriction status code
 * @param finalTradeDate final trading date for instruments such as derivatives/warrants
 * @param listingDate listing date
 * @param time exchange event timestamp
 * @param receivedAtEpochMillis local SDK receipt time
 */
public record SecurityDefinition(
        String marketId,
        String boardId,
        String symbol,
        String isin,
        String productGrpId,
        String securityGroupId,
        BigDecimal basicPrice,
        BigDecimal ceilingPrice,
        BigDecimal floorPrice,
        long openInterestQuantity,
        String securityStatus,
        String symbolAdminStatusCode,
        String symbolTradingMethodStatusCode,
        String symbolTradingSanctionStatusCode,
        String finalTradeDate,
        String listingDate,
        String time,
        long receivedAtEpochMillis
) {}
