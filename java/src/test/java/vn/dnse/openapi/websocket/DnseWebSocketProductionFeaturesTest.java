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
import vn.dnse.openapi.websocket.exception.DnseSubscriptionException;
import vn.dnse.openapi.websocket.metrics.DnseWebSocketMetricsListener;
import vn.dnse.openapi.websocket.subscription.BulkSubscriptionResult;
import vn.dnse.openapi.websocket.subscription.SubscriptionConfirmation;
import vn.dnse.openapi.websocket.subscription.SubscriptionOptions;
import vn.dnse.openapi.websocket.subscription.SubscriptionResult;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DnseWebSocketProductionFeaturesTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void bulkSubscriptionsAreBatchedAndExposeStateAndMetrics() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            CountDownLatch subscriptionsReceived = new CountDownLatch(3);
            List<Integer> batchSizes = Collections.synchronizedList(new ArrayList<>());
            AtomicReference<Throwable> serverError = new AtomicReference<>();

            server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    webSocket.send(ByteString.encodeUtf8("{\"session_id\":\"session-bulk\"}"));
                }

                @Override
                public void onMessage(WebSocket webSocket, ByteString bytes) {
                    try {
                        JsonNode message = MAPPER.readTree(bytes.utf8());
                        String action = message.path("action").asText();
                        if ("auth".equals(action)) {
                            webSocket.send(ByteString.encodeUtf8("{\"action\":\"auth_success\"}"));
                        } else if ("subscribe".equals(action)) {
                            JsonNode channel = message.path("channels").path(0);
                            assertEquals("tick.G1.json", channel.path("name").asText());
                            batchSizes.add(channel.path("symbols").size());
                            subscriptionsReceived.countDown();
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

            List<ConnectionState> states = Collections.synchronizedList(new ArrayList<>());
            AtomicInteger metricSubscribedSymbols = new AtomicInteger();
            AtomicInteger metricMessages = new AtomicInteger();

            try (DnseWebSocketClient client = new DnseWebSocketClient(config(server))) {
                client.onStateChanged(event -> states.add(event.current()));
                client.onMetrics(new DnseWebSocketMetricsListener() {
                    @Override
                    public void onSubscriptionAdded(String channel, int symbolCount) {
                        metricSubscribedSymbols.addAndGet(symbolCount);
                    }

                    @Override
                    public void onMessageReceived(String eventName) {
                        metricMessages.incrementAndGet();
                    }
                });

                client.connect().get(2, TimeUnit.SECONDS);

                Map<String, List<String>> symbolsByBoard = new LinkedHashMap<>();
                symbolsByBoard.put("G1", List.of("FPT", "VNM", "HPG", "SSI", "VCB"));

                BulkSubscriptionResult result = client.subscribeTrades(
                        symbolsByBoard,
                        SubscriptionOptions.builder().batchSize(2).build()
                );

                assertTrue(subscriptionsReceived.await(1, TimeUnit.SECONDS));
                assertEquals(List.of(2, 2, 1), batchSizes);
                assertEquals(5, result.requestedSymbols());
                assertEquals(5, result.subscribedSymbols());
                assertEquals(3, result.subscriptionCount());
                assertEquals(5, metricSubscribedSymbols.get());
                assertTrue(metricMessages.get() >= 2); // welcome + auth_success
                assertTrue(states.contains(ConnectionState.CONNECTING));
                assertTrue(states.contains(ConnectionState.CONNECTED));
                assertTrue(states.contains(ConnectionState.AUTHENTICATING));
                assertTrue(states.contains(ConnectionState.AUTHENTICATED));
                assertEquals(6, client.dispatcherStats().workerCount());
                assertNull(serverError.get());
            }
        }
    }

    @Test
    void asyncSubscriptionReportsTransportAcceptanceAndServerErrorsAreStructured() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            CountDownLatch serverErrorReceived = new CountDownLatch(1);
            AtomicReference<DnseSubscriptionException> receivedError = new AtomicReference<>();

            server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    webSocket.send(ByteString.encodeUtf8("{\"session_id\":\"session-error\"}"));
                }

                @Override
                public void onMessage(WebSocket webSocket, ByteString bytes) {
                    try {
                        JsonNode message = MAPPER.readTree(bytes.utf8());
                        String action = message.path("action").asText();
                        if ("auth".equals(action)) {
                            webSocket.send(ByteString.encodeUtf8("{\"action\":\"auth_success\"}"));
                        } else if ("subscribe".equals(action)) {
                            webSocket.send(ByteString.encodeUtf8(
                                    "{\"action\":\"error\",\"code\":\"LIMIT\",\"message\":\"subscription rejected\","
                                            + "\"channels\":[{\"name\":\"tick.G1.json\",\"symbols\":[\"FPT\"]}]}"
                            ));
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

            try (DnseWebSocketClient client = new DnseWebSocketClient(config(server))) {
                client.onError(error -> {
                    if (error instanceof DnseSubscriptionException subscriptionError && subscriptionError.serverReported()) {
                        receivedError.set(subscriptionError);
                        serverErrorReceived.countDown();
                    }
                });

                client.connect().get(2, TimeUnit.SECONDS);
                SubscriptionResult result = client.subscribeTradesAsync(List.of("FPT"), "G1")
                        .get(1, TimeUnit.SECONDS);

                assertEquals(SubscriptionConfirmation.TRANSPORT_ACCEPTED, result.confirmation());
                assertEquals("tick.G1.json", result.subscription().channel());
                assertTrue(serverErrorReceived.await(1, TimeUnit.SECONDS));
                assertEquals("LIMIT", receivedError.get().errorCode());
                assertEquals("tick.G1.json", receivedError.get().channel());
                assertEquals(List.of("FPT"), receivedError.get().symbols());
                assertTrue(receivedError.get().serverReported());
            }
        }
    }

    private static DnseWebSocketConfig config(MockWebServer server) {
        return DnseWebSocketConfig.builder()
                .apiKey("test-key")
                .apiSecret("test-secret")
                .baseUrl(webSocketBaseUrl(server))
                .encoding(MessageEncoding.JSON)
                .connectTimeout(Duration.ofSeconds(2))
                .heartbeatInterval(Duration.ZERO)
                .reconnectPolicy(new ReconnectPolicy(false, 0, Duration.ofMillis(10), Duration.ofMillis(10)))
                .clock(Clock.fixed(Instant.ofEpochSecond(1_720_000_000L), ZoneOffset.UTC))
                .nonceGenerator(() -> "1720000000123456")
                .build();
    }

    private static String webSocketBaseUrl(MockWebServer server) {
        String httpUrl = server.url("/").toString();
        return httpUrl.replaceFirst("^http", "ws").replaceAll("/$", "");
    }
}
