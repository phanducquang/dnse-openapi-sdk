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
 * @param reconnectPolicy automatic reconnect/backoff settings
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
        /** Required DNSE API key. */
        private String apiKey;
        /** Required secret used to sign authentication messages. */
        private String apiSecret;
        /** DNSE production WebSocket endpoint by default. */
        private String baseUrl = "wss://ws-openapi.dnse.com.vn";
        /** JSON is the default because it is easiest to inspect while integrating. */
        private MessageEncoding encoding = MessageEncoding.JSON;
        /** Connection establishment timeout, aligned with the Python SDK default. */
        private Duration connectTimeout = Duration.ofSeconds(60);
        /** Proactive application heartbeat interval. */
        private Duration heartbeatInterval = Duration.ofSeconds(25);
        /** Number of independent ordered event stripes. */
        private int dispatchWorkers = 6;
        /** Per-stripe bounded callback queue size. */
        private int queueCapacity = 10_000;
        /** Reconnect policy used after abnormal close/failure. */
        private ReconnectPolicy reconnectPolicy = ReconnectPolicy.defaults();
        /** Injectable clock, primarily useful for deterministic auth/health tests. */
        private Clock clock = Clock.systemUTC();
        /** Injectable nonce source, primarily useful for deterministic auth tests. */
        private NonceGenerator nonceGenerator = NonceGenerator.epochMicros();

        /** Sets the DNSE API key. */
        public Builder apiKey(String value) { this.apiKey = value; return this; }
        /** Sets the DNSE API secret used for HMAC signing. */
        public Builder apiSecret(String value) { this.apiSecret = value; return this; }
        /** Sets the WebSocket base URL; trailing slashes are removed by {@link #build()}. */
        public Builder baseUrl(String value) { this.baseUrl = value; return this; }
        /** Sets JSON or MessagePack wire encoding. */
        public Builder encoding(MessageEncoding value) { this.encoding = value; return this; }
        /** Sets the transport connection timeout. */
        public Builder connectTimeout(Duration value) { this.connectTimeout = value; return this; }
        /** Sets the client heartbeat interval; use zero to disable proactive heartbeat. */
        public Builder heartbeatInterval(Duration value) { this.heartbeatInterval = value; return this; }
        /** Sets the number of symbol-ordering dispatch stripes. */
        public Builder dispatchWorkers(int value) { this.dispatchWorkers = value; return this; }
        /** Sets the bounded queue capacity of each dispatch stripe. */
        public Builder queueCapacity(int value) { this.queueCapacity = value; return this; }
        /** Sets reconnect/backoff behavior. */
        public Builder reconnectPolicy(ReconnectPolicy value) { this.reconnectPolicy = value; return this; }
        /** Replaces the time source used internally. */
        public Builder clock(Clock value) { this.clock = value; return this; }
        /** Replaces the authentication nonce generator. */
        public Builder nonceGenerator(NonceGenerator value) { this.nonceGenerator = value; return this; }

        /**
         * Builds and validates an immutable configuration.
         *
         * @return normalized configuration instance
         */
        public DnseWebSocketConfig build() {
            String normalized = baseUrl == null ? null : baseUrl.replaceAll("/+$", "");
            return new DnseWebSocketConfig(apiKey, apiSecret, normalized, encoding, connectTimeout,
                    heartbeatInterval, dispatchWorkers, queueCapacity, reconnectPolicy, clock, nonceGenerator);
        }
    }
}
