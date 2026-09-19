package vn.dnse.openapi.rest.typed;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Typed instrument response plus lightweight pagination metadata.
 *
 * @param instruments decoded instruments
 * @param page optional current page when present in the DNSE response
 * @param limit optional page size/limit when present
 * @param total optional total item count when present
 * @param totalPages optional total page count when present
 * @param nextPageToken optional continuation token when present
 * @param rawRoot complete decoded JSON root for fields not yet modeled
 */
public record InstrumentListResponse(
        List<Instrument> instruments,
        Integer page,
        Integer limit,
        Long total,
        Integer totalPages,
        String nextPageToken,
        JsonNode rawRoot
) {
    public InstrumentListResponse {
        instruments = List.copyOf(instruments);
    }
}
