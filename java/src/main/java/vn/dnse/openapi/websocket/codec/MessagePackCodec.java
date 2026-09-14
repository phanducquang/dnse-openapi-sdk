package vn.dnse.openapi.websocket.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.msgpack.jackson.dataformat.MessagePackFactory;
import vn.dnse.openapi.websocket.exception.DnseEncodingException;

public final class MessagePackCodec implements MessageCodec {
    private final ObjectMapper mapper = new ObjectMapper(new MessagePackFactory());

    @Override
    public byte[] encode(Object value) {
        try {
            return mapper.writeValueAsBytes(value);
        } catch (Exception e) {
            throw new DnseEncodingException("Failed to encode MessagePack message", e);
        }
    }

    @Override
    public JsonNode decode(byte[] payload) {
        try {
            return mapper.readTree(payload);
        } catch (Exception e) {
            throw new DnseEncodingException("Failed to decode MessagePack message", e);
        }
    }
}
