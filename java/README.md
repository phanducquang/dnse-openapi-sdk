# DNSE OpenAPI Java SDK

Java 17 SDK for DNSE OpenAPI. The first milestone focuses on WebSocket parity with the Python SDK.

## Current scope

- Java 17, framework-independent core
- HMAC-SHA256 WebSocket authentication compatible with the Python SDK
- JSON and MessagePack codecs
- OkHttp WebSocket transport
- connection/authentication state machine
- trade, quote, OHLC and security-definition typed events
- subscription/unsubscription helpers
- per-symbol ordered dispatch using striped single-thread executors
- application heartbeat
- exponential-backoff reconnect, re-authentication and subscription restore

## Build

```bash
cd java
./gradlew test
```

If the Gradle wrapper is not committed yet, use an installed Gradle 8.x:

```bash
cd java
gradle test
```

## Example

```java
DnseWebSocketConfig config = DnseWebSocketConfig.builder()
        .apiKey(System.getenv("DNSE_API_KEY"))
        .apiSecret(System.getenv("DNSE_API_SECRET"))
        .encoding(MessageEncoding.JSON)
        .build();

try (DnseWebSocketClient client = new DnseWebSocketClient(config)) {
    client.onTrade(System.out::println);
    client.onError(Throwable::printStackTrace);
    client.connect().join();
    client.subscribeTrades(List.of("FPT", "VNM"), "G1");

    Thread.currentThread().join();
}
```

## Compatibility source

Protocol behavior is ported from `python/dnse/websocket` in this repository. REST support is intentionally deferred until the WebSocket implementation reaches parity.
