package vn.dnse.openapi.examples;

import vn.dnse.openapi.websocket.ConnectionState;
import vn.dnse.openapi.websocket.DnseWebSocketClient;
import vn.dnse.openapi.websocket.DnseWebSocketConfig;
import vn.dnse.openapi.websocket.MessageEncoding;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

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
        List<String> symbols = parseSymbols(symbolValue);

        DnseWebSocketConfig config = DnseWebSocketConfig.builder()
                .apiKey(apiKey)
                .apiSecret(apiSecret)
                .baseUrl(baseUrl)
                .encoding(encoding)
                .connectTimeout(Duration.ofSeconds(30))
                .heartbeatInterval(Duration.ofSeconds(25))
                .build();

        DnseWebSocketClient client = new DnseWebSocketClient(config);
        CountDownLatch shutdownLatch = new CountDownLatch(1);
        AtomicBoolean closed = new AtomicBoolean(false);

        Runnable closeClient = () -> {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
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
                    "Connecting to %s using %s...%n",
                    baseUrl,
                    encoding.wireName()
            );

            client.connect().join();

            if (client.state() != ConnectionState.AUTHENTICATED) {
                throw new IllegalStateException("WebSocket authentication did not reach AUTHENTICATED state");
            }

            System.out.printf(
                    "Connected and authenticated. sessionId=%s%n",
                    client.sessionId()
            );

            client.subscribeTrades(symbols, boardId);

            System.out.printf(
                    "Subscribed to trades. symbols=%s board=%s%n",
                    symbols,
                    boardId
            );
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

    private static List<String> parseSymbols(String value) {
        List<String> symbols = Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(symbol -> !symbol.isBlank())
                .distinct()
                .toList();

        if (symbols.isEmpty()) {
            throw new IllegalArgumentException("DNSE_SYMBOLS must contain at least one symbol");
        }
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
