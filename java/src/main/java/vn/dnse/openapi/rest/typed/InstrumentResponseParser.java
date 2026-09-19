package vn.dnse.openapi.rest.typed;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import vn.dnse.openapi.rest.DnseRestException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Tolerant parser for the DNSE instruments endpoint.
 *
 * <p>The live envelope is deliberately not hard-coded to a single shape until gateway validation
 * is completed. Supported collection locations are top-level arrays and arrays named
 * {@code instruments}, {@code items}, {@code content} or {@code data}, including one nested
 * {@code data} object.</p>
 */
final class InstrumentResponseParser {
    private static final Set<String> COLLECTION_KEYS =
            Set.of("instruments", "items", "content", "data");

    private final ObjectMapper objectMapper;

    InstrumentResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    InstrumentListResponse parse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body == null || body.isBlank() ? "null" : body);
            JsonNode collection = findCollection(root);
            List<Instrument> instruments = new ArrayList<>();

            if (collection != null && collection.isArray()) {
                for (JsonNode item : collection) {
                    instruments.add(objectMapper.treeToValue(item, Instrument.class));
                }
            }

            JsonNode metadataRoot = root;
            if (root != null && root.isObject() && root.path("data").isObject()) {
                metadataRoot = root.path("data");
            }

            return new InstrumentListResponse(
                    instruments,
                    integer(first(metadataRoot, root, "page", "pageIndex", "currentPage")),
                    integer(first(metadataRoot, root, "limit", "pageSize", "size")),
                    longValue(first(metadataRoot, root, "total", "totalElements", "totalItems")),
                    integer(first(metadataRoot, root, "totalPages", "pageCount")),
                    text(first(metadataRoot, root, "nextPageToken", "nextToken")),
                    root
            );
        } catch (JsonProcessingException e) {
            throw new DnseRestException("Unable to parse DNSE instruments response", e);
        }
    }

    private static JsonNode findCollection(JsonNode root) {
        if (root == null || root.isNull()) return null;
        if (root.isArray()) return root;
        if (!root.isObject()) return null;

        for (String key : COLLECTION_KEYS) {
            JsonNode candidate = root.get(key);
            if (candidate != null && candidate.isArray()) return candidate;
        }

        JsonNode data = root.get("data");
        if (data != null && data.isObject()) {
            for (String key : COLLECTION_KEYS) {
                JsonNode candidate = data.get(key);
                if (candidate != null && candidate.isArray()) return candidate;
            }
        }

        // Last-resort compatibility: accept the first array field in the object.
        Iterator<JsonNode> values = root.elements();
        while (values.hasNext()) {
            JsonNode candidate = values.next();
            if (candidate.isArray()) return candidate;
        }
        return null;
    }

    private static JsonNode first(JsonNode preferred, JsonNode fallback, String... keys) {
        for (String key : keys) {
            JsonNode value = preferred == null ? null : preferred.get(key);
            if (present(value)) return value;
            if (fallback != preferred) {
                value = fallback == null ? null : fallback.get(key);
                if (present(value)) return value;
            }
        }
        return null;
    }

    private static boolean present(JsonNode value) {
        return value != null && !value.isNull() && !value.isMissingNode();
    }

    private static Integer integer(JsonNode value) {
        if (!present(value)) return null;
        if (value.isInt() || value.isLong()) return value.intValue();
        try {
            return Integer.valueOf(value.asText());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Long longValue(JsonNode value) {
        if (!present(value)) return null;
        if (value.isNumber()) return value.longValue();
        try {
            return Long.valueOf(value.asText());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String text(JsonNode value) {
        return present(value) ? value.asText() : null;
    }
}
