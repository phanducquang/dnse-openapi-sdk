# DNSE OpenAPI Java SDK - REST Usage

The REST client is framework-independent Java 17 code under `vn.dnse.openapi.rest`. It ports the request-signing behavior and all public REST endpoint methods from `python/dnse/api/client.py`.

## Configuration

```java
DnseRestConfig config = DnseRestConfig.builder()
        .apiKey(System.getenv("DNSE_API_KEY"))
        .apiSecret(System.getenv("DNSE_API_SECRET"))
        .baseUrl("https://openapi.dnse.com.vn")
        .apiVersion("2026-07-23")
        .connectTimeout(Duration.ofSeconds(30))
        .readTimeout(Duration.ofSeconds(60))
        .build();

try (DnseRestClient client = new DnseRestClient(config)) {
    DnseRestResponse response = client.getInstruments(
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

Defaults mirror the current Python SDK:

```text
baseUrl             https://openapi.dnse.com.vn
algorithm           hmac-sha256
HMAC nonce          enabled
API version         2026-07-23
date header         Date
connect timeout     30s
read timeout        60s
```

## REST authentication parity

For each request the Java SDK builds the same signature shape as the Python SDK:

```text
(request-target): {lowercase-method} {path}
date: {RFC-2822 UTC date}
nonce: {uuid-hex}
```

Important: the signed request target contains the URL path only. Query parameters are not included in the signature by the current Python implementation.

The resulting request contains:

```text
Date
X-Signature
x-api-key
version
```

The HMAC result is Base64 encoded and then percent encoded before being placed in `X-Signature`.

## Raw response semantics

The first REST milestone intentionally mirrors Python behavior and returns raw HTTP data instead of introducing Java-only DTO assumptions:

```java
DnseRestResponse response = client.getLatestTrade("FPT", "G1");

Integer status = response.statusCode();
String body = response.body();

if (response.successful()) {
    // parse/use body
}
```

HTTP 4xx/5xx responses are returned with their status/body. Network or transport failures raise `DnseRestException`.

## Dry run

Every converted convenience method has a dry-run form. Dry run signs and builds the request but does not execute network I/O.

```java
DnseRestResponse response = client.getLatestTrade(
        "FPT",
        "G1",
        true
);

RestRequestPreview preview = response.requestPreview();

System.out.println(preview.method());
System.out.println(preview.url());
System.out.println(preview.headers());
```

Request previews contain authentication headers and must be treated as sensitive diagnostic data. Do not log or persist them in production.

## Converted Market Data APIs

The following Python Market Data REST APIs are available in Java:

```text
getSecurityDefinition
getOhlc
getTrades
getTradesVolumeProfile
getExpectedPrice
getQuotes
getForeignTrading
getMarketIndex
getInstruments
getLatestTrade
getLatestQuote
getClosePrice
getWorkingDates
getLatestSession
```

`getLastestSession` is also retained as a compatibility alias for the misspelled Python method name.

### Instruments

```java
DnseRestResponse response = client.getInstruments(
        null,       // symbol
        null,       // marketId
        null,       // securityGroupId
        null,       // indexName
        500,        // limit
        1           // page
);
```

To override the API version for this call and/or use dry-run:

```java
DnseRestResponse response = client.getInstruments(
        "FPT",
        null,
        null,
        null,
        20,
        1,
        "2026-07-23",
        true
);
```

### Security definition

```java
DnseRestResponse response =
        client.getSecurityDefinition("FPT", "G1");
```

### Trades

```java
DnseRestResponse response = client.getTrades(
        "FPT",
        "G1",
        "2026-09-01",
        "2026-09-19",
        100,
        "desc",
        null
);
```

### Trade volume profile

The Java SDK intentionally preserves the current Python query key `board_id` for this endpoint:

```java
client.getTradesVolumeProfile(
        "FPT",
        "2026-09-01",
        "2026-09-19",
        "G1"
);
```

### Quotes and latest quote

```java
client.getQuotes(
        "FPT",
        "G1",
        null,
        null,
        100,
        "desc",
        null
);

