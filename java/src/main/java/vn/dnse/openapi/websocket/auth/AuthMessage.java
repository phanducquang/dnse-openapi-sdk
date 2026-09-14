package vn.dnse.openapi.websocket.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DNSE authentication payload sent immediately after the server welcome message.
 *
 * @param action protocol action; always {@code auth}
 * @param apiKey DNSE API key serialized as {@code api_key}
 * @param signature lowercase hexadecimal HMAC-SHA256 of {@code apiKey:timestamp:nonce}
 * @param timestamp Unix epoch timestamp in seconds used by the signature
 * @param nonce per-authentication nonce used by the signature
 */
public record AuthMessage(
        String action,
        @JsonProperty("api_key") String apiKey,
        String signature,
        long timestamp,
        String nonce
) {}
