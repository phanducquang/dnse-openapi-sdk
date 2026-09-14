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

/**
 * Minimal executable example that connects to DNSE market data, authenticates, subscribes trades
 * and prints realtime events until Ctrl+C.
 *
 * <p>Required environment variables: {@code DNSE_API_KEY}, {@code DNSE_API_SECRET}.</p>
 * <p>Optional: {@code DNSE_WS_BASE_URL}, {@code DNSE_SYMBOLS}, {@code DNSE_BOARD}, {@code DNSE_ENCODING}.</p>
 */
public final class MarketDataExample {
    private MarketDataExample() {
    }

    /** Application entry point used by the Gradle {@code run} task. */
    public static void main(String[] args) throws Exception {
        // Credentials are intentionally read from the environment so secrets never need to be committed.
        String apiKey = requiredEnv("DNSE_API_KEY");
        String apiSecret = requiredEnv("DNSE_API_SECRET");

        // Runtime overrides make the example usable against production or a compatible test gateway.
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
        // Keeps main alive without busy-waiting until the shutdown hook closes the client.
        CountDownLatch shutdownLatch = new CountDownLatch(1);
        // Guarantees close() executes only once even when finally and the JVM shutdown hook race.
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

        // Ctrl+C triggers a graceful WebSocket close instead of abruptly abandoning the connection.
        Thread shutdownHook = new Thread(() -> {
            System.out.println("\nStopping DNSE market-data example...");
            closeClient.run();
        }, "dnse-market-data-example-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        // Trade callbacks execute asynchronously on the client's striped event dispatcher.
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

            // connect().join() returns only after the DNSE welcome/authentication exchange succeeds.
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
                // JVM is already shutting down, so the hook can no longer be removed safely.
            }
        }
    }

    /** Parses JSON/MSGPACK environment input into the SDK enum. */
    private static MessageEncoding parseEncoding(String value) {
        try {
            return MessageEncoding.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("DNSE_ENCODING must be JSON or MSGPACK", e);
        }
    }

    /**
     * Parses comma-separated symbols, removes blanks and preserves first-occurrence order after
     * de-duplication. DNSE requires callers to provide symbols in uppercase (for example FPT or HPG).
     */
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

    /** Reads one mandatory environment variable and rejects missing/blank secrets. */
    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " environment variable is required");
        }
        return value;
    }

    /** Returns an environment value when non-blank, otherwise a caller-supplied default. */
    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
