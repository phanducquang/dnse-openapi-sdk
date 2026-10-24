# DNSE OpenAPI Java SDK - Usage Guide

This guide covers building, running, integrating and operating the Java WebSocket SDK in a long-running application. REST Market Data usage is documented separately in [REST_USAGE.md](REST_USAGE.md).

## 1. Requirements

- Java 17+
- Gradle 8.x for building this repository
- DNSE OpenAPI `apiKey` and `apiSecret`

The Java SDK core is framework-independent. Spring Boot, Micrometer, Kafka, Redis and database integrations belong in the consuming application.

## 2. Build and test the SDK

```bash
cd java
gradle test
```

Build the JAR:

```bash
gradle build
```

The current artifact coordinates are:

```text
groupId:    vn.dnse.openapi
artifactId: dnse-openapi-sdk
version:    0.1.0-SNAPSHOT
```

Until a remote Maven repository is configured, publish the SDK to your local Maven repository:

```bash
cd java
gradle publishToMavenLocal
```

Then consume it from another Gradle project:

```gradle
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation 'vn.dnse.openapi:dnse-openapi-sdk:0.1.0-SNAPSHOT'
}
```

## 3. Run the executable realtime example

Required environment variables:

```bash
export DNSE_API_KEY='<your-api-key>'
export DNSE_API_SECRET='<your-api-secret>'
```

Run:

```bash
cd java
gradle run
```

Defaults:

```text
DNSE_WS_BASE_URL                 wss://ws-openapi.dnse.com.vn
DNSE_SYMBOLS                     FPT
DNSE_BOARD                       G1
DNSE_ENCODING                    JSON
DNSE_INITIAL_CONNECTION_POLICY   RETRY
```

Example with several symbols and MessagePack:

```bash
DNSE_API_KEY='<your-api-key>' \
DNSE_API_SECRET='<your-api-secret>' \
DNSE_SYMBOLS='FPT,VNM,HPG' \
DNSE_BOARD='G1' \
DNSE_ENCODING='MSGPACK' \
DNSE_INITIAL_CONNECTION_POLICY='RETRY' \
gradle run
```

The example logs lifecycle transitions, authenticates, subscribes trades and keeps running until `Ctrl+C`. Shutdown closes the WebSocket gracefully.

## 4. Minimal SDK usage

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

    // Keep your application alive here.
}
```

`connect()` completes only after the DNSE welcome/authentication sequence reaches `AUTHENTICATED` or the configured startup policy gives up.

## 5. Recommended configuration for a long-running market-data service

For a service that continuously consumes many symbols, a reasonable starting configuration is:

```java
DnseWebSocketConfig config = DnseWebSocketConfig.builder()
        .apiKey(apiKey)
        .apiSecret(apiSecret)
        .baseUrl("wss://ws-openapi.dnse.com.vn")
        .encoding(MessageEncoding.MSGPACK)
        .connectTimeout(Duration.ofSeconds(30))
        .handshakeTimeout(Duration.ofSeconds(30))
        .heartbeatInterval(Duration.ofSeconds(25))
        .dispatchWorkers(16)
        .queueCapacity(4_000)
        .initialConnectionPolicy(InitialConnectionPolicy.RETRY)
        .reconnectPolicy(ReconnectPolicy.forever(
                Duration.ofSeconds(1),
                Duration.ofSeconds(60)
        ))
        .build();
```

These worker/queue values are an operational starting point, not a DNSE protocol requirement. Measure real traffic and tune them using SDK metrics/backpressure data.

## 6. Initial connection policy

The default is:

```java
InitialConnectionPolicy.FAIL_FAST
```

This preserves simple/previous behavior: the first connection failure completes `connect()` exceptionally and the SDK does not start a hidden background startup retry.

For a long-running service use:

```java
.initialConnectionPolicy(InitialConnectionPolicy.RETRY)
.handshakeTimeout(Duration.ofSeconds(30))
.reconnectPolicy(ReconnectPolicy.forever(
        Duration.ofSeconds(1),
        Duration.ofSeconds(60)
))
```

`ReconnectPolicy.defaults()` remains compatible with the Python SDK and stops after 10 retries. `ReconnectPolicy.forever(...)` is intended for continuously running ingestion services that must recover after a prolonged gateway outage.

Startup flow becomes:

```text
CONNECTING
  -> failure
RECONNECTING
  -> delay 1s
CONNECTING
  -> failure
RECONNECTING
  -> delay 2s
