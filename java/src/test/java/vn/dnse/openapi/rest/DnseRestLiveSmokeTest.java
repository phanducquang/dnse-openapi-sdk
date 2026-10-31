package vn.dnse.openapi.rest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opt-in live DNSE REST smoke test. Normal CI never enables this test because real credentials are
 * intentionally kept out of the repository/workflow environment.
 */
@EnabledIfEnvironmentVariable(named = "DNSE_REST_LIVE_TEST", matches = "(?i)true")
class DnseRestLiveSmokeTest {
    @Test
    void fetchesInstrumentFromLiveGateway() {
        String apiKey = requiredEnv("DNSE_API_KEY");
        String apiSecret = requiredEnv("DNSE_API_SECRET");
        String baseUrl = envOrDefault(
                "DNSE_REST_BASE_URL",
                DnseRestConfig.DEFAULT_BASE_URL
        );
        String apiVersion = envOrDefault(
                "DNSE_API_VERSION",
                DnseRestConfig.DEFAULT_API_VERSION
        );
        String symbol = envOrDefault("DNSE_TEST_SYMBOL", "FPT");

        DnseRestConfig config = DnseRestConfig.builder()
                .apiKey(apiKey)
                .apiSecret(apiSecret)
                .baseUrl(baseUrl)
                .apiVersion(apiVersion)
                .build();

        try (DnseRestClient client = new DnseRestClient(config)) {
            DnseRestResponse response = client.getInstruments(
                    symbol,
                    null,
                    null,
                    null,
                    1,
                    1
            );

            assertNotNull(response.statusCode());
            assertTrue(
                    response.statusCode() >= 200 && response.statusCode() < 300,
                    () -> "unexpected live status=" + response.statusCode()
                            + " body=" + response.body()
            );
            assertNotNull(response.body());
        }
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
