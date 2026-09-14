package vn.dnse.openapi.websocket.subscription;

import vn.dnse.openapi.websocket.MessageEncoding;

/** Builds exact channel names expected by the DNSE WebSocket protocol. */
public final class ChannelBuilder {
    private ChannelBuilder() {}

    /** Realtime matched trades: {@code tick.{boardId}.{encoding}}. */
    public static String trades(String boardId, MessageEncoding encoding) { return "tick." + boardId + "." + encoding.wireName(); }
    /** Enhanced trades including DNSE-derived side/average price: {@code tick_extra.{boardId}.{encoding}}. */
    public static String tradeExtra(String boardId, MessageEncoding encoding) { return "tick_extra." + boardId + "." + encoding.wireName(); }
    /** ATO/ATC indicative match price: {@code expected_price.{boardId}.{encoding}}. */
    public static String expectedPrice(String boardId, MessageEncoding encoding) { return "expected_price." + boardId + "." + encoding.wireName(); }
    /** Market depth/top price feed: {@code top_price.{boardId}.{encoding}}. */
    public static String quotes(String boardId, MessageEncoding encoding) { return "top_price." + boardId + "." + encoding.wireName(); }
    /** Beginning-of-day/security status feed: {@code security_definition.{boardId}.{encoding}}. */
    public static String securityDefinition(String boardId, MessageEncoding encoding) { return "security_definition." + boardId + "." + encoding.wireName(); }
    /** Forming candle feed: {@code ohlc.{resolution}.{encoding}}. */
    public static String ohlc(String resolution, MessageEncoding encoding) { return "ohlc." + resolution + "." + encoding.wireName(); }
    /** Finalized candle feed: {@code ohlc_closed.{resolution}.{encoding}}. */
    public static String ohlcClosed(String resolution, MessageEncoding encoding) { return "ohlc_closed." + resolution + "." + encoding.wireName(); }
    /** Foreign-investor activity: {@code foreign.{boardId}.{encoding}}. */
    public static String foreignTrading(String boardId, MessageEncoding encoding) { return "foreign." + boardId + "." + encoding.wireName(); }
    /** Market index snapshot/update: {@code market_index.{indexName}.{encoding}}. */
    public static String marketIndex(String indexName, MessageEncoding encoding) { return "market_index." + indexName + "." + encoding.wireName(); }
    /** Estimated market index (currently VN30 in DNSE docs): {@code estimated_market_index.{indexName}.{encoding}}. */
    public static String estimatedMarketIndex(String indexName, MessageEncoding encoding) { return "estimated_market_index." + indexName + "." + encoding.wireName(); }
    /** Per-symbol index contribution feed used by the Python SDK. */
    public static String marketIndexInfluence(String indexName, int resolution, MessageEncoding encoding) { return "market_index_influence." + indexName + "." + resolution + "." + encoding.wireName(); }
    /** Trading-session state: {@code session.{productGroupId}.{boardId}.{encoding}}. */
    public static String session(String productGroupId, String boardId, MessageEncoding encoding) { return "session." + productGroupId + "." + boardId + "." + encoding.wireName(); }
    /** Private order-event feed for the authenticated account/market type. */
    public static String order(String marketType, MessageEncoding encoding) { return "order." + marketType + "." + encoding.wireName(); }
    /** Broker-scoped private order-event feed for a specific investor. */
    public static String brokerOrder(String marketType, String investorId, MessageEncoding encoding) { return "order.broker." + marketType + "." + investorId + "." + encoding.wireName(); }
    /** Private position-event feed for the authenticated account/market type. */
    public static String position(String marketType, MessageEncoding encoding) { return "position." + marketType + "." + encoding.wireName(); }
    /** Broker-scoped private position-event feed for a specific investor. */
    public static String brokerPosition(String marketType, String investorId, MessageEncoding encoding) { return "position.broker." + marketType + "." + investorId + "." + encoding.wireName(); }
    /** Legacy/general account-update channel from the Python SDK. */
    public static String account() { return "account"; }
    /** Legacy/general order channel from the Python SDK. */
    public static String orders() { return "orders"; }
    /** Legacy/general position channel from the Python SDK. */
    public static String positions() { return "positions"; }
}
