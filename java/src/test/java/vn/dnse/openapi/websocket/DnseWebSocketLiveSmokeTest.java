package vn.dnse.openapi.websocket;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag("live")
@EnabledIfEnvironmentVariable(named = "DNSE_LIVE_TEST", matches = "true")
class DnseWebSocketLiveSmokeTest {

    @Test
    void connectsAuthenticatesAndSendsMarketDataSubscription() throws Exception {
        String apiKey = requiredEnv("DNSE_API_KEY");
        String apiSecret = requiredEnv("DNSE_API_SECRET");
        String baseUrl = envOrDefault("DNSE_WS_BASE_URL", "wss://ws-openapi.dnse.com.vn");
        String symbol = envOrDefault("DNSE_TEST_SYMBOL", "FPT");
        String board = envOrDefault("DNSE_TEST_BOARD", "G1");

        DnseWebSocketConfig config = DnseWebSocketConfig.builder()
                .apiKey(apiKey)
                .apiSecret(apiSecret)
                .baseUrl(baseUrl)
                .encoding(MessageEncoding.JSON)
                .connectTimeout(Duration.ofSeconds(30))
                .heartbeatInterval(Duration.ofSeconds(25))
                .reconnectPolicy(new ReconnectPolicy(false, 0, Duration.ofSeconds(1), Duration.ofSeconds(1)))
                .build();

        try (DnseWebSocketClient client = new DnseWebSocketClient(config)) {
            client.connect().get(45, TimeUnit.SECONDS);
            assertEquals(ConnectionState.AUTHENTICATED, client.state());
            assertNotNull(client.sessionId());
            client.subscribeTrades(List.of(symbol), board);
        }
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be configured for live smoke tests");
        }
        return value;
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
