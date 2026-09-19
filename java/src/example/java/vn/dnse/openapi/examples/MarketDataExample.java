package vn.dnse.openapi.examples;

import vn.dnse.openapi.websocket.ConnectionState;
import vn.dnse.openapi.websocket.DnseWebSocketClient;
import vn.dnse.openapi.websocket.DnseWebSocketConfig;
import vn.dnse.openapi.websocket.InitialConnectionPolicy;
import vn.dnse.openapi.websocket.MessageEncoding;
import vn.dnse.openapi.websocket.ReconnectPolicy;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Executable long-running market-data example. It connects/authenticates, subscribes trades,
 * automatically retries startup/runtime disconnects and prints realtime events until Ctrl+C.
 *
 * <p>Required environment variables: {@code DNSE_API_KEY}, {@code DNSE_API_SECRET}.</p>
 * <p>Optional: {@code DNSE_WS_BASE_URL}, {@code DNSE_SYMBOLS}, {@code DNSE_BOARD},
 * {@code DNSE_ENCODING}, {@code DNSE_INITIAL_CONNECTION_POLICY}.</p>
 */
public final class MarketDataExample {
    private MarketDataExample() {
    }

    public static void main(String[] args) throws Exception {
        String apiKey = requiredEnv("DNSE_API_KEY");
        String apiSecret = requiredEnv("DNSE_API_SECRET");
        String baseUrl = envOrDefault("DNSE_WS_BASE_URL", "wss://ws-openapi.dnse.com.vn");
        String boardId = envOrDefault("DNSE_BOARD", envOrDefault("DNSE_TEST_BOARD", "G1"));
        String symbolValue = envOrDefault("DNSE_SYMBOLS", envOrDefault("DNSE_TEST_SYMBOL", "FPT"));
        MessageEncoding encoding = parseEncoding(envOrDefault("DNSE_ENCODING", "JSON"));
        InitialConnectionPolicy initialConnectionPolicy = parseInitialConnectionPolicy(
                envOrDefault("DNSE_INITIAL_CONNECTION_POLICY", "RETRY")
        );
        List<String> symbols = parseSymbols(symbolValue);

        DnseWebSocketConfig config = DnseWebSocketConfig.builder()
                .apiKey(apiKey)
                .apiSecret(apiSecret)
                .baseUrl(baseUrl)
                .encoding(encoding)
                .connectTimeout(Duration.ofSeconds(30))
                .handshakeTimeout(Duration.ofSeconds(30))
                .heartbeatInterval(Duration.ofSeconds(25))
                .initialConnectionPolicy(initialConnectionPolicy)
                .reconnectPolicy(ReconnectPolicy.forever(
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(60)
                ))
                .build();

        DnseWebSocketClient client = new DnseWebSocketClient(config);
        CountDownLatch shutdownLatch = new CountDownLatch(1);
        AtomicBoolean closed = new AtomicBoolean(false);

        Runnable closeClient = () -> {
            if (!closed.compareAndSet(false, true)) return;
            try {
                client.close();
            } catch (Exception e) {
                System.err.println("Failed to close DNSE WebSocket client: " + e.getMessage());
            } finally {
                shutdownLatch.countDown();
            }
        };

        Thread shutdownHook = new Thread(() -> {
            System.out.println("\nStopping DNSE market-data example...");
            closeClient.run();
        }, "dnse-market-data-example-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        client.onStateChanged(event -> System.out.printf(
                "[%s] STATE %s -> %s sessionId=%s%s%n",
                event.timestamp(),
                event.previous(),
                event.current(),
                event.sessionId(),
                event.cause() == null ? "" : " cause=" + event.cause().getMessage()
        ));

        client.onTrade(trade -> System.out.printf(
                "[%s] TRADE symbol=%s board=%s price=%s quantity=%d totalVolume=%d time=%s%n",
                Instant.now(),
                trade.symbol(),
                trade.boardId(),
                trade.price(),
                trade.quantity(),
                trade.totalVolumeTraded(),
                trade.time()
        ));

        client.onError(error -> {
            System.err.println("DNSE WebSocket error: " + error.getMessage());
            error.printStackTrace(System.err);
        });

        try {
            System.out.printf(
                    "Connecting to %s using %s, initialPolicy=%s...%n",
                    baseUrl,
                    encoding.wireName(),
                    initialConnectionPolicy
            );

            // In RETRY + forever mode this completes after authentication succeeds or the client is closed.
            client.connect().join();

            if (client.state() != ConnectionState.AUTHENTICATED) {
                throw new IllegalStateException("WebSocket authentication did not reach AUTHENTICATED state");
            }

            System.out.printf("Connected and authenticated. sessionId=%s%n", client.sessionId());
            client.subscribeTrades(symbols, boardId);
            System.out.printf("Subscribed to trades. symbols=%s board=%s%n", symbols, boardId);
            System.out.println("Waiting for realtime trades. Press Ctrl+C to stop.");

            shutdownLatch.await();
        } finally {
            closeClient.run();
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException ignored) {
                // JVM is already shutting down.
            }
        }
    }

    private static MessageEncoding parseEncoding(String value) {
        try {
            return MessageEncoding.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("DNSE_ENCODING must be JSON or MSGPACK", e);
        }
    }

    private static InitialConnectionPolicy parseInitialConnectionPolicy(String value) {
        try {
            return InitialConnectionPolicy.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "DNSE_INITIAL_CONNECTION_POLICY must be FAIL_FAST or RETRY",
                    e
            );
        }
    }

    private static List<String> parseSymbols(String value) {
        List<String> symbols = Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(symbol -> !symbol.isBlank())
                .distinct()
                .toList();
        if (symbols.isEmpty()) throw new IllegalArgumentException("DNSE_SYMBOLS must contain at least one symbol");
        return symbols;
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " environment variable is required");
        }
        return value;
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
