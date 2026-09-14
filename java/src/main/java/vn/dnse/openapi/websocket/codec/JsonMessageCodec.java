package vn.dnse.openapi.websocket.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import vn.dnse.openapi.websocket.exception.DnseEncodingException;

public final class JsonMessageCodec implements MessageCodec {
    private final ObjectMapper mapper;

    public JsonMessageCodec() {
        this(new ObjectMapper());
    }

    public JsonMessageCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public byte[] encode(Object value) {
        try {
            return mapper.writeValueAsBytes(value);
        } catch (Exception e) {
            throw new DnseEncodingException("Failed to encode JSON message", e);
        }
    }

    @Override
    public JsonNode decode(byte[] payload) {
        try {
            return mapper.readTree(payload);
        } catch (Exception e) {
            throw new DnseEncodingException("Failed to decode JSON message", e);
        }
    }
}
