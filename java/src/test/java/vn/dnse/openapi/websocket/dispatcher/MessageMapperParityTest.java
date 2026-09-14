package vn.dnse.openapi.websocket.dispatcher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vn.dnse.openapi.websocket.model.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class MessageMapperParityTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void mapsAllPythonMessageTypesToEquivalentJavaEvents() throws Exception {
        List<Case> cases = List.of(
                new Case("{\"T\":\"t\",\"symbol\":\"FPT\",\"matchPrice\":123.45,\"matchQtty\":100}", "trade", Trade.class),
                new Case("{\"T\":\"te\",\"symbol\":\"FPT\",\"matchPrice\":123.45,\"matchQtty\":100,\"side\":1}", "trade_extra", TradeExtra.class),
                new Case("{\"T\":\"e\",\"symbol\":\"FPT\",\"expectedTradePrice\":123.5}", "expected_price", ExpectedPrice.class),
                new Case("{\"T\":\"sd\",\"symbol\":\"FPT\",\"basicPrice\":120}", "security_definition", SecurityDefinition.class),
                new Case("{\"T\":\"q\",\"symbol\":\"FPT\",\"bid\":[{\"price\":123,\"qtty\":10}],\"offer\":[]}", "quote", Quote.class),
                new Case("{\"T\":\"b\",\"symbol\":\"FPT\",\"resolution\":\"1\",\"open\":120,\"high\":125,\"low\":119,\"close\":123}", "ohlc", Ohlc.class),
                new Case("{\"T\":\"bc\",\"symbol\":\"FPT\",\"resolution\":\"1\",\"open\":120,\"high\":125,\"low\":119,\"close\":123}", "ohlc_closed", Ohlc.class),
                new Case("{\"T\":\"do\",\"order\":{\"id\":\"o1\",\"symbol\":\"FPT\",\"price\":123,\"quantity\":10}}", "order_event", Order.class),
                new Case("{\"T\":\"eo\",\"order\":{\"id\":\"o2\",\"symbol\":\"FPT\",\"price\":123,\"quantity\":10}}", "order_event", Order.class),
                new Case("{\"T\":\"dp\",\"position\":{\"id\":1,\"symbol\":\"FPT\",\"marketPrice\":123}}", "position_event", Position.class),
                new Case("{\"T\":\"ep\",\"position\":{\"id\":1,\"symbol\":\"FPT\",\"marketPrice\":123}}", "position_event", Position.class),
                new Case("{\"T\":\"mi\",\"indexName\":\"VNINDEX\",\"valueIndexes\":1300}", "market_index", MarketIndex.class),
                new Case("{\"T\":\"emi\",\"marketIndex\":{\"indexName\":\"VN30\",\"valueIndexes\":1400}}", "estimated_market_index", EstimatedMarketIndex.class),
                new Case("{\"T\":\"ii\",\"index_name\":\"VN30\",\"Data\":[{\"symbol\":\"FPT\",\"influence\":1.2}]}", "market_index_influence", IndexInfluence.class),
                new Case("{\"T\":\"a\",\"cash\":1000,\"buyingPower\":2000,\"portfolioValue\":3000,\"equity\":4000,\"timestamp\":1720000000000}", "account", AccountUpdate.class),
                new Case("{\"T\":\"f\",\"symbol\":\"FPT\",\"buyVolume\":10,\"sellVolume\":5}", "foreign", ForeignInvestor.class),
                new Case("{\"T\":\"s\",\"marketId\":\"HOSE\",\"boardId\":\"G1\",\"eventId\":\"OPEN\",\"tradingSessionId\":1}", "session", Session.class)
        );

        for (Case testCase : cases) {
            JsonNode node = mapper.readTree(testCase.json());
            assertEquals(testCase.eventName(), MessageMapper.eventName(node), testCase.json());
            assertInstanceOf(testCase.type(), MessageMapper.map(node, 123L), testCase.json());
        }
    }

    @Test
    void keepsNestedSymbolForPrivateEvents() throws Exception {
        JsonNode order = mapper.readTree("{\"T\":\"do\",\"order\":{\"symbol\":\"FPT\"}}");
        JsonNode position = mapper.readTree("{\"T\":\"dp\",\"position\":{\"symbol\":\"VNM\"}}");

        assertEquals("FPT", MessageMapper.symbol(order));
        assertEquals("VNM", MessageMapper.symbol(position));
    }

    private record Case(String json, String eventName, Class<?> type) {}
}
