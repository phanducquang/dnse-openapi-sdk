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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DnseRestClientMarketDataTest {
    @Test
    void dryRunBuildsSignedInstrumentRequestWithoutNetworkCall() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();

            try (DnseRestClient client = new DnseRestClient(config(server))) {
                DnseRestResponse response = client.getInstruments(
                        "FPT",
                        "HOSE",
                        "STOCK",
                        "VN30",
                        50,
                        2,
                        "2026-05-07",
                        true
                );

                assertTrue(response.dryRun());
                assertNull(response.statusCode());
                assertNull(response.body());

                RestRequestPreview preview = response.requestPreview();
                assertNotNull(preview);
                assertEquals("GET", preview.method());
                assertEquals(
                        server.url("/").toString().replaceAll("/$", "")
                                + "/market/instruments"
                                + "?symbol=FPT&marketId=HOSE&securityGroupId=STOCK&indexName=VN30&limit=50&page=2",
                        preview.url()
                );
                assertEquals(
                        "Thu, 23 Jul 2026 10:11:12 +0000",
                        preview.headers().get("Date")
                );
                assertEquals("test-key", preview.headers().get("x-api-key"));
                assertEquals("2026-05-07", preview.headers().get("version"));
                assertEquals(
                        "Signature keyId=\"test-key\",algorithm=\"hmac-sha256\","
                                + "headers=\"(request-target) date\","
                                + "signature=\"t%2FcEoFNqZ6dUA2pV6N3yCwCE87QGMHdEI0sWMQ%2Bj4PE%3D\","
                                + "nonce=\"0123456789abcdef0123456789abcdef\"",
                        preview.headers().get("X-Signature")
                );
                assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS));
            }
        }
    }

    @Test
    void executesRequestAndReturnsRawStatusAndBody() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody("{\"symbol\":\"FPT\",\"matchPrice\":123.45}"));
            server.start();

            try (DnseRestClient client = new DnseRestClient(config(server))) {
                DnseRestResponse response = client.getLatestTrade("FPT", "G1");

                assertFalse(response.dryRun());
                assertTrue(response.successful());
                assertEquals(200, response.statusCode());
                assertEquals(
                        "{\"symbol\":\"FPT\",\"matchPrice\":123.45}",
                        response.body()
                );

                RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
                assertNotNull(request);
                assertEquals("GET", request.getMethod());
                assertEquals("/price/FPT/trades/latest?boardId=G1", request.getPath());
                assertEquals("test-key", request.getHeader("x-api-key"));
                assertEquals("2026-07-23", request.getHeader("version"));
                assertNotNull(request.getHeader("X-Signature"));
            }
        }
    }

    @Test
    void preservesErrorHttpStatusInsteadOfThrowing() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(404)
                    .setBody("{\"message\":\"not found\"}"));
            server.start();

            try (DnseRestClient client = new DnseRestClient(config(server))) {
                DnseRestResponse response = client.getClosePrice("UNKNOWN", null);

                assertEquals(404, response.statusCode());
                assertEquals("{\"message\":\"not found\"}", response.body());
                assertFalse(response.successful());
            }
        }
    }

    @Test
    void marketDataConvenienceMethodsMatchPythonPathsAndQueries() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();

            try (DnseRestClient client = new DnseRestClient(config(server))) {
                assertUrlEndsWith(
                        client.getSecurityDefinition("FPT", "G1", true),
                        "/price/FPT/secdef?boardId=G1"
                );

                LinkedHashMap<String, Object> ohlcQuery = new LinkedHashMap<>();
                ohlcQuery.put("symbol", "FPT");
                ohlcQuery.put("from", "2026-09-01");
                assertUrlEndsWith(
                        client.getOhlc("1D", ohlcQuery, true),
                        "/price/ohlc?symbol=FPT&from=2026-09-01&type=1D"
                );

                assertUrlEndsWith(
                        client.getTrades(
                                "FPT", "G1", "2026-09-01", "2026-09-19",
                                100, "desc", "next-token", true
                        ),
                        "/price/FPT/trades?boardId=G1&from=2026-09-01&to=2026-09-19"
                                + "&limit=100&order=desc&nextPageToken=next-token"
                );

                assertUrlEndsWith(
                        client.getTradesVolumeProfile(
                                "FPT", "2026-09-01", "2026-09-19", "G1", true
                        ),
                        "/price/FPT/trades/volume-profile"
                                + "?from=2026-09-01&to=2026-09-19&board_id=G1"
                );

                assertUrlEndsWith(
                        client.getExpectedPrice(
                                "FPT", "G1", null, null, 10, null, null, true
                        ),
                        "/price/FPT/expected-price?boardId=G1&limit=10"
                );

                assertUrlEndsWith(
                        client.getQuotes(
                                "FPT", "G1", null, null, 10, "asc", null, true
                        ),
                        "/price/FPT/quotes?boardId=G1&limit=10&order=asc"
                );

                assertUrlEndsWith(
                        client.getForeignTrading(
                                "FPT", "G1", null, null, null, null, null, true
                        ),
                        "/price/FPT/foreign-trading?boardId=G1"
                );

                assertUrlEndsWith(
                        client.getMarketIndex(
                                "VNINDEX", "2026-09-01", "2026-09-19",
                                50, "desc", null, true
                        ),
                        "/price/VNINDEX/market-index"
                                + "?from=2026-09-01&to=2026-09-19&limit=50&order=desc"
                );

                assertUrlEndsWith(
                        client.getLatestQuote("FPT", "G1", true),
                        "/price/FPT/quotes/latest?boardId=G1"
                );

                assertUrlEndsWith(
                        client.getClosePrice("FPT", "G1", true),
                        "/price/FPT/close?boardId=G1"
                );

                assertUrlEndsWith(
                        client.getWorkingDates(true),
                        "/market/working-dates"
                );

                assertUrlEndsWith(
                        client.getLatestSession("EQUITY", "G1", true),
                        "/market/trading-session?tscProdGrpId=EQUITY&boardId=G1"
                );

                assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS));
            }
        }
    }

    @Test
    void queryStringIsNotPartOfSignature() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();

            try (DnseRestClient client = new DnseRestClient(config(server))) {
                DnseRestResponse fpt = client.getInstruments(
                        "FPT", null, null, null, null, null,
                        null, true
                );
                DnseRestResponse hpg = client.getInstruments(
                        "HPG", null, null, null, null, null,
                        null, true
                );

                assertEquals(
                        fpt.requestPreview().headers().get("X-Signature"),
                        hpg.requestPreview().headers().get("X-Signature")
                );
            }
        }
    }

    private static void assertUrlEndsWith(DnseRestResponse response, String expectedSuffix) {
        assertTrue(response.dryRun());
        assertTrue(
                response.requestPreview().url().endsWith(expectedSuffix),
                () -> "expected URL suffix " + expectedSuffix
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
