package vn.dnse.openapi.rest;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DnseRestClientAccountAndWriteTest {
    @Test
    void accountReadMethodsMatchPythonPathsAndQueries() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();

            try (DnseRestClient client = new DnseRestClient(config(server))) {
                assertPreview(client.getAccounts(true), "GET", "/accounts");
                assertPreview(
                        client.getBalances("000123", true),
                        "GET",
                        "/accounts/000123/balances"
                );
                assertPreview(
                        client.getLoanPackages("000123", "STOCK", "FPT", true),
                        "GET",
                        "/accounts/000123/loan-packages?marketType=STOCK&symbol=FPT"
                );
                assertPreview(
                        client.getPositions("000123", "STOCK", true),
                        "GET",
                        "/accounts/000123/positions?marketType=STOCK"
                );
                assertPreview(
                        client.getPositionById("STOCK", "77", "2026-05-07", true),
                        "GET",
                        "/positions/77?marketType=STOCK"
                );
                assertPreview(
                        client.getPositionPnlConfigs("DERIVATIVE", "77", null, true),
                        "GET",
                        "/positions/77/pnl-configs?marketType=DERIVATIVE"
                );
                assertPreview(
                        client.getOrders("000123", "STOCK", "NORMAL", 1, 50, true),
                        "GET",
                        "/accounts/000123/orders"
                                + "?marketType=STOCK&orderCategory=NORMAL&pageIndex=1&pageSize=50"
                );
                assertPreview(
                        client.getOrderDetail(
                                "000123", "order-1", "STOCK", "NORMAL", true
                        ),
                        "GET",
                        "/accounts/000123/orders/order-1"
                                + "?marketType=STOCK&orderCategory=NORMAL"
                );
                assertPreview(
                        client.getExecutionDetail(
                                "000123", "order-1", "STOCK", "NORMAL", true
                        ),
                        "GET",
                        "/accounts/000123/executions/order-1"
                                + "?marketType=STOCK&orderCategory=NORMAL"
                );
                assertPreview(
                        client.getOrderHistory(
                                "000123", "STOCK",
                                "2026-09-01", "2026-09-19",
                                100, 2, true
                        ),
                        "GET",
                        "/accounts/000123/orders/history"
                                + "?marketType=STOCK&from=2026-09-01&to=2026-09-19"
                                + "&pageSize=100&pageIndex=2"
                );
                assertPreview(
                        client.getCorporateActionHistory(
                                "000123", "FPT", "DIVIDEND", "DONE",
                                1, 50, true
                        ),
                        "GET",
                        "/accounts/000123/corporate-action-history"
                                + "?symbol=FPT&caType=DIVIDEND&caStatus=DONE&pageIndex=1&pageSize=50"
                );
                assertPreview(
                        client.getPpse(
                                "000123", "STOCK", "FPT", "123.45", 10, true
                        ),
                        "GET",
                        "/accounts/000123/ppse"
                                + "?marketType=STOCK&symbol=FPT&price=123.45&loanPackageId=10"
                );
                assertPreview(
                        client.getListCareBy(true),
                        "GET",
                        "/brokers/accounts/care-by"
                );
            }
        }
    }

    @Test
    void writeApisBuildExpectedWireContractWithoutLiveExecution() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();

            try (DnseRestClient client = new DnseRestClient(config(server))) {
                DnseRestResponse pnlResponse = client.postPositionPnlConfigs(
                        "DERIVATIVE",
                        "77",
                        Map.of("configField", "value"),
                        "trade-token",
                        "2026-05-07",
                        true
                );
                assertPreview(
                        pnlResponse,
                        "POST",
                        "/positions/77/pnl-configs?marketType=DERIVATIVE"
                );
                assertEquals(
                        "trade-token",
                        pnlResponse.requestPreview().headers().get("trading-token")
                );
                assertEquals(
                        "2026-05-07",
                        pnlResponse.requestPreview().headers().get("version")
                );

                assertPreview(
                        client.sendEmailOtp(true),
                        "POST",
                        "/registration/send-email-otp"
                );

                DnseRestResponse tokenResponse =
                        client.createTradingToken("EMAIL", "000000", true);
                assertPreview(
                        tokenResponse,
                        "POST",
                        "/registration/trading-token"
                );
                assertEquals(
                        "{\"otpType\":\"EMAIL\",\"passcode\":\"000000\"}",
                        tokenResponse.requestPreview().body()
                );

                LinkedHashMap<String, Object> genericPayload = new LinkedHashMap<>();
                genericPayload.put("fieldA", "valueA");
                genericPayload.put("fieldB", 1);

                DnseRestResponse postOrder = client.postOrder(
                        "000123",
                        "STOCK",
                        genericPayload,
                        "trade-token",
                        "NORMAL",
                        "2026-07-23",
                        true
                );
                assertPreview(
                        postOrder,
                        "POST",
                        "/accounts/000123/orders"
                                + "?marketType=STOCK&orderCategory=NORMAL"
                );
                assertEquals(
                        "trade-token",
                        postOrder.requestPreview().headers().get("trading-token")
                );

                assertPreview(
                        client.replaceOrder(
                                "000123",
                                "order-1",
                                "STOCK",
                                Map.of("field", "value"),
                                "trade-token",
                                "NORMAL",
                                true
                        ),
                        "PUT",
                        "/accounts/000123/orders/order-1"
                                + "?marketType=STOCK&orderCategory=NORMAL"
                );

                assertPreview(
                        client.cancelOrder(
                                "000123",
                                "order-1",
                                "STOCK",
                                "trade-token",
                                "NORMAL",
                                true
                        ),
                        "DELETE",
                        "/accounts/000123/orders/order-1"
                                + "?marketType=STOCK&orderCategory=NORMAL"
                );

                assertPreview(
                        client.closePosition(
                                "77",
                                "DERIVATIVE",
                                "trade-token",
                                "2026-05-07",
                                true
                        ),
                        "POST",
                        "/positions/77/close?marketType=DERIVATIVE"
                );
            }
        }
    }

    @Test
    void postTransportSendsTradingTokenAndJsonBody() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody("{\"status\":\"ok\"}"));
            server.start();

            LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
            payload.put("fieldA", "valueA");
            payload.put("fieldB", 1);

            try (DnseRestClient client = new DnseRestClient(config(server))) {
                DnseRestResponse response = client.postOrder(
                        "000123",
                        "STOCK",
                        payload,
                        "trade-token",
                        "NORMAL",
                        null,
                        false
                );

                assertEquals(200, response.statusCode());

                RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
                assertNotNull(request);
                assertEquals("POST", request.getMethod());
                assertEquals(
                        "/accounts/000123/orders?marketType=STOCK&orderCategory=NORMAL",
                        request.getPath()
                );
                assertEquals("trade-token", request.getHeader("trading-token"));
                assertEquals("application/json", request.getHeader("Content-Type"));
                assertEquals(
                        "{\"fieldA\":\"valueA\",\"fieldB\":1}",
                        request.getBody().readUtf8()
                );
                assertNotNull(request.getHeader("X-Signature"));
            }
        }
    }

    private static void assertPreview(
            DnseRestResponse response,
            String method,
            String expectedPathAndQuery
    ) {
        assertTrue(response.dryRun());
        assertEquals(method, response.requestPreview().method());
        assertTrue(
                response.requestPreview().url().endsWith(expectedPathAndQuery),
                () -> "expected suffix " + expectedPathAndQuery
                        + " but got " + response.requestPreview().url()
        );
    }

    private static DnseRestConfig config(MockWebServer server) {
        return DnseRestConfig.builder()
                .apiKey("test-key")
                .apiSecret("test-secret")
                .baseUrl(server.url("/").toString())
                .connectTimeout(Duration.ofSeconds(1))
                .readTimeout(Duration.ofSeconds(1))
                .clock(Clock.fixed(
                        Instant.parse("2026-07-23T10:11:12Z"),
                        ZoneOffset.UTC
                ))
                .nonceGenerator(() -> "0123456789abcdef0123456789abcdef")
                .build();
    }
}
