# DNSE OpenAPI Java SDK

Java 17 SDK for DNSE OpenAPI with production-ready WebSocket market/private realtime flows and REST endpoint parity with the current Python SDK.

The core library is framework-independent: it does not depend on Spring Boot, Micrometer, Kafka, Redis or a database. Those integrations stay in the consuming application.

For detailed WebSocket integration and production usage, see [USAGE.md](USAGE.md). For REST signing and Market Data APIs, see [REST_USAGE.md](REST_USAGE.md).

## Features

- HMAC-SHA256 authentication compatible with the Python SDK
- JSON and MessagePack codecs
- OkHttp WebSocket transport
- typed market/private event models and handlers
- lifecycle state callbacks
- initial connection policies: `FAIL_FAST` and `RETRY`
- configurable welcome/authentication handshake timeout
- bounded or unlimited exponential-backoff runtime reconnect and re-authentication
- peer `1001 Going Away` reconnect handling for gateway restarts
- automatic subscription restoration after reconnect with explicit readiness state
- single, async and bulk/batched subscriptions
- runtime Trade and TradeExtra universe reconciliation (`added` / `removed` / `unchanged`)
- reconnect-safe subscription batch restoration
- explicit `TRANSPORT_ACCEPTED` subscribe confirmation semantics
- structured server-reported subscription errors
- per-symbol ordered dispatch
- bounded queues and blocking backpressure
- framework-neutral metrics hooks and dispatcher statistics
- heartbeat and health helpers
- executable realtime example
- MockWebServer integration tests and Python-generated MessagePack compatibility tests
- signed REST client with Python-compatible Date/X-Signature/x-api-key/version headers
- REST dry-run request preview
- raw REST status/body responses, including HTTP error responses
- Market Data REST APIs: instruments, secdef, trades, volume profile, expected price, quotes, foreign trading, market index, OHLC, latest trade/quote, close price, working dates and latest trading session
- Account/read REST APIs: accounts, balances, loan packages, positions, orders, executions, order history, corporate actions, PPSE and care-by
- Trading REST wrappers: OTP/trading-token, position PnL config, place/replace/cancel order and close position

## Requirements

- Java 17+
- Gradle 8.x to build this module
- DNSE OpenAPI credentials for live usage

## Build and test

```bash
cd java
gradle test
```

Build JAR/source/Javadoc artifacts:

```bash
gradle build
```

## Use the SDK from another local project

The current Maven coordinates are:

```text
vn.dnse.openapi:dnse-openapi-sdk:0.1.0-SNAPSHOT
```

Publish locally:

```bash
cd java
gradle publishToMavenLocal
```

Consumer Gradle project:

```gradle
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation 'vn.dnse.openapi:dnse-openapi-sdk:0.1.0-SNAPSHOT'
}
```

A remote Maven release repository is not configured yet.

## Run the realtime example

```bash
cd java

export DNSE_API_KEY='<your-api-key>'
export DNSE_API_SECRET='<your-api-secret>'

gradle run
```

The example defaults to:

```text
Gateway:                     wss://ws-openapi.dnse.com.vn
Symbol:                      FPT
Board:                       G1
Encoding:                    JSON
Initial connection policy:   RETRY
```

Optional environment variables:

```text
DNSE_WS_BASE_URL                 default: wss://ws-openapi.dnse.com.vn
DNSE_SYMBOLS                     default: FPT; comma-separated, e.g. FPT,VNM,HPG
DNSE_BOARD                       default: G1
DNSE_ENCODING                    default: JSON; JSON or MSGPACK
DNSE_INITIAL_CONNECTION_POLICY   default: RETRY; RETRY or FAIL_FAST
```

Example:

```bash
DNSE_API_KEY='<your-api-key>' \
DNSE_API_SECRET='<your-api-secret>' \
DNSE_SYMBOLS='FPT,VNM,HPG' \
DNSE_BOARD='G1' \
DNSE_ENCODING='MSGPACK' \
DNSE_INITIAL_CONNECTION_POLICY='RETRY' \
gradle run
```

The process logs connection-state changes, subscribes trades, prints realtime data and closes gracefully on `Ctrl+C`.

