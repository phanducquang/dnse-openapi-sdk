package vn.dnse.openapi.rest;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/** Immutable configuration for the DNSE REST client. */
public record DnseRestConfig(
        String apiKey,
        String apiSecret,
        String baseUrl,
        String algorithm,
        boolean hmacNonceEnabled,
        String apiVersion,
        String dateHeaderName,
        Duration connectTimeout,
        Duration readTimeout,
        Clock clock,
        RestNonceGenerator nonceGenerator
) {
    public static final String DEFAULT_BASE_URL = "https://openapi.dnse.com.vn";
    public static final String DEFAULT_ALGORITHM = "hmac-sha256";
    public static final String DEFAULT_API_VERSION = "2026-07-23";
    public static final String DEFAULT_DATE_HEADER_NAME = "Date";

    public DnseRestConfig {
        Objects.requireNonNull(apiKey, "apiKey");
        Objects.requireNonNull(apiSecret, "apiSecret");
        Objects.requireNonNull(baseUrl, "baseUrl");
        Objects.requireNonNull(algorithm, "algorithm");
        Objects.requireNonNull(apiVersion, "apiVersion");
        Objects.requireNonNull(dateHeaderName, "dateHeaderName");
        Objects.requireNonNull(connectTimeout, "connectTimeout");
        Objects.requireNonNull(readTimeout, "readTimeout");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(nonceGenerator, "nonceGenerator");
        if (apiKey.isBlank()) throw new IllegalArgumentException("apiKey must not be blank");
        if (apiSecret.isBlank()) throw new IllegalArgumentException("apiSecret must not be blank");
        if (baseUrl.isBlank()) throw new IllegalArgumentException("baseUrl must not be blank");
        if (algorithm.isBlank()) throw new IllegalArgumentException("algorithm must not be blank");
        if (apiVersion.isBlank()) throw new IllegalArgumentException("apiVersion must not be blank");
        if (dateHeaderName.isBlank()) throw new IllegalArgumentException("dateHeaderName must not be blank");
        if (connectTimeout.isNegative() || connectTimeout.isZero()) {
            throw new IllegalArgumentException("connectTimeout must be > 0");
        }
        if (readTimeout.isNegative() || readTimeout.isZero()) {
            throw new IllegalArgumentException("readTimeout must be > 0");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String apiKey;
        private String apiSecret;
        private String baseUrl = DEFAULT_BASE_URL;
        private String algorithm = DEFAULT_ALGORITHM;
        private boolean hmacNonceEnabled = true;
        private String apiVersion = DEFAULT_API_VERSION;
        private String dateHeaderName = DEFAULT_DATE_HEADER_NAME;
        private Duration connectTimeout = Duration.ofSeconds(30);
        private Duration readTimeout = Duration.ofSeconds(60);
        private Clock clock = Clock.systemUTC();
        private RestNonceGenerator nonceGenerator = RestNonceGenerator.uuidHex();

        public Builder apiKey(String value) { this.apiKey = value; return this; }
        public Builder apiSecret(String value) { this.apiSecret = value; return this; }
        public Builder baseUrl(String value) { this.baseUrl = value; return this; }
        public Builder algorithm(String value) { this.algorithm = value; return this; }
        public Builder hmacNonceEnabled(boolean value) { this.hmacNonceEnabled = value; return this; }
        public Builder apiVersion(String value) { this.apiVersion = value; return this; }
        public Builder dateHeaderName(String value) { this.dateHeaderName = value; return this; }
        public Builder connectTimeout(Duration value) { this.connectTimeout = value; return this; }
        public Builder readTimeout(Duration value) { this.readTimeout = value; return this; }
        public Builder clock(Clock value) { this.clock = value; return this; }
        public Builder nonceGenerator(RestNonceGenerator value) { this.nonceGenerator = value; return this; }

        public DnseRestConfig build() {
            String normalizedBaseUrl = baseUrl == null ? null : baseUrl.replaceAll("/+$", "");
            return new DnseRestConfig(
                    apiKey,
                    apiSecret,
                    normalizedBaseUrl,
                    algorithm,
                    hmacNonceEnabled,
                    apiVersion,
                    dateHeaderName,
                    connectTimeout,
                    readTimeout,
                    clock,
                    nonceGenerator
            );
        }
    }
}