...
AUTHENTICATED
```

Important behavior:

- `RETRY` uses the configured reconnect policy and exponential backoff.
- `handshakeTimeout` bounds the wait for the DNSE welcome/authentication exchange after the WebSocket opens.
- Authentication failures are not retried. Invalid credentials should fail instead of repeatedly hitting the gateway.
- After the first successful authentication, runtime disconnect handling uses the normal reconnect/re-auth/restore flow.
- A peer `1001 Going Away` close is recoverable for long-running clients; explicit application shutdown still suppresses reconnect.

## 7. Event handlers

Typed handlers are available for market and private events:

```java
client.onTrade(trade -> processTrade(trade));
client.onTradeExtra(this::processTradeExtra);
client.onQuote(this::processQuote);
client.onOhlc(this::processOhlc);
client.onOhlcClosed(this::processClosedOhlc);
client.onExpectedPrice(this::processExpectedPrice);
client.onSecurityDefinition(this::processSecurityDefinition);
client.onForeignTrading(this::processForeignTrading);
client.onMarketIndex(this::processMarketIndex);
client.onEstimatedMarketIndex(this::processEstimatedMarketIndex);
client.onMarketIndexInfluence(this::processIndexInfluence);
client.onSession(this::processSession);
client.onOrderEvent(this::processOrder);
client.onPositionEvent(this::processPosition);
client.onAccount(this::processAccount);
```

Callbacks execute on SDK threads. Keep callbacks fast. For high-volume all-market ingestion, hand events to an asynchronous downstream layer such as Kafka or another bounded processing queue rather than blocking on database/HTTP work in the callback.

## 8. Single-channel subscriptions

```java
Subscription tradeSubscription = client.subscribeTrades(
        List.of("FPT", "VNM"),
        "G1"
);

tradeSubscription.unsubscribe().join();
```

Calling `subscribeTrades(symbols)` without a board subscribes the same symbol list across the Python SDK default trade boards. Do not use that overload for an all-market universe unless the same symbols really belong on every board.

## 9. Bulk subscriptions for an all-market universe

Load instruments in the consuming application, filter the instruments you want, then group symbols by the correct DNSE board:

```java
Map<String, List<String>> symbolsByBoard = new LinkedHashMap<>();
symbolsByBoard.put("G1", g1Symbols);
symbolsByBoard.put("G3", g3Symbols);
symbolsByBoard.put("G4", g4Symbols);
```

Subscribe in bounded batches:

```java
SubscriptionOptions options = SubscriptionOptions.builder()
        .batchSize(200)
        .build();

BulkSubscriptionResult result = client.subscribeTrades(
        symbolsByBoard,
        options
);

// Enhanced TradeExtra feed:
BulkSubscriptionResult tradeExtraResult = client.subscribeTradeExtra(
        symbolsByBoard,
        options
);

System.out.printf(
        "requested=%d subscribed=%d requests=%d%n",
        result.requestedSymbols(),
        result.subscribedSymbols(),
        result.subscriptionCount()
);
```

The SDK:

- deduplicates symbols per board;
- sends batches sequentially;
- merges the local reconnect state;
- remembers the safe restore batch size;
- restores the same universe after reconnect using bounded batches.

`200` is a conservative application setting, not a documented DNSE hard limit. Validate the chosen value against the live gateway.

## 10. Runtime trade-universe reconciliation

Long-running services often refresh the instrument universe periodically. Do not reconnect the WebSocket just because symbols changed.

Pass the new desired universe to:

```java
SubscriptionReconciliationResult result = client.reconcileTrades(
        desiredSymbolsByBoard,
        SubscriptionOptions.builder()
                .batchSize(200)
                .build()
);
```

The SDK calculates:

```text
current subscriptions
        vs
new desired subscriptions
        -> added symbols
        -> removed symbols
        -> unchanged symbols
```

Only the delta is sent to DNSE. The result reports:

```java
result.desiredSymbols();
result.addedSymbols();
result.removedSymbols();
result.unchangedSymbols();
result.subscribeOperations();
result.unsubscribeOperations();
result.activeChannels();
```

Example:

```text
Current G1: FPT, VNM, HPG
Desired G1: FPT, HPG, SSI
Desired G3: VCB

Wire delta:
subscribe   G1 -> SSI
unsubscribe G1 -> VNM
subscribe   G3 -> VCB
```

After reconciliation, reconnect restoration uses the new desired universe. Calling `reconcileTrades()` again with the same universe produces zero subscribe/unsubscribe operations.

TradeExtra (`tick_extra.*`) supports the same SDK-managed reconciliation:

```java
SubscriptionReconciliationResult tradeExtraResult = client.reconcileTradeExtra(
        desiredSymbolsByBoard,
        options
);
```

Both Trade and TradeExtra reconciliation update the local reconnect state to the converged desired universe.

## 11. Runtime reconnect behavior

Once authenticated, abnormal transport failures use the configured reconnect policy:

```text
AUTHENTICATED
  -> connection failure
