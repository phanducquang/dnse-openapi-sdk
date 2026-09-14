package vn.dnse.openapi.websocket.auth;

import vn.dnse.openapi.websocket.NonceGenerator;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

/** Creates DNSE WebSocket HMAC authentication messages compatible with the Python SDK. */
public final class WebSocketAuthManager {
    /** Public API key included in the auth payload and signed message. */
    private final String apiKey;
    /** Private secret used as the HMAC key; never serialized into an outgoing payload. */
    private final String apiSecret;
    /** Time source for the epoch-second timestamp. */
    private final Clock clock;
    /** Source for the nonce included in every auth attempt. */
    private final NonceGenerator nonceGenerator;

    /** Creates an authentication manager using the supplied credentials and time sources. */
    public WebSocketAuthManager(String apiKey, String apiSecret, Clock clock, NonceGenerator nonceGenerator) {
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
        this.apiSecret = Objects.requireNonNull(apiSecret, "apiSecret");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.nonceGenerator = Objects.requireNonNull(nonceGenerator, "nonceGenerator");
    }

    /**
     * Creates the wire payload for one authentication attempt.
     *
     * @return signed authentication message
     */
    public AuthMessage createAuthMessage() {
        long timestamp = Instant.now(clock).getEpochSecond();
        String nonce = nonceGenerator.generate();
        return new AuthMessage("auth", apiKey, computeSignature(timestamp, nonce), timestamp, nonce);
    }

    /**
     * Computes the Python-compatible lowercase hexadecimal HMAC-SHA256 signature.
     *
     * @param timestamp epoch seconds included in the signed message
     * @param nonce nonce included in the signed message
     * @return HMAC digest encoded as lowercase hex
     */
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
