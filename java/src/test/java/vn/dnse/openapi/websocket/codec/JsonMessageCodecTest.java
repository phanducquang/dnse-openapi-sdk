package vn.dnse.openapi.websocket.codec;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonMessageCodecTest {
    @Test
    void roundTripsJson() {
        JsonMessageCodec codec = new JsonMessageCodec();
        byte[] encoded = codec.encode(Map.of("action", "ping"));
        assertEquals("ping", codec.decode(encoded).get("action").asText());
    }
}
