package vn.dnse.openapi.websocket.auth;

import org.junit.jupiter.api.Test;
import vn.dnse.openapi.websocket.NonceGenerator;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebSocketAuthManagerTest {
    @Test
    void matchesPythonHmacSha256GoldenVector() {
        Clock clock = Clock.fixed(Instant.ofEpochSecond(1_720_000_000L), ZoneOffset.UTC);
        NonceGenerator nonce = () -> "1720000000123456";
        WebSocketAuthManager manager = new WebSocketAuthManager("test-api-key", "test-secret", clock, nonce);

        AuthMessage message = manager.createAuthMessage();

        assertEquals(1_720_000_000L, message.timestamp());
        assertEquals("1720000000123456", message.nonce());
        assertEquals("b65a4df35aafcf3dcaa550704672bc003197db3a230faa97cb2dcdfcb9e16ba0", message.signature());
    }
}
