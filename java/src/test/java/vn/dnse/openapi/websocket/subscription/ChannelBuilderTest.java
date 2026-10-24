package vn.dnse.openapi.websocket.subscription;

import org.junit.jupiter.api.Test;
import vn.dnse.openapi.websocket.MessageEncoding;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChannelBuilderTest {
    @Test
    void buildsPythonCompatibleChannels() {
        assertEquals("tick.G1.json", ChannelBuilder.trades("G1", MessageEncoding.JSON));
        assertEquals("tick_extra.G1.msgpack", ChannelBuilder.tradeExtra("G1", MessageEncoding.MSGPACK));
        assertEquals("expected_price.G1.json", ChannelBuilder.expectedPrice("G1", MessageEncoding.JSON));
        assertEquals("top_price.G1.msgpack", ChannelBuilder.quotes("G1", MessageEncoding.MSGPACK));
        assertEquals("security_definition.G1.json", ChannelBuilder.securityDefinition("G1", MessageEncoding.JSON));
        assertEquals("ohlc.1.json", ChannelBuilder.ohlc("1", MessageEncoding.JSON));
        assertEquals("ohlc_closed.1.msgpack", ChannelBuilder.ohlcClosed("1", MessageEncoding.MSGPACK));
        assertEquals("foreign.*.json", ChannelBuilder.foreignTrading("*", MessageEncoding.JSON));
        assertEquals("market_index.VNINDEX.json", ChannelBuilder.marketIndex("VNINDEX", MessageEncoding.JSON));
        assertEquals("estimated_market_index.VN30.msgpack", ChannelBuilder.estimatedMarketIndex("VN30", MessageEncoding.MSGPACK));
        assertEquals("market_index_influence.VN30.1.msgpack", ChannelBuilder.marketIndexInfluence("VN30", 1, MessageEncoding.MSGPACK));
        assertEquals("session.EQUITY.*.json", ChannelBuilder.session("EQUITY", "*", MessageEncoding.JSON));
        assertEquals("order.STOCK.json", ChannelBuilder.order("STOCK", MessageEncoding.JSON));
        assertEquals("order.broker.STOCK.12345.msgpack", ChannelBuilder.brokerOrder("STOCK", "12345", MessageEncoding.MSGPACK));
        assertEquals("position.STOCK.json", ChannelBuilder.position("STOCK", MessageEncoding.JSON));
        assertEquals("position.broker.STOCK.12345.msgpack", ChannelBuilder.brokerPosition("STOCK", "12345", MessageEncoding.MSGPACK));
        assertEquals("account", ChannelBuilder.account());
        assertEquals("orders", ChannelBuilder.orders());
        assertEquals("positions", ChannelBuilder.positions());
    }
}