client.getLatestQuote("FPT", "G1");
```

### Expected price

```java
client.getExpectedPrice(
        "FPT",
        "G1",
        null,
        null,
        100,
        "desc",
        null
);
```

### Foreign trading

```java
client.getForeignTrading(
        "FPT",
        "G1",
        null,
        null,
        100,
        "desc",
        null
);
```

### Market index

```java
client.getMarketIndex(
        "VNINDEX",
        "2026-09-01",
        "2026-09-19",
        100,
        "desc",
        null
);
```

### OHLC

The Python API accepts an arbitrary query map and forces/overwrites the `type` query value with the supplied bar type. Java preserves that behavior.

```java
Map<String, Object> query = new LinkedHashMap<>();
query.put("symbol", "FPT");
query.put("from", "2026-09-01");
query.put("to", "2026-09-19");

client.getOhlc("1D", query);
```

### Close price, working dates and latest session

```java
client.getClosePrice("FPT", "G1");

client.getWorkingDates();

client.getLatestSession(
        "EQUITY",
        "G1"
);
```

## Account/read APIs

The Python account/read APIs are also available:

```text
getAccounts
getBalances
getLoanPackages
getPositions
getPositionById
getPositionPnlConfigs
getOrders
getOrderDetail
getExecutionDetail
getOrderHistory
getCorporateActionHistory
getPpse
getListCareBy
```

Examples:

```java
client.getAccounts();

client.getBalances("account-no");

client.getLoanPackages(
        "account-no",
        "STOCK",
        "FPT"
);

client.getOrders(
        "account-no",
        "STOCK",
        "NORMAL",
        1,
        50
);

client.getOrderHistory(
        "account-no",
        "STOCK",
        "2026-09-01",
        "2026-09-19",
        100,
        1
);
```

Methods that accept a Python per-request `version` override preserve that option in Java.

## Trading-token and write APIs

The remaining public Python REST endpoint wrappers are ported as well:

```text
postPositionPnlConfigs
sendEmailOtp
createTradingToken
postOrder
replaceOrder
putOrder
cancelOrder
closePosition
```

`replaceOrder` is the preferred Java name; `putOrder` is retained as a Python-compatible alias.

Write APIs accept the request body as an object/map and serialize it with Jackson. Endpoints that require it send the exact `trading-token` header used by Python.

Example request construction:

```java
Map<String, Object> payload = new LinkedHashMap<>();
payload.put("field", "value");

DnseRestResponse preview = client.postOrder(
        "account-no",
        "STOCK",
        payload,
        "trading-token",
        "NORMAL",
        null,
        true
);
```

The last argument above is `dryRun=true`, so no order is sent; it only builds the signed request preview.

## REST + WebSocket all-symbol flow

The REST client can now supply the instrument source for an application that wants to subscribe the WebSocket SDK to the market universe:

```text
DnseRestClient
   -> getInstruments(...)
   -> parse returned JSON
   -> group symbols by boardId
   -> DnseWebSocketClient.subscribeTradeExtra(...)
   -> periodic refresh
   -> DnseWebSocketClient.reconcileTradeExtra(...)
```

The REST response is still raw JSON in this milestone, so parsing/filtering the instrument payload remains in the consuming application. A future typed model layer can be added without changing REST signing or endpoint behavior.

## Live smoke test

Normal CI uses MockWebServer only. Live REST access is opt-in:

```bash
cd java

DNSE_REST_LIVE_TEST=true \
DNSE_API_KEY='<your-api-key>' \
DNSE_API_SECRET='<your-api-secret>' \
DNSE_API_VERSION='2026-07-23' \
gradle test --tests vn.dnse.openapi.rest.DnseRestLiveSmokeTest --stacktrace
```

Optional:

```text
DNSE_REST_BASE_URL   default: https://openapi.dnse.com.vn
DNSE_TEST_SYMBOL     default: FPT
DNSE_API_VERSION     default: 2026-07-23
```

The GitHub Actions `Java SDK Live Smoke` manual workflow can run WebSocket, REST, or both.

## Validation

Normal Java CI covers:

```text
Python-compatible REST signature golden vector
nonce-enabled and nonce-disabled signing
Date/API-version/X-Signature headers
query-string exclusion from the signed request target
dry-run with no network request
raw success response handling
raw HTTP error response handling
Market Data path/query parity
MockWebServer transport behavior
```

## Remaining REST work

All public endpoint methods currently present in `python/dnse/api/client.py` now have Java equivalents.

The main optional follow-up is a typed DTO layer for REST responses. The current raw `statusCode + body` response is intentionally retained for Python parity and to keep endpoint conversion independent from response-schema assumptions.
