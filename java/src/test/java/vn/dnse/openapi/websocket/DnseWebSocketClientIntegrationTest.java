package vn.dnse.openapi.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.ByteString;
import org.junit.jupiter.api.Test;
import vn.dnse.openapi.websocket.model.Trade;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DnseWebSocketClientIntegrationTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void performsWelcomeAuthSubscribeAndDispatchFlow() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            CountDownLatch authReceived = new CountDownLatch(1);
            CountDownLatch subscribeReceived = new CountDownLatch(1);
            CountDownLatch tradeReceived = new CountDownLatch(1);
            AtomicReference<JsonNode> authMessage = new AtomicReference<>();
            AtomicReference<Throwable> serverError = new AtomicReference<>();
            AtomicReference<Trade> receivedTrade = new AtomicReference<>();

            server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    webSocket.send(ByteString.encodeUtf8("{\"session_id\":\"session-1\"}"));
                }

                @Override
                public void onMessage(WebSocket webSocket, ByteString bytes) {
                    try {
                        JsonNode message = MAPPER.readTree(bytes.utf8());
                        String action = message.path("action").asText();
                        if ("auth".equals(action)) {
                            authMessage.set(message);
                            authReceived.countDown();
                            webSocket.send(ByteString.encodeUtf8("{\"action\":\"auth_success\"}"));
                        } else if ("subscribe".equals(action)) {
                            subscribeReceived.countDown();
                            webSocket.send(ByteString.encodeUtf8("{\"T\":\"t\",\"marketId\":\"HOSE\",\"boardId\":\"G1\",\"symbol\":\"FPT\",\"matchPrice\":123.45,\"matchQtty\":100}"));
                        }
                    } catch (Throwable error) {
                        serverError.set(error);
                    }
                }
            }));
            server.start();

            try (DnseWebSocketClient client = new DnseWebSocketClient(config(server, disabledReconnect()))) {
                client.onTrade(trade -> {
                    receivedTrade.set(trade);
                    tradeReceived.countDown();
                });

                client.connect().get(2, TimeUnit.SECONDS);
                assertEquals(ConnectionState.AUTHENTICATED, client.state());
                assertEquals("session-1", client.sessionId());
                assertTrue(authReceived.await(1, TimeUnit.SECONDS));

                JsonNode auth = authMessage.get();
                assertNotNull(auth);
                assertEquals("test-key", auth.path("api_key").asText());
                assertEquals(1_720_000_000L, auth.path("timestamp").asLong());
                assertEquals("1720000000123456", auth.path("nonce").asText());

                client.subscribeTrades(List.of("FPT"), "G1");
                assertTrue(subscribeReceived.await(1, TimeUnit.SECONDS));
                assertTrue(tradeReceived.await(1, TimeUnit.SECONDS));
                assertEquals("FPT", receivedTrade.get().symbol());
                assertEquals(100L, receivedTrade.get().quantity());
                assertEquals(null, serverError.get());
            }
        }
    }

    @Test
    void reconnectsReauthenticatesAndRestoresSubscriptionsAfterServerRestart() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            CountDownLatch firstSubscribed = new CountDownLatch(1);
            CountDownLatch restoredSubscription = new CountDownLatch(1);
            AtomicInteger authCount = new AtomicInteger();
            AtomicReference<Throwable> serverError = new AtomicReference<>();

            server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    webSocket.send(ByteString.encodeUtf8("{\"session_id\":\"session-1\"}"));
                }

                @Override
                public void onMessage(WebSocket webSocket, ByteString bytes) {
                    try {
                        JsonNode message = MAPPER.readTree(bytes.utf8());
                        String action = message.path("action").asText();
                        if ("auth".equals(action)) {
                            authCount.incrementAndGet();
                            webSocket.send(ByteString.encodeUtf8("{\"action\":\"auth_success\"}"));
                        } else if ("subscribe".equals(action)) {
                            firstSubscribed.countDown();
                            webSocket.close(1012, "service restart");
                        }
                    } catch (Throwable error) {
                        serverError.set(error);
                    }
                }
            }));

            server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    webSocket.send(ByteString.encodeUtf8("{\"session_id\":\"session-2\"}"));
                }

                @Override
                public void onMessage(WebSocket webSocket, ByteString bytes) {
                    try {
                        JsonNode message = MAPPER.readTree(bytes.utf8());
                        String action = message.path("action").asText();
                        if ("auth".equals(action)) {
                            authCount.incrementAndGet();
                            webSocket.send(ByteString.encodeUtf8("{\"action\":\"auth_success\"}"));
                        } else if ("subscribe".equals(action)) {
                            JsonNode channel = message.path("channels").path(0);
                            if ("tick.G1.json".equals(channel.path("name").asText())
                                    && channel.path("symbols").toString().contains("FPT")) {
                                restoredSubscription.countDown();
                            }
                        }
                    } catch (Throwable error) {
                        serverError.set(error);
                    }
                }
            }));
            server.start();

            ReconnectPolicy reconnect = new ReconnectPolicy(true, 3, Duration.ofMillis(10), Duration.ofMillis(50));
            try (DnseWebSocketClient client = new DnseWebSocketClient(config(server, reconnect))) {
                client.connect().get(2, TimeUnit.SECONDS);
                client.subscribeTrades(List.of("FPT"), "G1");

                assertTrue(firstSubscribed.await(1, TimeUnit.SECONDS));
                assertTrue(restoredSubscription.await(3, TimeUnit.SECONDS));
                assertEquals(2, authCount.get());
                assertEquals(ConnectionState.AUTHENTICATED, client.state());
                assertEquals("session-2", client.sessionId());
                assertEquals(null, serverError.get());
            }
        }
    }

    private static DnseWebSocketConfig config(MockWebServer server, ReconnectPolicy reconnectPolicy) {
        return DnseWebSocketConfig.builder()
                .apiKey("test-key")
                .apiSecret("test-secret")
                .baseUrl(webSocketBaseUrl(server))
                .encoding(MessageEncoding.JSON)
                .connectTimeout(Duration.ofSeconds(2))
                .heartbeatInterval(Duration.ZERO)
                .reconnectPolicy(reconnectPolicy)
                .clock(Clock.fixed(Instant.ofEpochSecond(1_720_000_000L), ZoneOffset.UTC))
                .nonceGenerator(() -> "1720000000123456")
                .build();
    }

    private static ReconnectPolicy disabledReconnect() {
        return new ReconnectPolicy(false, 0, Duration.ofMillis(10), Duration.ofMillis(10));
    }

    private static String webSocketBaseUrl(MockWebServer server) {
        String httpUrl = server.url("/").toString();
        return httpUrl.replaceFirst("^http", "ws").replaceAll("/$", "");
    }
}
