package vn.dnse.openapi.websocket.dispatcher;

import com.fasterxml.jackson.databind.JsonNode;
import vn.dnse.openapi.websocket.model.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class MessageMapper {
    private MessageMapper() {}

    public static Object map(JsonNode data, long receivedAt) {
        return switch (text(data, "T")) {
            case "t" -> trade(data, receivedAt);
            case "q" -> quote(data, receivedAt);
            case "b", "bc" -> ohlc(data, receivedAt);
            case "sd" -> securityDefinition(data, receivedAt);
            default -> null;
        };
    }

    public static String symbol(JsonNode data) {
        String symbol = text(data, "symbol");
        return symbol == null ? text(data, "Symbol") : symbol;
    }

    private static Trade trade(JsonNode d, long receivedAt) {
        return new Trade(text(d,"marketId"), text(d,"boardId"), text(d,"isin"), text(d,"symbol"),
                decimal(d,"matchPrice"), number(d,"matchQtty"), number(d,"totalVolumeTraded"), decimal(d,"grossTradeAmount"),
                decimal(d,"highestPrice"), decimal(d,"lowestPrice"), decimal(d,"openPrice"), (int) number(d,"tradingSessionId"),
                timestamp(d.get("time")), receivedAt);
    }

    private static Quote quote(JsonNode d, long receivedAt) {
        return new Quote(text(d,"marketId"), text(d,"boardId"), text(d,"symbol"), text(d,"isin"), levels(d.get("bid")),
                levels(d.get("offer")), decimal(d,"totalOfferQtty"), decimal(d,"totalBidQtty"), timestamp(d.get("time")), receivedAt);
    }

    private static Ohlc ohlc(JsonNode d, long receivedAt) {
        return new Ohlc(text(d,"symbol"), text(d,"resolution"), decimal(d,"open"), decimal(d,"high"), decimal(d,"low"),
                decimal(d,"close"), number(d,"volume"), number(d,"time"), number(d,"lastUpdated"), text(d,"type"), receivedAt);
    }

    private static SecurityDefinition securityDefinition(JsonNode d, long receivedAt) {
        return new SecurityDefinition(text(d,"marketId"), text(d,"boardId"), text(d,"symbol"), text(d,"isin"), text(d,"productGrpId"),
                text(d,"securityGroupId"), decimal(d,"basicPrice"), decimal(d,"ceilingPrice"), decimal(d,"floorPrice"),
                number(d,"openInterestQuantity"), text(d,"securityStatus"), text(d,"symbolAdminStatusCode"),
                text(d,"symbolTradingMethodStatusCode"), text(d,"symbolTradingSanctionStatusCode"), timestamp(d.get("finalTradeDate")),
                timestamp(d.get("listingDate")), timestamp(d.get("time")), receivedAt);
    }

    private static List<PriceLevel> levels(JsonNode node) {
        List<PriceLevel> result = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) result.add(new PriceLevel(decimal(item,"price"), number(item,"qtty")));
        }
        return List.copyOf(result);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static long number(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? 0L : value.asLong();
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) return BigDecimal.ZERO;
        try { return value.decimalValue(); } catch (Exception ignored) { return new BigDecimal(value.asText("0")); }
    }

    private static String timestamp(JsonNode value) {
        if (value == null || value.isNull()) return null;
        if (value.isTextual()) return value.asText();
        if (value.isNumber()) return value.asText();
        if (value.isObject()) {
            JsonNode seconds = value.get("Seconds");
            if (seconds == null) seconds = value.get("seconds");
            JsonNode nanos = value.get("Nanos");
            if (nanos == null) nanos = value.get("nanos");
            return (seconds == null ? "0" : seconds.asText()) + ":" + (nanos == null ? "0" : nanos.asText());
        }
        return value.toString();
    }
}