## Quick start

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

    // Keep the application alive while consuming data.
}
```

`connect()` returns only after authentication succeeds, or after the selected initial connection policy fails/exhausts its retry budget.

## Recommended long-running configuration

For continuous market-data ingestion:

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

`FAIL_FAST` remains the SDK default for backward-compatible startup behavior. `RETRY` is recommended for services that should tolerate the gateway being temporarily unavailable during application startup. Authentication failures are not retried. `ReconnectPolicy.defaults()` still mirrors the Python SDK's 10-retry behavior; long-running ingestion services can opt into `ReconnectPolicy.forever(...)`.

## Subscribe an all-market universe

The application should load instruments and group each symbol by the correct DNSE board. Do not send one global symbol list to every board.

```java
Map<String, List<String>> symbolsByBoard = new LinkedHashMap<>();
symbolsByBoard.put("G1", g1Symbols);
symbolsByBoard.put("G3", g3Symbols);
symbolsByBoard.put("G4", g4Symbols);

SubscriptionOptions options = SubscriptionOptions.builder()
        .batchSize(200)
        .build();

BulkSubscriptionResult result = client.subscribeTrades(
        symbolsByBoard,
        options
);

// Enhanced matched trades (tick_extra.*) use the same bulk batching semantics:
BulkSubscriptionResult extraResult = client.subscribeTradeExtra(
        symbolsByBoard,
        options
);
```

The SDK deduplicates symbols, sends bounded batches sequentially and remembers the batch size for reconnect restoration.

`200` is an application starting point, not a documented DNSE protocol limit. Validate the value against the live gateway.

## Update the universe without reconnecting

When your instrument list changes, call:

```java
SubscriptionReconciliationResult result = client.reconcileTrades(
        desiredSymbolsByBoard,
        options
);

// For tick_extra.*:
SubscriptionReconciliationResult extraResult = client.reconcileTradeExtra(
        desiredSymbolsByBoard,
        options
);
```

The SDK compares current Trade subscriptions with the desired universe and sends only the delta:

```text
new symbol      -> subscribe
removed symbol  -> unsubscribe
unchanged       -> no wire operation
```

Example:

```text
Current G1: FPT, VNM, HPG
Desired G1: FPT, HPG, SSI
Desired G3: VCB

Sent:
subscribe   tick.G1 -> SSI
unsubscribe tick.G1 -> VNM
subscribe   tick.G3 -> VCB
```

After reconciliation, runtime reconnect restores the new desired state. Calling reconciliation again with the same universe produces zero subscribe/unsubscribe operations.

## Runtime reconnect behavior

After a successful connection, abnormal disconnects follow:

```text
AUTHENTICATED
  -> RECONNECTING
  -> CONNECTING
  -> CONNECTED
  -> AUTHENTICATING
  -> AUTHENTICATED
  -> restore active subscriptions in safe batches
  -> READY
```

A peer `1001 Going Away` close is treated as recoverable unless the application explicitly closed the client. During reconnect/restore, `isHealthy()` may already reflect an authenticated transport while `isReady()` remains false until all locally tracked subscriptions have been queued for restoration successfully.

Do not add a competing reconnect loop in Spring or application code.

## Subscription confirmation semantics

```java
SubscriptionResult result = client
        .subscribeTradesAsync(List.of("FPT"), "G1")
        .join();

assert result.confirmation() == SubscriptionConfirmation.TRANSPORT_ACCEPTED;
```

`TRANSPORT_ACCEPTED` means the outgoing WebSocket message was accepted by the transport and stored in local reconnect state. It deliberately does not claim a DNSE subscribe ACK because the Python SDK flow used as the compatibility source does not consume a subscribe ACK/request id.

A later DNSE `action=error` message with channel details is exposed through `onError` as a structured `DnseSubscriptionException` when possible.

## Lifecycle and observability

```java
client.onStateChanged(event -> log.info(
        "DNSE {} -> {} session={} cause={}",
        event.previous(),
        event.current(),
        event.sessionId(),
        event.cause()
));

client.onBackpressure(event -> log.warn(
        "worker={} queue={}/{} blocked={}ms",
        event.workerIndex(),
        event.queueSize(),
        event.queueCapacity(),
        event.blockedFor().toMillis()
));

