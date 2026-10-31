package vn.dnse.openapi.websocket.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import vn.dnse.openapi.websocket.exception.DnseEncodingException;

/** JSON implementation of {@link MessageCodec}. */
public final class JsonMessageCodec implements MessageCodec {
    /** Jackson mapper used for UTF-8 JSON serialization/deserialization. */
    private final ObjectMapper mapper;

    /** Creates a codec using a default Jackson {@link ObjectMapper}. */
    public JsonMessageCodec() {
        this(new ObjectMapper());
    }

    /** Creates a codec using a caller-supplied mapper. */
    public JsonMessageCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** {@inheritDoc} */
    @Override
    public byte[] encode(Object value) {
        try {
            return mapper.writeValueAsBytes(value);
        } catch (Exception e) {
            throw new DnseEncodingException("Failed to encode JSON message", e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public JsonNode decode(byte[] payload) {
        try {
            return mapper.readTree(payload);
        } catch (Exception e) {
            throw new DnseEncodingException("Failed to decode JSON message", e);
        }
    }
}
