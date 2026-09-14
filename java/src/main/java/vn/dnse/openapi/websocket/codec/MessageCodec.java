package vn.dnse.openapi.websocket.codec;

import com.fasterxml.jackson.databind.JsonNode;

public interface MessageCodec {
    byte[] encode(Object value);
    JsonNode decode(byte[] payload);
}
