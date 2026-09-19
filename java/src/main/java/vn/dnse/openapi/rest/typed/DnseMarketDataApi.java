package vn.dnse.openapi.rest.typed;

import com.fasterxml.jackson.databind.ObjectMapper;
import vn.dnse.openapi.rest.DnseRestClient;
import vn.dnse.openapi.rest.DnseRestConfig;
import vn.dnse.openapi.rest.DnseRestException;
import vn.dnse.openapi.rest.DnseRestResponse;

import java.util.Objects;

/**
 * Typed convenience layer over the raw {@link DnseRestClient}.
 *
 * <p>The raw client remains the wire-compatibility layer. This class adds decoded domain objects
 * without changing any existing REST method signature.</p>
 */
public final class DnseMarketDataApi implements AutoCloseable {
    private final DnseRestClient rawClient;
    private final InstrumentResponseParser instrumentParser;
    private final boolean ownsClient;

    public DnseMarketDataApi(DnseRestConfig config) {
        this(new DnseRestClient(config), true, new ObjectMapper());
    }

    public DnseMarketDataApi(DnseRestClient rawClient) {
        this(rawClient, false, new ObjectMapper());
    }

    DnseMarketDataApi(
            DnseRestClient rawClient,
            boolean ownsClient,
            ObjectMapper objectMapper
    ) {
        this.rawClient = Objects.requireNonNull(rawClient, "rawClient");
        this.ownsClient = ownsClient;
        this.instrumentParser = new InstrumentResponseParser(
                Objects.requireNonNull(objectMapper, "objectMapper")
        );
    }

    public DnseRestClient rawClient() {
        return rawClient;
    }

    public InstrumentListResponse getInstruments(
            String symbol,
            String marketId,
            String securityGroupId,
            String indexName,
            Integer limit,
            Integer page
    ) {
        return getInstruments(
                symbol,
                marketId,
                securityGroupId,
                indexName,
                limit,
                page,
                null
        );
    }

    public InstrumentListResponse getInstruments(
            String symbol,
            String marketId,
            String securityGroupId,
            String indexName,
            Integer limit,
            Integer page,
            String version
    ) {
        DnseRestResponse response = rawClient.getInstruments(
                symbol,
                marketId,
                securityGroupId,
                indexName,
                limit,
                page,
                version,
                false
        );
        requireSuccess(response);
        return instrumentParser.parse(response.body());
    }

    private static void requireSuccess(DnseRestResponse response) {
        if (response.statusCode() == null) {
            throw new DnseRestException("Typed REST API requires a live HTTP response");
        }
        if (!response.successful()) {
            throw new DnseRestHttpException(response.statusCode(), response.body());
        }
    }

    @Override
    public void close() {
        if (ownsClient) rawClient.close();
    }
}
