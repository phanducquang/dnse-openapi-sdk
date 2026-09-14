package vn.dnse.openapi.websocket.auth;

import vn.dnse.openapi.websocket.NonceGenerator;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

public final class WebSocketAuthManager {
    private final String apiKey;
    private final String apiSecret;
    private final Clock clock;
    private final NonceGenerator nonceGenerator;

    public WebSocketAuthManager(String apiKey, String apiSecret, Clock clock, NonceGenerator nonceGenerator) {
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
        this.apiSecret = Objects.requireNonNull(apiSecret, "apiSecret");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.nonceGenerator = Objects.requireNonNull(nonceGenerator, "nonceGenerator");
    }

    public AuthMessage createAuthMessage() {
        long timestamp = Instant.now(clock).getEpochSecond();
        String nonce = nonceGenerator.generate();
        return new AuthMessage("auth", apiKey, computeSignature(timestamp, nonce), timestamp, nonce);
    }

    public String computeSignature(long timestamp, String nonce) {
        try {
            String message = apiKey + ":" + timestamp + ":" + nonce;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to compute WebSocket HMAC signature", e);
        }
    }
}
