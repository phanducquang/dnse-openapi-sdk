# DNSE OpenAPI Java SDK

Java 17 SDK for DNSE OpenAPI. The current milestone focuses on WebSocket parity with the Python SDK before REST support is added.

## Current scope

- Java 17, framework-independent core
- HMAC-SHA256 WebSocket authentication compatible with the Python SDK
- JSON and MessagePack codecs
- OkHttp WebSocket transport
- connection/authentication state machine
- typed market/private events: Trade, TradeExtra, Quote, OHLC, ExpectedPrice, SecurityDefinition, ForeignInvestor, MarketIndex, EstimatedMarketIndex, IndexInfluence, Order, Position, Session and AccountUpdate
- typed subscription/unsubscription helpers for market-data and private channels
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

The `Java SDK` GitHub Actions workflow runs the same test suite on Java 17 for pushes and pull requests that change the Java module.

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

    // Later:
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

## Protocol validation

The automated suite verifies:

- welcome -> authentication -> subscription -> typed event dispatch
- abnormal server close -> reconnect -> re-authentication -> subscription restore
- all message type codes currently mapped by the Python SDK
- same-symbol ordering under queue pressure
- partial unsubscribe state used for reconnect
- JSON and Python-generated MessagePack payload compatibility

## Live smoke test

The final validation gate uses real DNSE credentials and is opt-in only. Never commit credentials into the repository.

Before merging the WebSocket implementation, the live smoke test can be run locally from the feature branch:

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
