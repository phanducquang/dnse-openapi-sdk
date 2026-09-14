package vn.dnse.openapi.websocket.subscription;

import vn.dnse.openapi.websocket.MessageEncoding;

public final class ChannelBuilder {
    private ChannelBuilder() {}

    public static String trades(String boardId, MessageEncoding encoding) { return "tick." + boardId + "." + encoding.wireName(); }
    public static String tradeExtra(String boardId, MessageEncoding encoding) { return "tick_extra." + boardId + "." + encoding.wireName(); }
    public static String expectedPrice(String boardId, MessageEncoding encoding) { return "expected_price." + boardId + "." + encoding.wireName(); }
    public static String quotes(String boardId, MessageEncoding encoding) { return "top_price." + boardId + "." + encoding.wireName(); }
    public static String securityDefinition(String boardId, MessageEncoding encoding) { return "security_definition." + boardId + "." + encoding.wireName(); }
    public static String ohlc(String resolution, MessageEncoding encoding) { return "ohlc." + resolution + "." + encoding.wireName(); }
    public static String ohlcClosed(String resolution, MessageEncoding encoding) { return "ohlc_closed." + resolution + "." + encoding.wireName(); }
    public static String foreignTrading(String boardId, MessageEncoding encoding) { return "foreign." + boardId + "." + encoding.wireName(); }
    public static String marketIndex(String indexName, MessageEncoding encoding) { return "market_index." + indexName + "." + encoding.wireName(); }
    public static String estimatedMarketIndex(String indexName, MessageEncoding encoding) { return "estimated_market_index." + indexName + "." + encoding.wireName(); }
    public static String marketIndexInfluence(String indexName, int resolution, MessageEncoding encoding) { return "market_index_influence." + indexName + "." + resolution + "." + encoding.wireName(); }
    public static String session(String productGroupId, String boardId, MessageEncoding encoding) { return "session." + productGroupId + "." + boardId + "." + encoding.wireName(); }
    public static String order(String marketType, MessageEncoding encoding) { return "order." + marketType + "." + encoding.wireName(); }
    public static String brokerOrder(String marketType, String investorId, MessageEncoding encoding) { return "order.broker." + marketType + "." + investorId + "." + encoding.wireName(); }
    public static String position(String marketType, MessageEncoding encoding) { return "position." + marketType + "." + encoding.wireName(); }
    public static String brokerPosition(String marketType, String investorId, MessageEncoding encoding) { return "position.broker." + marketType + "." + investorId + "." + encoding.wireName(); }
    public static String account() { return "account"; }
    public static String orders() { return "orders"; }
    public static String positions() { return "positions"; }
}