DispatcherStats stats = client.dispatcherStats();
```

Framework-neutral metrics callbacks are available through `DnseWebSocketMetricsListener`. A Spring application can bridge them to Micrometer/Prometheus and use:

```java
client.state();
client.sessionId();
client.lastPongAt();
client.isHealthy();          // transport/auth/heartbeat
client.isReady();            // healthy + subscriptions restored
client.subscriptionsReady();
client.subscriptionRestoreInProgress();
client.lastSubscriptionRestoreError();
```

for its own health/readiness integration.

## Typed subscription helpers

```text
subscribeTrades
subscribeTradeExtra
subscribeExpectedPrice
subscribeQuotes
subscribeSecurityDefinitions
subscribeOhlc
subscribeOhlcClosed
subscribeForeignTrading
subscribeMarketIndex
subscribeEstimatedMarketIndex
subscribeMarketIndexInfluence
subscribeSession
subscribeOrderEvents
subscribeBrokerOrderEvents
subscribePositionEvents
subscribeBrokerPositionEvents
subscribeOrders
subscribePositions
subscribeAccount
```

## Protocol and reliability tests

The automated suite covers:

- HMAC authentication golden vector
- JSON and Python-generated MessagePack compatibility
- Python `_MSG_TYPE_MAP` parity
- welcome -> auth -> subscribe -> typed event dispatch
- runtime disconnect -> reconnect -> re-auth -> subscription restore
- initial connection `FAIL_FAST`
- initial connection `RETRY` until authentication succeeds
- welcome/authentication handshake timeout
- bounded and unlimited reconnect-policy behavior
- reconnect on peer `1001 Going Away`
- bulk Trade and TradeExtra subscription batching
- reconnect-safe batch restoration and readiness
- runtime Trade/TradeExtra universe reconciliation and converged local state
- same-symbol ordering under queue pressure
- partial unsubscribe state
- state-change callbacks
- metrics callbacks
- structured server subscription errors
- dispatcher/backpressure statistics
- graceful WebSocket close handshake
- graceful draining of already queued callback work before forced dispatcher shutdown

## Live smoke test

Normal CI does not use real DNSE credentials.

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

Never commit credentials or signatures into source/logs.

## REST quick start

```java
DnseRestConfig restConfig = DnseRestConfig.builder()
        .apiKey(System.getenv("DNSE_API_KEY"))
        .apiSecret(System.getenv("DNSE_API_SECRET"))
        .apiVersion("2026-07-23")
        .build();

try (DnseRestClient rest = new DnseRestClient(restConfig)) {
    DnseRestResponse response = rest.getInstruments(
            "FPT",
            null,
            null,
            null,
            20,
            1
    );

    System.out.println(response.statusCode());
    System.out.println(response.body());
}
```

The raw REST layer deliberately returns status/body for Python parity and supports every public endpoint method currently present in the Python REST client. A separate typed Market Data layer now provides typed instrument discovery without changing those raw APIs; see [REST_USAGE.md](REST_USAGE.md).

## Typed instrument discovery

```java
try (DnseMarketDataApi marketData = new DnseMarketDataApi(restConfig)) {
    InstrumentListResponse instruments =
            marketData.getInstruments(null, null, null, null, 500, 1);

    Map<String, List<String>> symbolsByBoard =
            instruments.symbolsByBoard();

    webSocketClient.subscribeTradeExtra(
            symbolsByBoard,
            SubscriptionOptions.builder().batchSize(200).build()
    );
}
```

This typed layer is additive; `DnseRestClient.getInstruments(...)` still returns the raw status/body response.

## Current scope

WebSocket conversion is feature-complete at code/CI level, pending live-gateway validation. REST signing/transport/dry-run and all public endpoint wrappers from the current Python REST client are implemented. Typed instrument discovery is now available as the first typed REST model.

Further typed DTOs should be added after validating real REST response schemas, while the existing raw APIs remain the compatibility fallback.

See [USAGE.md](USAGE.md) for the WebSocket integration guide and [REST_USAGE.md](REST_USAGE.md) for REST usage.
