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
import vn.dnse.openapi.websocket.dispatcher.StripedEventExecutor;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DnseWebSocketHardeningTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void unlimitedReconnectPolicyKeepsAllowingRetriesAndCapsDelay() {
        ReconnectPolicy policy = ReconnectPolicy.forever(
                Duration.ofMillis(10),
                Duration.ofMillis(80)
        );

        assertTrue(policy.enabled());
        assertTrue(policy.unlimited());
        assertTrue(policy.canRetry(0));
        assertTrue(policy.canRetry(10_000));
        assertEquals(Duration.ofMillis(80), policy.delayForAttempt(100));
    }

    @Test
    void handshakeTimeoutFailsAConnectionThatNeverSendsWelcome() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
                @Override
                public void onClosing(WebSocket webSocket, int code, String reason) {
                    webSocket.close(code, reason);
                }
            }));
            server.start();

            DnseWebSocketConfig config = baseConfig(server)
                    .handshakeTimeout(Duration.ofMillis(50))
                    .reconnectPolicy(disabledReconnect())
                    .initialConnectionPolicy(InitialConnectionPolicy.FAIL_FAST)
                    .build();

            try (DnseWebSocketClient client = new DnseWebSocketClient(config)) {
                assertThrows(ExecutionException.class, () -> client.connect().get(1, TimeUnit.SECONDS));
                assertEquals(ConnectionState.DISCONNECTED, client.state());
                assertFalse(client.isReady());
            }
        }
    }

    @Test
    void reconnectsOnGoingAwayAndBecomesReadyAfterSubscriptionRestore() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            CountDownLatch firstSubscription = new CountDownLatch(1);
            CountDownLatch restoredSubscription = new CountDownLatch(1);
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
                            webSocket.send(ByteString.encodeUtf8("{\"action\":\"auth_success\"}"));
                        } else if ("subscribe".equals(action)) {
                            firstSubscription.countDown();
                            webSocket.close(1001, "gateway going away");
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
                            webSocket.send(ByteString.encodeUtf8("{\"action\":\"auth_success\"}"));
                        } else if ("subscribe".equals(action)) {
                            JsonNode channel = message.path("channels").path(0);
                            if ("tick_extra.G1.json".equals(channel.path("name").asText())
                                    && channel.path("symbols").toString().contains("FPT")) {
                                restoredSubscription.countDown();
                            }
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

            DnseWebSocketConfig config = baseConfig(server)
                    .reconnectPolicy(new ReconnectPolicy(
                            true,
                            3,
                            Duration.ofMillis(10),
                            Duration.ofMillis(50)
                    ))
                    .build();

            try (DnseWebSocketClient client = new DnseWebSocketClient(config)) {
                AtomicBoolean sawReconnectNotReady = new AtomicBoolean();
                client.onStateChanged(event -> {
                    if (event.current() == ConnectionState.RECONNECTING && !client.isReady()) {
                        sawReconnectNotReady.set(true);
                    }
                });

                client.connect().get(2, TimeUnit.SECONDS);
                assertTrue(client.isReady());

                client.subscribeTradeExtra(List.of("FPT"), "G1");
                assertTrue(firstSubscription.await(1, TimeUnit.SECONDS));
                assertTrue(restoredSubscription.await(3, TimeUnit.SECONDS));

                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
                while (!client.isReady() && System.nanoTime() < deadline) {
                    Thread.sleep(5);
                }

                assertTrue(sawReconnectNotReady.get());
                assertTrue(client.subscriptionsReady());
                assertFalse(client.subscriptionRestoreInProgress());
                assertTrue(client.isReady());
                assertEquals("session-2", client.sessionId());
                assertNull(client.lastSubscriptionRestoreError());
                assertNull(serverError.get());
            }
        }
    }

    @Test
    void bulkSubscribesAndReconcilesTradeExtraUniverse() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            List<WireOperation> operations = Collections.synchronizedList(new ArrayList<>());
            AtomicReference<Throwable> serverError = new AtomicReference<>();

            server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    webSocket.send(ByteString.encodeUtf8("{\"session_id\":\"session-extra\"}"));
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
                            operations.add(new WireOperation(
                                    action,
                                    channel.path("name").asText(),
                                    List.copyOf(symbols)
                            ));
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

            try (DnseWebSocketClient client = new DnseWebSocketClient(
                    baseConfig(server).reconnectPolicy(disabledReconnect()).build()
            )) {
                client.connect().get(2, TimeUnit.SECONDS);
                SubscriptionOptions options = SubscriptionOptions.builder().batchSize(2).build();

                client.subscribeTradeExtra(
                        Map.of("G1", List.of("FPT", "VNM", "HPG")),
                        options
                );

                Map<String, List<String>> desired = new LinkedHashMap<>();
                desired.put("G1", List.of("FPT", "HPG", "SSI"));
                desired.put("G3", List.of("VCB"));

                SubscriptionReconciliationResult result =
                        client.reconcileTradeExtra(desired, options);

                assertEquals(4, result.desiredSymbols());
                assertEquals(2, result.addedSymbols());
                assertEquals(1, result.removedSymbols());
                assertEquals(2, result.unchangedSymbols());
                assertEquals(2, result.subscribeOperations());
                assertEquals(1, result.unsubscribeOperations());

                SubscriptionReconciliationResult converged =
                        client.reconcileTradeExtra(desired, options);

                assertEquals(0, converged.addedSymbols());
                assertEquals(0, converged.removedSymbols());
                assertEquals(0, converged.subscribeOperations());
                assertEquals(0, converged.unsubscribeOperations());

                waitForOperations(operations, 5);
                assertTrue(operations.contains(
                        new WireOperation("subscribe", "tick_extra.G1.json", List.of("SSI"))
                ));
                assertTrue(operations.contains(
                        new WireOperation("unsubscribe", "tick_extra.G1.json", List.of("VNM"))
                ));
                assertTrue(operations.contains(
                        new WireOperation("subscribe", "tick_extra.G3.json", List.of("VCB"))
                ));
                assertNull(serverError.get());
            }
        }
    }

    @Test
    void gracefulDispatcherCloseDrainsAlreadyQueuedCallbacks() throws Exception {
        StripedEventExecutor executor = new StripedEventExecutor(1, 4);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondProcessed = new CountDownLatch(1);

        executor.execute("FPT", () -> {
            try {
                releaseFirst.await(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        executor.execute("FPT", secondProcessed::countDown);

        Thread releaser = new Thread(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            releaseFirst.countDown();
        }, "dispatcher-test-releaser");
        releaser.start();

        executor.close();

        assertTrue(secondProcessed.await(100, TimeUnit.MILLISECONDS));
        releaser.join(500);
    }

    private static DnseWebSocketConfig.Builder baseConfig(MockWebServer server) {
        return DnseWebSocketConfig.builder()
                .apiKey("test-key")
                .apiSecret("test-secret")
                .baseUrl(webSocketBaseUrl(server))
                .encoding(MessageEncoding.JSON)
                .connectTimeout(Duration.ofMillis(300))
                .handshakeTimeout(Duration.ofMillis(300))
                .heartbeatInterval(Duration.ZERO)
                .initialConnectionPolicy(InitialConnectionPolicy.FAIL_FAST)
                .clock(Clock.fixed(Instant.ofEpochSecond(1_720_000_000L), ZoneOffset.UTC))
                .nonceGenerator(() -> "1720000000123456");
    }

    private static ReconnectPolicy disabledReconnect() {
        return new ReconnectPolicy(false, 0, Duration.ofMillis(10), Duration.ofMillis(10));
    }

    private static void waitForOperations(List<WireOperation> operations, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (operations.size() < expected && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertTrue(
                operations.size() >= expected,
                "expected at least " + expected + " wire operations but got " + operations.size()
        );
    }

    private static String webSocketBaseUrl(MockWebServer server) {
        String httpUrl = server.url("/").toString();
        return httpUrl.replaceFirst("^http", "ws").replaceAll("/$", "");
    }

    private record WireOperation(String action, String channel, List<String> symbols) {
    }
}
