package vn.dnse.openapi.rest.typed;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import vn.dnse.openapi.rest.DnseRestClient;
import vn.dnse.openapi.rest.DnseRestConfig;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DnseMarketDataApiTest {
    @Test
    void parsesTopLevelInstrumentArray() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody("""
                            [
                              {
                                "symbol":"FPT",
                                "isin":"VN000000FPT1",
                                "symbolName":"FPT Corporation",
                                "marketId":"STO",
                                "boardId":"G1",
                                "securityGroupId":"ST",
                                "referencePrice":123.4,
                                "highLimitPrice":132.0,
                                "lowLimitPrice":115.0
                              }
                            ]
                            """));
            server.start();

            try (DnseMarketDataApi api = new DnseMarketDataApi(config(server))) {
                InstrumentListResponse result = api.getInstruments(
                        "FPT", null, null, null, 20, 1
                );

                assertEquals(1, result.instruments().size());
                Instrument instrument = result.instruments().get(0);
                assertEquals("FPT", instrument.symbol());
                assertEquals("G1", instrument.boardId());
                assertEquals("STO", instrument.marketId());
                assertEquals("ST", instrument.securityGroupId());
                assertEquals("123.4", instrument.referencePrice().toPlainString());
                assertNotNull(result.rawRoot());
            }
        }
    }

    @Test
    void parsesNestedDataItemsAndPaginationMetadata() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody("""
                            {
                              "data": {
                                "items": [
                                  {
                                    "symbol":"HPG",
                                    "marketId":6,
                                    "boardId":2,
                                    "boardIdOriginal":"G1",
                                    "productGrpId":"STO",
                                    "securityGroupId":7,
                                    "securityStatus":"NO_HALT",
                                    "tradingSessionId":6,
                                    "totalVolumeTraded":123456
                                  }
                                ],
                                "page": 2,
                                "limit": 100,
                                "total": 2450,
                                "totalPages": 25,
                                "nextPageToken": "next-3"
                              }
                            }
                            """));
            server.start();

            try (DnseMarketDataApi api = new DnseMarketDataApi(config(server))) {
                InstrumentListResponse result = api.getInstruments(
                        null, null, null, null, 100, 2
                );

                assertEquals(1, result.instruments().size());
                Instrument instrument = result.instruments().get(0);
                assertEquals("HPG", instrument.symbol());
                assertEquals("6", instrument.marketId());
                assertEquals("2", instrument.boardId());
                assertEquals("7", instrument.securityGroupId());
                assertEquals("6", instrument.tradingSessionId());
                assertEquals(123456L, instrument.totalVolumeTraded());

                assertEquals(2, result.page());
                assertEquals(100, result.limit());
                assertEquals(2450L, result.total());
                assertEquals(25, result.totalPages());
                assertEquals("next-3", result.nextPageToken());
            }
        }
    }

    @Test
    void preservesRawHttpErrorInTypedException() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(401)
                    .setBody("{\"code\":\"UNAUTHORIZED\",\"message\":\"invalid signature\"}"));
            server.start();

            try (DnseMarketDataApi api = new DnseMarketDataApi(config(server))) {
                DnseRestHttpException error = assertThrows(
                        DnseRestHttpException.class,
                        () -> api.getInstruments(
                                null, null, null, null, 20, 1
                        )
                );

                assertEquals(401, error.statusCode());
                assertEquals(
                        "{\"code\":\"UNAUTHORIZED\",\"message\":\"invalid signature\"}",
                        error.responseBody()
                );
            }
        }
    }

    @Test
    void wrapperAroundExistingRawClientDoesNotOwnItsLifecycle() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(200).setBody("[]"));
            server.enqueue(new MockResponse().setResponseCode(200).setBody("[]"));
            server.start();

            try (DnseRestClient raw = new DnseRestClient(config(server))) {
                DnseMarketDataApi typed = new DnseMarketDataApi(raw);
                typed.getInstruments(null, null, null, null, 10, 1);
                typed.close();

                // The caller-provided raw client is still usable after closing only the typed wrapper.
                assertEquals(
                        200,
                        raw.getInstruments(null, null, null, null, 10, 1).statusCode()
                );
            }
        }
    }

    private static DnseRestConfig config(MockWebServer server) {
        return DnseRestConfig.builder()
                .apiKey("test-key")
                .apiSecret("test-secret")
                .baseUrl(server.url("/").toString())
                .connectTimeout(Duration.ofSeconds(1))
                .readTimeout(Duration.ofSeconds(1))
                .build();
    }
}
