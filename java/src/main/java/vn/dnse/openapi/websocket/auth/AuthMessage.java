package vn.dnse.openapi.websocket.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AuthMessage(
        String action,
        @JsonProperty("api_key") String apiKey,
        String signature,
        long timestamp,
        String nonce
) {}