RECONNECTING
  -> CONNECTING
  -> CONNECTED
  -> AUTHENTICATING
  -> AUTHENTICATED
  -> restore active subscriptions
  -> READY
```

The SDK exposes two related health concepts:

```java
client.isHealthy(); // authenticated transport + heartbeat
client.isReady();   // healthy and subscription restoration complete
```

During reconnect, `isReady()` remains false until the local subscription universe has been restored successfully. Applications should normally use `isReady()` for feed readiness.

Do not create a second reconnect loop in Spring, `@Scheduled`, or application code. Let the SDK own WebSocket reconnect/re-auth/re-subscribe behavior.

## 12. Subscription confirmation semantics

Async subscribe APIs expose:

```java
SubscriptionResult result = client
        .subscribeTradesAsync(List.of("FPT"), "G1")
        .join();

SubscriptionConfirmation confirmation = result.confirmation();
```

The current confirmation is:

```text
TRANSPORT_ACCEPTED
```

It means the WebSocket transport accepted the outgoing message and the SDK stored the subscription for reconnect. It does not mean DNSE returned a subscription ACK because the Python SDK/protocol flow currently used by this project does not consume an ACK/request-id.

If DNSE later sends an `action=error` payload with channel information, the SDK emits a structured `DnseSubscriptionException` through `onError`.

## 13. State, errors, metrics and backpressure

Lifecycle changes:

```java
client.onStateChanged(event -> log.info(
        "DNSE {} -> {} session={} cause={}",
        event.previous(),
        event.current(),
        event.sessionId(),
        event.cause()
));
```

Errors:

```java
client.onError(error -> log.error("DNSE WebSocket error", error));
```

Framework-neutral metrics:

```java
client.onMetrics(new DnseWebSocketMetricsListener() {
    @Override
    public void onReconnect(int attempt) {
        // bridge to Micrometer/Prometheus in your application
    }

    @Override
    public void onSubscriptionAdded(String channel, int symbolCount) {
        // metric
    }
});
```

Backpressure:

```java
client.onBackpressure(event -> log.warn(
        "worker={} queue={}/{} blocked={}ms",
        event.workerIndex(),
        event.queueSize(),
        event.queueCapacity(),
        event.blockedFor().toMillis()
));

DispatcherStats stats = client.dispatcherStats();
```

Health-related values:

```java
client.state();
client.sessionId();
client.lastPongAt();
client.isHealthy();
client.isReady();
client.subscriptionsReady();
client.subscriptionRestoreInProgress();
client.lastSubscriptionRestoreError();
```

A Spring Boot application can map these values into its own `HealthIndicator`, readiness group and Micrometer metrics without adding Spring dependencies to the SDK.

## 14. Graceful shutdown

The client implements `AutoCloseable`:

```java
try (DnseWebSocketClient client = new DnseWebSocketClient(config)) {
    client.connect().join();
    // use client
}
```

Or explicitly:

```java
client.close();
```

`close()` intentionally disconnects, suppresses reconnect and performs the WebSocket close handshake. The striped callback dispatcher first stops accepting new callbacks and drains already queued callback work for a bounded period before force-stopping remaining work.

## 15. Live smoke test

Real credentials are intentionally excluded from normal CI.

```bash
cd java
DNSE_LIVE_TEST=true \
DNSE_API_KEY='<your-api-key>' \
DNSE_API_SECRET='<your-api-secret>' \
gradle test --tests vn.dnse.openapi.websocket.DnseWebSocketLiveSmokeTest --stacktrace
```

Optional:

```text
DNSE_WS_BASE_URL   default: wss://ws-openapi.dnse.com.vn
DNSE_TEST_SYMBOL   default: FPT
DNSE_TEST_BOARD    default: G1
```

Never commit API credentials to the repository.

## 16. Current scope and next work

Implemented WebSocket scope includes authentication, JSON/MessagePack, typed events, subscriptions, bulk batching, handshake timeout, bounded/unlimited startup and runtime retry, reconnect/restore readiness, Trade/TradeExtra universe reconciliation, ordering, backpressure, graceful callback draining and observability hooks.

REST signing/transport plus all public endpoint wrappers currently present in the Python REST client are now implemented under `vn.dnse.openapi.rest`. See [REST_USAGE.md](REST_USAGE.md).

The remaining REST work is optional typed-response modeling and live endpoint validation; the wire-level API conversion is complete.
