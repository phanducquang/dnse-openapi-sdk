package vn.dnse.openapi.websocket.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.msgpack.jackson.dataformat.MessagePackFactory;
import vn.dnse.openapi.websocket.exception.DnseEncodingException;

/** MessagePack implementation of {@link MessageCodec} for lower bandwidth realtime feeds. */
public final class MessagePackCodec implements MessageCodec {
    /** Jackson mapper configured with the MessagePack binary factory. */
    private final ObjectMapper mapper = new ObjectMapper(new MessagePackFactory());

    /** {@inheritDoc} */
    @Override
    public byte[] encode(Object value) {
        try {
            return mapper.writeValueAsBytes(value);
        } catch (Exception e) {
            throw new DnseEncodingException("Failed to encode MessagePack message", e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public JsonNode decode(byte[] payload) {
        try {
            return mapper.readTree(payload);
        } catch (Exception e) {
            throw new DnseEncodingException("Failed to decode MessagePack message", e);
        }
    }
}
