package vn.dnse.openapi.websocket.subscription;

import org.junit.jupiter.api.Test;
import vn.dnse.openapi.websocket.MessageEncoding;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChannelBuilderTest {
    @Test
    void buildsPythonCompatibleChannels() {
        assertEquals("tick.G1.json", ChannelBuilder.trades("G1", MessageEncoding.JSON));
        assertEquals("top_price.G1.msgpack", ChannelBuilder.quotes("G1", MessageEncoding.MSGPACK));
        assertEquals("ohlc.1.json", ChannelBuilder.ohlc("1", MessageEncoding.JSON));
        assertEquals("market_index_influence.VN30.1.msgpack", ChannelBuilder.marketIndexInfluence("VN30", 1, MessageEncoding.MSGPACK));
    }
}
