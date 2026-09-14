package vn.dnse.openapi.websocket.codec;

import com.fasterxml.jackson.databind.JsonNode;

/** Converts SDK messages between Java objects and the selected DNSE wire representation. */
public interface MessageCodec {
    /**
     * Serializes an outbound protocol message.
     * @param value message object/map
     * @return encoded wire bytes
     */
    byte[] encode(Object value);

    /**
     * Parses an inbound WebSocket payload into a common JSON tree used by the message mapper.
     * @param payload raw WebSocket message bytes
     * @return decoded tree
     */
    JsonNode decode(byte[] payload);
}
