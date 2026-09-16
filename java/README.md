# DNSE OpenAPI Java SDK

Java 17 SDK for DNSE OpenAPI. The current milestone focuses on WebSocket parity with the Python SDK before REST support is added.

## Current scope

- Java 17, framework-independent core
- HMAC-SHA256 WebSocket authentication compatible with the Python SDK
- JSON and MessagePack codecs
- OkHttp WebSocket transport
- connection/authentication state machine with lifecycle transition callbacks
- typed market/private events: Trade, TradeExtra, Quote, OHLC, ExpectedPrice, SecurityDefinition, ForeignInvestor, MarketIndex, EstimatedMarketIndex, IndexInfluence, Order, Position, Session and AccountUpdate
- typed subscription/unsubscription helpers for market-data and private channels
- async subscription result with explicit transport-confirmation semantics
- bulk/batched trade subscriptions with reconnect-safe batch restoration
- framework-neutral metrics hooks and dispatcher/backpressure statistics
- per-symbol ordered dispatch with bounded queues and blocking backpressure
- application heartbeat
- exponential-backoff reconnect, re-authentication and subscription restore
- MockWebServer protocol integration tests
- Python-generated MessagePack compatibility fixture tests

## Build and test

```bash
cd java
gradle test
```

The `Java SDK` GitHub Actions workflow runs the same test suite on Java 17 for pushes and pull requests that change the Java module. The live smoke test is guarded by `DNSE_LIVE_TEST=true`, so it is skipped during normal CI.

## Run the realtime market-data example

The executable example lives in a dedicated `example` source set, so it is not packaged into the SDK JAR. The normal test task also compiles the example to keep it validated by CI.

Configure your DNSE credentials as environment variables:

```bash
cd java

export DNSE_API_KEY='<your-api-key>'
export DNSE_API_SECRET='<your-api-secret>'

gradle run
```

By default the example connects to `wss://ws-openapi.dnse.com.vn`, authenticates, subscribes to `FPT` trades on board `G1`, then prints realtime trades until you press `Ctrl+C`.

Optional environment variables:

```text
DNSE_WS_BASE_URL   default: wss://ws-openapi.dnse.com.vn
DNSE_SYMBOLS       default: FPT; comma-separated, for example FPT,VNM,HPG
DNSE_BOARD         default: G1
DNSE_ENCODING      default: JSON; accepted values: JSON, MSGPACK
```

For compatibility with the live smoke test, `DNSE_TEST_SYMBOL` and `DNSE_TEST_BOARD` are also accepted as fallbacks when `DNSE_SYMBOLS` and `DNSE_BOARD` are not set.

For example:

```bash
DNSE_API_KEY='<your-api-key>' \
DNSE_API_SECRET='<your-api-secret>' \
DNSE_SYMBOLS='FPT,VNM,HPG' \
DNSE_BOARD='G1' \
DNSE_ENCODING='JSON' \
gradle run
```

## Basic usage

```java
DnseWebSocketConfig config = DnseWebSocketConfig.builder()
        .apiKey(System.getenv("DNSE_API_KEY"))
        .apiSecret(System.getenv("DNSE_API_SECRET"))
        .encoding(MessageEncoding.JSON)
        .build();

try (DnseWebSocketClient client = new DnseWebSocketClient(config)) {
    client.onTrade(System.out::println);
    client.onQuote(System.out::println);
    client.onError(Throwable::printStackTrace);

    client.connect().join();

    Subscription trades = client.subscribeTrades(List.of("FPT", "VNM"), "G1");
    Subscription quotes = client.subscribeQuotes(List.of("FPT", "VNM"), "G1");

    trades.unsubscribe().join();
    quotes.unsubscribe().join();
}
```

If no board is supplied, the SDK can subscribe across the Python SDK default boards:

```java
List<Subscription> subscriptions = client.subscribeTrades(List.of("FPT"));
```

Additional helpers include:

```text
subscribeTradeExtra
subscribeExpectedPrice
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

## Bulk subscriptions for large symbol universes

For all-market ingestion, group instruments by their correct DNSE board before calling the WebSocket SDK. Do not send one global symbol list to every board.

```java
Map<String, List<String>> symbolsByBoard = Map.of(
        "G1", List.of("FPT", "VNM", "HPG"),
        "G3", List.of("...")
);

