package vn.dnse.openapi.websocket;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

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

    public static Builder builder() {
        return new Builder();
    }

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
        public Builder clock(Clock value) { this.clock = value; return this; }
        public Builder nonceGenerator(NonceGenerator value) { this.nonceGenerator = value; return this; }

        public DnseWebSocketConfig build() {
            String normalized = baseUrl == null ? null : baseUrl.replaceAll("/+$", "");
            return new DnseWebSocketConfig(apiKey, apiSecret, normalized, encoding, connectTimeout,
                    heartbeatInterval, dispatchWorkers, queueCapacity, reconnectPolicy, clock, nonceGenerator);
        }
    }
}
