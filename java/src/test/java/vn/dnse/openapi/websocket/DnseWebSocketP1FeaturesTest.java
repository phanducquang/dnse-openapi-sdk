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
import vn.dnse.openapi.websocket.subscription.SubscriptionOptions;
import vn.dnse.openapi.websocket.subscription.SubscriptionReconciliationResult;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DnseWebSocketP1FeaturesTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void retriesInitialConnectionUntilAuthenticationSucceeds() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(503));
            AtomicInteger authCount = new AtomicInteger();
            server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    webSocket.send(ByteString.encodeUtf8("{\"session_id\":\"session-after-retry\"}"));
                }

                @Override
                public void onMessage(WebSocket webSocket, ByteString bytes) {
                    try {
                        JsonNode message = MAPPER.readTree(bytes.utf8());
                        if ("auth".equals(message.path("action").asText())) {
                            authCount.incrementAndGet();
                            webSocket.send(ByteString.encodeUtf8("{\"action\":\"auth_success\"}"));
                        }
                    } catch (Exception ignored) {
                    }
                }

                @Override
                public void onClosing(WebSocket webSocket, int code, String reason) {
                    webSocket.close(code, reason);
                }
            }));
            server.start();

            List<ConnectionState> states = Collections.synchronizedList(new ArrayList<>());
            try (DnseWebSocketClient client = new DnseWebSocketClient(config(
                    server,
                    InitialConnectionPolicy.RETRY,
                    new ReconnectPolicy(true, 2, Duration.ofMillis(10), Duration.ofMillis(20))
            ))) {
                client.onStateChanged(event -> states.add(event.current()));

                client.connect().get(2, TimeUnit.SECONDS);

                assertEquals(2, server.getRequestCount());
                assertEquals(1, authCount.get());
                assertEquals(ConnectionState.AUTHENTICATED, client.state());
                assertEquals("session-after-retry", client.sessionId());
                assertTrue(states.contains(ConnectionState.RECONNECTING));
                assertTrue(Collections.frequency(states, ConnectionState.CONNECTING) >= 2);
            }
        }
    }

    @Test
    void failFastDoesNotStartBackgroundInitialRetry() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(503));
            server.enqueue(new MockResponse().setResponseCode(503));
            server.start();

            try (DnseWebSocketClient client = new DnseWebSocketClient(config(
                    server,
                    InitialConnectionPolicy.FAIL_FAST,
                    new ReconnectPolicy(true, 3, Duration.ofMillis(10), Duration.ofMillis(20))
            ))) {
                assertThrows(ExecutionException.class, () -> client.connect().get(1, TimeUnit.SECONDS));
                Thread.sleep(80);
                assertEquals(1, server.getRequestCount());
                assertEquals(ConnectionState.DISCONNECTED, client.state());
            }
        }
    }

    @Test
    void reconcilesTradeUniverseUsingOnlyAddedAndRemovedSymbols() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            List<WireOperation> operations = Collections.synchronizedList(new ArrayList<>());
            AtomicReference<Throwable> serverError = new AtomicReference<>();

            server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    webSocket.send(ByteString.encodeUtf8("{\"session_id\":\"session-reconcile\"}"));
                }

                @Override
                public void onMessage(WebSocket webSocket, ByteString bytes) {
                    try {
                        JsonNode message = MAPPER.readTree(bytes.utf8());
                        String action = message.path("action").asText();
                        if ("auth".equals(action)) {
                            webSocket.send(ByteString.encodeUtf8("{\"action\":\"auth_success\"}"));
                        } else if ("subscribe".equals(action) || "unsubscribe".equals(action)) {
                            JsonNode channel = message.path("channels").path(0);
                            List<String> symbols = new ArrayList<>();
                            channel.path("symbols").forEach(value -> symbols.add(value.asText()));
                            operations.add(new WireOperation(action, channel.path("name").asText(), List.copyOf(symbols)));
                        }
                    } catch (Throwable error) {
                        serverError.set(error);
                    }
                }

                @Override
                public void onClosing(WebSocket webSocket, int code, String reason) {
                    webSocket.close(code, reason);
                }
            }));
            server.start();

            try (DnseWebSocketClient client = new DnseWebSocketClient(config(
                    server,
                    InitialConnectionPolicy.FAIL_FAST,
                    disabledReconnect()
            ))) {
                client.connect().get(2, TimeUnit.SECONDS);
                SubscriptionOptions options = SubscriptionOptions.builder().batchSize(2).build();

                client.subscribeTrades(Map.of("G1", List.of("FPT", "VNM", "HPG")), options);

                Map<String, List<String>> desired = new LinkedHashMap<>();
                desired.put("G1", List.of("FPT", "HPG", "SSI"));
                desired.put("G3", List.of("VCB"));

                SubscriptionReconciliationResult result = client.reconcileTrades(desired, options);

                assertEquals(4, result.desiredSymbols());
                assertEquals(2, result.addedSymbols());
                assertEquals(1, result.removedSymbols());
                assertEquals(2, result.unchangedSymbols());
                assertEquals(2, result.subscribeOperations());
                assertEquals(1, result.unsubscribeOperations());
                assertEquals(2, result.activeChannels());

                SubscriptionReconciliationResult second = client.reconcileTrades(desired, options);
                assertEquals(4, second.desiredSymbols());
                assertEquals(0, second.addedSymbols());
                assertEquals(0, second.removedSymbols());
                assertEquals(4, second.unchangedSymbols());
                assertEquals(0, second.subscribeOperations());
                assertEquals(0, second.unsubscribeOperations());

                waitForOperations(operations, 5);
                assertTrue(operations.contains(new WireOperation("subscribe", "tick.G1.json", List.of("SSI"))));
                assertTrue(operations.contains(new WireOperation("unsubscribe", "tick.G1.json", List.of("VNM"))));
                assertTrue(operations.contains(new WireOperation("subscribe", "tick.G3.json", List.of("VCB"))));
                assertFalse(operations.stream().anyMatch(operation ->
                        "unsubscribe".equals(operation.action()) && operation.symbols().contains("FPT")));
                assertNull(serverError.get());
            }
        }
    }

    private static void waitForOperations(List<WireOperation> operations, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (operations.size() < expected && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertTrue(operations.size() >= expected, "expected at least " + expected + " wire operations but got " + operations.size());
    }

    private static DnseWebSocketConfig config(
            MockWebServer server,
            InitialConnectionPolicy initialConnectionPolicy,
            ReconnectPolicy reconnectPolicy
    ) {
        return DnseWebSocketConfig.builder()
                .apiKey("test-key")
                .apiSecret("test-secret")
                .baseUrl(webSocketBaseUrl(server))
                .encoding(MessageEncoding.JSON)
                .connectTimeout(Duration.ofMillis(300))
                .heartbeatInterval(Duration.ZERO)
                .initialConnectionPolicy(initialConnectionPolicy)
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

    private record WireOperation(String action, String channel, List<String> symbols) {
    }
}
