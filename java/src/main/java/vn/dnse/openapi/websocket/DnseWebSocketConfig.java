package vn.dnse.openapi.websocket;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/**
 * Immutable runtime configuration for {@link DnseWebSocketClient}.
 *
 * @param apiKey DNSE OpenAPI key sent in the authentication message
 * @param apiSecret secret used only to compute the HMAC-SHA256 signature; it is never sent directly
 * @param baseUrl WebSocket gateway base URL, normally {@code wss://ws-openapi.dnse.com.vn}
 * @param encoding payload encoding used both in the connection query string and channel names
 * @param connectTimeout maximum time allowed for the underlying WebSocket connection attempt
 * @param heartbeatInterval interval for client-initiated application heartbeat messages; zero disables it
 * @param dispatchWorkers number of striped single-thread workers used to preserve per-symbol ordering
 * @param queueCapacity maximum queued callbacks per dispatch stripe before backpressure blocks the producer
 * @param reconnectPolicy automatic reconnect/backoff settings used after runtime disconnects and as the retry budget for initial RETRY mode
 * @param initialConnectionPolicy fail-fast or retry behavior for the first connection/authentication sequence
 * @param clock time source used for auth timestamps, heartbeat health checks and deterministic tests
 * @param nonceGenerator generator used for the authentication nonce
 */
public record DnseWebSocketConfig(
        String apiKey,
        String apiSecret,
        String baseUrl,
        MessageEncoding encoding,
        Duration connectTimeout,
        Duration heartbeatInterval,
        int dispatchWorkers,
        int queueCapacity,
        ReconnectPolicy reconnectPolicy,
        InitialConnectionPolicy initialConnectionPolicy,
        Clock clock,
        NonceGenerator nonceGenerator
) {
    /** Validates configuration before a client can be created. */
    public DnseWebSocketConfig {
        Objects.requireNonNull(apiKey, "apiKey");
        Objects.requireNonNull(apiSecret, "apiSecret");
        Objects.requireNonNull(baseUrl, "baseUrl");
        Objects.requireNonNull(encoding, "encoding");
        Objects.requireNonNull(connectTimeout, "connectTimeout");
        Objects.requireNonNull(heartbeatInterval, "heartbeatInterval");
        Objects.requireNonNull(reconnectPolicy, "reconnectPolicy");
        Objects.requireNonNull(initialConnectionPolicy, "initialConnectionPolicy");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(nonceGenerator, "nonceGenerator");
        if (dispatchWorkers <= 0) throw new IllegalArgumentException("dispatchWorkers must be > 0");
        if (queueCapacity <= 0) throw new IllegalArgumentException("queueCapacity must be > 0");
    }

    /** @return a builder initialized with SDK defaults */
    public static Builder builder() {
        return new Builder();
    }

    /** Fluent builder for {@link DnseWebSocketConfig}. */
    public static final class Builder {
        private String apiKey;
        private String apiSecret;
        private String baseUrl = "wss://ws-openapi.dnse.com.vn";
        private MessageEncoding encoding = MessageEncoding.JSON;
        private Duration connectTimeout = Duration.ofSeconds(60);
        private Duration heartbeatInterval = Duration.ofSeconds(25);
        private int dispatchWorkers = 6;
        private int queueCapacity = 10_000;
        private ReconnectPolicy reconnectPolicy = ReconnectPolicy.defaults();
        /** Preserve previous SDK behavior unless long-running applications explicitly opt into startup retry. */
        private InitialConnectionPolicy initialConnectionPolicy = InitialConnectionPolicy.FAIL_FAST;
        private Clock clock = Clock.systemUTC();
        private NonceGenerator nonceGenerator = NonceGenerator.epochMicros();

        public Builder apiKey(String value) { this.apiKey = value; return this; }
        public Builder apiSecret(String value) { this.apiSecret = value; return this; }
        public Builder baseUrl(String value) { this.baseUrl = value; return this; }
        public Builder encoding(MessageEncoding value) { this.encoding = value; return this; }
        public Builder connectTimeout(Duration value) { this.connectTimeout = value; return this; }
        public Builder heartbeatInterval(Duration value) { this.heartbeatInterval = value; return this; }
        public Builder dispatchWorkers(int value) { this.dispatchWorkers = value; return this; }
        public Builder queueCapacity(int value) { this.queueCapacity = value; return this; }
        public Builder reconnectPolicy(ReconnectPolicy value) { this.reconnectPolicy = value; return this; }
        public Builder initialConnectionPolicy(InitialConnectionPolicy value) { this.initialConnectionPolicy = value; return this; }
        public Builder clock(Clock value) { this.clock = value; return this; }
        public Builder nonceGenerator(NonceGenerator value) { this.nonceGenerator = value; return this; }

        /** Builds and validates an immutable configuration. */
        public DnseWebSocketConfig build() {
            String normalized = baseUrl == null ? null : baseUrl.replaceAll("/+$", "");
            return new DnseWebSocketConfig(apiKey, apiSecret, normalized, encoding, connectTimeout,
                    heartbeatInterval, dispatchWorkers, queueCapacity, reconnectPolicy, initialConnectionPolicy,
                    clock, nonceGenerator);
        }
    }
}
