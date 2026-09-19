package vn.dnse.openapi.rest.typed;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

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

    /**
     * Groups all instruments that have both a non-blank board id and symbol into a WebSocket-ready
     * universe. Symbols are deduplicated per board while preserving response order.
     */
    public Map<String, List<String>> symbolsByBoard() {
        return symbolsByBoard(instrument -> true);
    }

    /**
     * Groups a filtered subset of instruments by board for bulk WebSocket subscription.
     *
     * <p>The SDK deliberately does not guess which security statuses mean active/tradable. Callers
     * can supply a predicate after validating the live instrument status semantics.</p>
     */
    public Map<String, List<String>> symbolsByBoard(Predicate<Instrument> filter) {
        LinkedHashMap<String, LinkedHashSet<String>> grouped = new LinkedHashMap<>();
        for (Instrument instrument : instruments) {
            if (instrument == null || !filter.test(instrument)) continue;
            String boardId = instrument.boardId();
            String symbol = instrument.symbol();
            if (boardId == null || boardId.isBlank() || symbol == null || symbol.isBlank()) continue;
            grouped.computeIfAbsent(boardId, ignored -> new LinkedHashSet<>()).add(symbol);
        }

        LinkedHashMap<String, List<String>> result = new LinkedHashMap<>();
        grouped.forEach((board, symbols) ->
                result.put(board, List.copyOf(new ArrayList<>(symbols))));
        return Collections.unmodifiableMap(result);
    }
}