BulkSubscriptionResult result = client.subscribeTrades(
        symbolsByBoard,
        SubscriptionOptions.builder()
                .batchSize(200)
                .build()
);

System.out.println("symbols=" + result.subscribedSymbols());
System.out.println("requests=" + result.subscriptionCount());
```

The SDK deduplicates symbols per board, sends batches sequentially, merges reconnect state, and remembers the configured batch size. After reconnect, restored subscriptions are split back into safe batches instead of being collapsed into one very large request.

The batch size is operational configuration. The current Python SDK does not document or consume a gateway maximum-symbol ACK, so choose a conservative value and validate it against the live DNSE gateway.

## Async subscription confirmation and server errors

```java
SubscriptionResult result = client
        .subscribeTradesAsync(List.of("FPT"), "G1")
        .join();

assert result.confirmation() == SubscriptionConfirmation.TRANSPORT_ACCEPTED;
```

`TRANSPORT_ACCEPTED` deliberately means the WebSocket transport accepted the outgoing message and the SDK stored the subscription locally. It does **not** claim that DNSE sent a subscribe ACK: the current Python SDK sends subscribe messages without consuming an ACK/request id.

If DNSE later sends an `action=error` payload containing channel details, the Java SDK exposes a structured `DnseSubscriptionException` through `onError`, including channel, symbols, error code and `serverReported=true` when those fields are available.

## Lifecycle and observability hooks

Long-running applications can observe connection transitions without polling:

```java
client.onStateChanged(event ->
        log.info("DNSE {} -> {} session={}",
                event.previous(),
                event.current(),
                event.sessionId())
);
```

Framework-neutral metrics hooks are available without adding Micrometer or Spring dependencies to the SDK:

```java
client.onMetrics(new DnseWebSocketMetricsListener() {
    @Override
    public void onReconnect(int attempt) {
        // increment application metric
    }

    @Override
    public void onSubscriptionAdded(String channel, int symbolCount) {
        // update application metric
    }
});
```

Backpressure can be observed directly:

```java
client.onBackpressure(event ->
        log.warn("worker={} queue={}/{} blocked={}ms",
                event.workerIndex(),
                event.queueSize(),
                event.queueCapacity(),
                event.blockedFor().toMillis())
);

DispatcherStats stats = client.dispatcherStats();
```

`DispatcherStats` exposes aggregate queue utilization, active workers, blocked-submission count and total blocking time. Application callbacks and metrics listeners should remain fast because they may execute on WebSocket or dispatcher threads.

## Protocol validation

The automated suite verifies:

- welcome -> authentication -> subscription -> typed event dispatch
- abnormal server close -> reconnect -> re-authentication -> subscription restore
- all message type codes currently mapped by the Python SDK
- same-symbol ordering under queue pressure
- partial unsubscribe state used for reconnect
- bulk subscription batching and reconnect-safe batch state
- connection-state transition callbacks
- framework-neutral metrics callbacks
- structured server subscription errors
- dispatcher/backpressure statistics
- JSON and Python-generated MessagePack payload compatibility
- graceful WebSocket close handshake

## Live smoke test

The final validation gate uses real DNSE credentials and is opt-in only. Never commit credentials into the repository.

Before merging the WebSocket implementation, run the live smoke test locally from the feature branch:

```bash
cd java
DNSE_LIVE_TEST=true \
DNSE_API_KEY='<your-api-key>' \
DNSE_API_SECRET='<your-api-secret>' \
gradle test --tests vn.dnse.openapi.websocket.DnseWebSocketLiveSmokeTest --stacktrace
```

Optional environment variables:

```text
DNSE_WS_BASE_URL   default: wss://ws-openapi.dnse.com.vn
DNSE_TEST_SYMBOL   default: FPT
DNSE_TEST_BOARD    default: G1
```

A separate `Java SDK Live Smoke` workflow is also included. Once that workflow is available on the repository default branch, it can be started manually through **Actions -> Java SDK Live Smoke -> Run workflow** using repository secrets:

```text
DNSE_API_KEY
DNSE_API_SECRET
```

The smoke test connects, authenticates and sends a market-data subscription. Credentials and signatures are not logged by the test.

## Compatibility source

Protocol behavior is ported from `python/dnse/websocket` in this repository. REST support remains intentionally deferred until the WebSocket implementation reaches protocol and live-runtime parity.
