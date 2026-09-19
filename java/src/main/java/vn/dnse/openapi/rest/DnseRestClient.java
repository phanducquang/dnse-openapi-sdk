package vn.dnse.openapi.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;

/**
 * Framework-independent DNSE OpenAPI REST client.
 *
 * <p>This milestone intentionally returns the raw HTTP status/body, matching the Python SDK. It
 * owns request signing, query encoding, API-version headers, dry-run request preview and transport
 * reuse. Typed domain DTOs can be layered on top without changing wire behavior.</p>
 */
public final class DnseRestClient implements AutoCloseable {
    private static final MediaType JSON = MediaType.get("application/json");

    private final DnseRestConfig config;
    private final RestAuthSigner signer;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public DnseRestClient(DnseRestConfig config) {
        this(config, new ObjectMapper());
    }

    DnseRestClient(DnseRestConfig config, ObjectMapper objectMapper) {
        this.config = Objects.requireNonNull(config, "config");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.signer = new RestAuthSigner(config);
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(config.connectTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .readTimeout(config.readTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .build();
    }

    public DnseRestConfig config() {
        return config;
    }

    public DnseRestResponse getSecurityDefinition(String symbol, String boardId) {
        return getSecurityDefinition(symbol, boardId, false);
    }

    public DnseRestResponse getSecurityDefinition(String symbol, String boardId, boolean dryRun) {
        LinkedHashMap<String, Object> query = new LinkedHashMap<>();
        putIfNotNull(query, "boardId", boardId);
        return get("/price/" + required(symbol, "symbol") + "/secdef", query, null, dryRun);
    }

    public DnseRestResponse getOhlc(String barType, Map<String, ?> query) {
        return getOhlc(barType, query, false);
    }

    public DnseRestResponse getOhlc(String barType, Map<String, ?> query, boolean dryRun) {
        LinkedHashMap<String, Object> requestQuery = new LinkedHashMap<>();
        if (query != null) requestQuery.putAll(query);
        requestQuery.put("type", required(barType, "barType"));
        return get("/price/ohlc", requestQuery, null, dryRun);
    }

    public DnseRestResponse getTrades(
            String symbol,
            String boardId,
            String fromDate,
            String toDate,
            Integer limit,
            String order,
            String nextPageToken
    ) {
        return getTrades(symbol, boardId, fromDate, toDate, limit, order, nextPageToken, false);
    }

    public DnseRestResponse getTrades(
            String symbol,
            String boardId,
            String fromDate,
            String toDate,
            Integer limit,
            String order,
            String nextPageToken,
            boolean dryRun
    ) {
        return get(
                "/price/" + required(symbol, "symbol") + "/trades",
                historyQuery(boardId, fromDate, toDate, limit, order, nextPageToken),
                null,
                dryRun
        );
    }

    public DnseRestResponse getTradesVolumeProfile(
            String symbol,
            String fromDate,
            String toDate,
            String boardId
    ) {
        return getTradesVolumeProfile(symbol, fromDate, toDate, boardId, false);
    }

    public DnseRestResponse getTradesVolumeProfile(
            String symbol,
            String fromDate,
            String toDate,
            String boardId,
            boolean dryRun
    ) {
        LinkedHashMap<String, Object> query = new LinkedHashMap<>();
        query.put("from", required(fromDate, "fromDate"));
        query.put("to", required(toDate, "toDate"));
        // Keep Python SDK wire compatibility: this endpoint currently uses board_id, not boardId.
        putIfNotNull(query, "board_id", boardId);
        return get(
                "/price/" + required(symbol, "symbol") + "/trades/volume-profile",
                query,
                null,
                dryRun
        );
    }

    public DnseRestResponse getExpectedPrice(
            String symbol,
            String boardId,
            String fromDate,
            String toDate,
            Integer limit,
            String order,
            String nextPageToken
    ) {
        return getExpectedPrice(symbol, boardId, fromDate, toDate, limit, order, nextPageToken, false);
    }

    public DnseRestResponse getExpectedPrice(
            String symbol,
            String boardId,
            String fromDate,
            String toDate,
            Integer limit,
            String order,
            String nextPageToken,
            boolean dryRun
    ) {
        return get(
                "/price/" + required(symbol, "symbol") + "/expected-price",
                historyQuery(boardId, fromDate, toDate, limit, order, nextPageToken),
                null,
                dryRun
        );
    }

    public DnseRestResponse getQuotes(
            String symbol,
            String boardId,
            String fromDate,
            String toDate,
            Integer limit,
            String order,
            String nextPageToken
    ) {
        return getQuotes(symbol, boardId, fromDate, toDate, limit, order, nextPageToken, false);
    }

    public DnseRestResponse getQuotes(
            String symbol,
            String boardId,
            String fromDate,
            String toDate,
            Integer limit,
            String order,
            String nextPageToken,
            boolean dryRun
    ) {
        return get(
                "/price/" + required(symbol, "symbol") + "/quotes",
                historyQuery(boardId, fromDate, toDate, limit, order, nextPageToken),
                null,
                dryRun
        );
    }

    public DnseRestResponse getForeignTrading(
            String symbol,
            String boardId,
            String fromDate,
            String toDate,
            Integer limit,
            String order,
            String nextPageToken
    ) {
        return getForeignTrading(symbol, boardId, fromDate, toDate, limit, order, nextPageToken, false);
    }

    public DnseRestResponse getForeignTrading(
            String symbol,
            String boardId,
            String fromDate,
            String toDate,
            Integer limit,
            String order,
            String nextPageToken,
            boolean dryRun
    ) {
        return get(
                "/price/" + required(symbol, "symbol") + "/foreign-trading",
                historyQuery(boardId, fromDate, toDate, limit, order, nextPageToken),
                null,
                dryRun
        );
    }

    public DnseRestResponse getMarketIndex(
            String indexName,
            String fromDate,
            String toDate,
            Integer limit,
            String order,
            String nextPageToken
    ) {
        return getMarketIndex(indexName, fromDate, toDate, limit, order, nextPageToken, false);
    }

    public DnseRestResponse getMarketIndex(
            String indexName,
            String fromDate,
            String toDate,
            Integer limit,
            String order,
            String nextPageToken,
            boolean dryRun
    ) {
        LinkedHashMap<String, Object> query = historyQuery(
                null,
                fromDate,
                toDate,
                limit,
                order,
                nextPageToken
        );
        return get(
                "/price/" + required(indexName, "indexName") + "/market-index",
                query,
                null,
                dryRun
        );
    }

    public DnseRestResponse getInstruments(
            String symbol,
            String marketId,
            String securityGroupId,
            String indexName,
            Integer limit,
            Integer page
    ) {
        return getInstruments(symbol, marketId, securityGroupId, indexName, limit, page, null, false);
    }

    public DnseRestResponse getInstruments(
            String symbol,
            String marketId,
            String securityGroupId,
            String indexName,
            Integer limit,
            Integer page,
            String version,
            boolean dryRun
    ) {
        LinkedHashMap<String, Object> query = new LinkedHashMap<>();
        putIfNotNull(query, "symbol", symbol);
        putIfNotNull(query, "marketId", marketId);
        putIfNotNull(query, "securityGroupId", securityGroupId);
        putIfNotNull(query, "indexName", indexName);
        putIfNotNull(query, "limit", limit);
        putIfNotNull(query, "page", page);
        return get("/market/instruments", query, version, dryRun);
    }

    public DnseRestResponse getLatestTrade(String symbol, String boardId) {
        return getLatestTrade(symbol, boardId, false);
    }

    public DnseRestResponse getLatestTrade(String symbol, String boardId, boolean dryRun) {
        return get(
                "/price/" + required(symbol, "symbol") + "/trades/latest",
                optionalBoardQuery(boardId),
                null,
                dryRun
        );
    }

    public DnseRestResponse getLatestQuote(String symbol, String boardId) {
        return getLatestQuote(symbol, boardId, false);
    }

    public DnseRestResponse getLatestQuote(String symbol, String boardId, boolean dryRun) {
        return get(
                "/price/" + required(symbol, "symbol") + "/quotes/latest",
                optionalBoardQuery(boardId),
                null,
                dryRun
        );
    }

    public DnseRestResponse getClosePrice(String symbol, String boardId) {
        return getClosePrice(symbol, boardId, false);
    }

    public DnseRestResponse getClosePrice(String symbol, String boardId, boolean dryRun) {
        return get(
                "/price/" + required(symbol, "symbol") + "/close",
                optionalBoardQuery(boardId),
                null,
                dryRun
        );
    }

    public DnseRestResponse getWorkingDates() {
        return getWorkingDates(false);
    }

    public DnseRestResponse getWorkingDates(boolean dryRun) {
        return get("/market/working-dates", Map.of(), null, dryRun);
    }

    public DnseRestResponse getLatestSession(String tscProductGroupId, String boardId) {
        return getLatestSession(tscProductGroupId, boardId, false);
    }

    public DnseRestResponse getLatestSession(
            String tscProductGroupId,
            String boardId,
            boolean dryRun
    ) {
        LinkedHashMap<String, Object> query = new LinkedHashMap<>();
        putIfNotNull(query, "tscProdGrpId", tscProductGroupId);
        putIfNotNull(query, "boardId", boardId);
        return get("/market/trading-session", query, null, dryRun);
    }

    /** Compatibility alias for the misspelled Python method name get_lastest_session. */
    public DnseRestResponse getLastestSession(String tscProductGroupId, String boardId) {
        return getLatestSession(tscProductGroupId, boardId);
    }

    // -------------------------------------------------------------------------
    // Account/read APIs
    // -------------------------------------------------------------------------

    public DnseRestResponse getAccounts() {
        return getAccounts(false);
    }

    public DnseRestResponse getAccounts(boolean dryRun) {
        return get("/accounts", Map.of(), null, dryRun);
    }

    public DnseRestResponse getBalances(String accountNo) {
        return getBalances(accountNo, false);
    }

    public DnseRestResponse getBalances(String accountNo, boolean dryRun) {
        return get(
                "/accounts/" + required(accountNo, "accountNo") + "/balances",
                Map.of(),
                null,
                dryRun
        );
    }

    public DnseRestResponse getLoanPackages(
            String accountNo,
            String marketType,
            String symbol
    ) {
        return getLoanPackages(accountNo, marketType, symbol, false);
    }

    public DnseRestResponse getLoanPackages(
            String accountNo,
            String marketType,
            String symbol,
            boolean dryRun
    ) {
        LinkedHashMap<String, Object> query = new LinkedHashMap<>();
        query.put("marketType", required(marketType, "marketType"));
        putIfNotNull(query, "symbol", symbol);
        return get(
                "/accounts/" + required(accountNo, "accountNo") + "/loan-packages",
                query,
                null,
                dryRun
        );
    }

    public DnseRestResponse getPositions(String accountNo, String marketType) {
        return getPositions(accountNo, marketType, false);
    }

    public DnseRestResponse getPositions(
            String accountNo,
            String marketType,
            boolean dryRun
    ) {
        return get(
                "/accounts/" + required(accountNo, "accountNo") + "/positions",
                Map.of("marketType", required(marketType, "marketType")),
                null,
                dryRun
        );
    }

    public DnseRestResponse getPositionById(
            String marketType,
            String positionId,
            String version,
            boolean dryRun
    ) {
        return get(
                "/positions/" + required(positionId, "positionId"),
                Map.of("marketType", required(marketType, "marketType")),
                version,
                dryRun
        );
    }

    public DnseRestResponse getPositionById(String marketType, String positionId) {
        return getPositionById(marketType, positionId, null, false);
    }

    public DnseRestResponse getPositionPnlConfigs(
            String marketType,
            String positionId,
            String version,
            boolean dryRun
    ) {
        return get(
                "/positions/" + required(positionId, "positionId") + "/pnl-configs",
                Map.of("marketType", required(marketType, "marketType")),
                version,
                dryRun
        );
    }

    public DnseRestResponse getPositionPnlConfigs(String marketType, String positionId) {
        return getPositionPnlConfigs(marketType, positionId, null, false);
    }

    public DnseRestResponse getOrders(
            String accountNo,
            String marketType,
            String orderCategory,
            Integer pageIndex,
            Integer pageSize
    ) {
        return getOrders(
                accountNo,
                marketType,
                orderCategory,
                pageIndex,
                pageSize,
                false
        );
    }

    public DnseRestResponse getOrders(
            String accountNo,
            String marketType,
            String orderCategory,
            Integer pageIndex,
            Integer pageSize,
            boolean dryRun
    ) {
        LinkedHashMap<String, Object> query = new LinkedHashMap<>();
        query.put("marketType", required(marketType, "marketType"));
        putIfNotNull(query, "orderCategory", orderCategory);
        putIfNotNull(query, "pageIndex", pageIndex);
        putIfNotNull(query, "pageSize", pageSize);
        return get(
                "/accounts/" + required(accountNo, "accountNo") + "/orders",
                query,
                null,
                dryRun
        );
    }

    public DnseRestResponse getOrderDetail(
            String accountNo,
            String orderId,
            String marketType,
            String orderCategory
    ) {
        return getOrderDetail(
                accountNo,
                orderId,
                marketType,
                orderCategory,
                false
        );
    }

    public DnseRestResponse getOrderDetail(
            String accountNo,
            String orderId,
            String marketType,
            String orderCategory,
            boolean dryRun
    ) {
        LinkedHashMap<String, Object> query = new LinkedHashMap<>();
        query.put("marketType", required(marketType, "marketType"));
        putIfNotNull(query, "orderCategory", orderCategory);
        return get(
                "/accounts/" + required(accountNo, "accountNo")
                        + "/orders/" + required(orderId, "orderId"),
                query,
                null,
                dryRun
        );
    }

    public DnseRestResponse getExecutionDetail(
            String accountNo,
            String orderId,
            String marketType,
            String orderCategory
    ) {
        return getExecutionDetail(
                accountNo,
                orderId,
                marketType,
                orderCategory,
                false
        );
    }

    public DnseRestResponse getExecutionDetail(
            String accountNo,
            String orderId,
            String marketType,
            String orderCategory,
            boolean dryRun
    ) {
        LinkedHashMap<String, Object> query = new LinkedHashMap<>();
        query.put("marketType", required(marketType, "marketType"));
        if (orderCategory != null && !orderCategory.isBlank()) {
            query.put("orderCategory", orderCategory);
        }
        return get(
                "/accounts/" + required(accountNo, "accountNo")
                        + "/executions/" + required(orderId, "orderId"),
                query,
                null,
                dryRun
        );
    }

    public DnseRestResponse getOrderHistory(
            String accountNo,
            String marketType,
            String fromDate,
            String toDate,
            Integer pageSize,
            Integer pageIndex
    ) {
        return getOrderHistory(
                accountNo,
                marketType,
                fromDate,
                toDate,
                pageSize,
                pageIndex,
                false
        );
    }

    public DnseRestResponse getOrderHistory(
            String accountNo,
            String marketType,
            String fromDate,
            String toDate,
            Integer pageSize,
            Integer pageIndex,
            boolean dryRun
    ) {
        LinkedHashMap<String, Object> query = new LinkedHashMap<>();
        query.put("marketType", required(marketType, "marketType"));
        putIfNotNull(query, "from", fromDate);
        putIfNotNull(query, "to", toDate);
        putIfNotNull(query, "pageSize", pageSize);
        putIfNotNull(query, "pageIndex", pageIndex);
        return get(
                "/accounts/" + required(accountNo, "accountNo") + "/orders/history",
                query,
                null,
                dryRun
        );
    }

    public DnseRestResponse getCorporateActionHistory(
            String accountNo,
            String symbol,
            String caType,
            String caStatus,
            Integer pageIndex,
            Integer pageSize
    ) {
        return getCorporateActionHistory(
                accountNo,
                symbol,
                caType,
                caStatus,
                pageIndex,
                pageSize,
                false
        );
    }

    public DnseRestResponse getCorporateActionHistory(
            String accountNo,
            String symbol,
            String caType,
            String caStatus,
            Integer pageIndex,
            Integer pageSize,
            boolean dryRun
    ) {
        LinkedHashMap<String, Object> query = new LinkedHashMap<>();
        putIfNotNull(query, "symbol", symbol);
        putIfNotNull(query, "caType", caType);
        putIfNotNull(query, "caStatus", caStatus);
        putIfNotNull(query, "pageIndex", pageIndex);
        putIfNotNull(query, "pageSize", pageSize);
        return get(
                "/accounts/" + required(accountNo, "accountNo")
                        + "/corporate-action-history",
                query,
                null,
                dryRun
        );
    }

    public DnseRestResponse getPpse(
            String accountNo,
            String marketType,
            String symbol,
            Object price,
            Object loanPackageId
    ) {
        return getPpse(
                accountNo,
                marketType,
                symbol,
                price,
                loanPackageId,
                false
        );
    }

    public DnseRestResponse getPpse(
            String accountNo,
            String marketType,
            String symbol,
            Object price,
            Object loanPackageId,
            boolean dryRun
    ) {
        LinkedHashMap<String, Object> query = new LinkedHashMap<>();
        query.put("marketType", required(marketType, "marketType"));
        query.put("symbol", required(symbol, "symbol"));
        query.put("price", requiredObject(price, "price"));
        query.put("loanPackageId", requiredObject(loanPackageId, "loanPackageId"));
        return get(
                "/accounts/" + required(accountNo, "accountNo") + "/ppse",
                query,
                null,
                dryRun
        );
    }

    public DnseRestResponse getListCareBy() {
        return getListCareBy(false);
    }

    public DnseRestResponse getListCareBy(boolean dryRun) {
        return get("/brokers/accounts/care-by", Map.of(), null, dryRun);
    }

    // -------------------------------------------------------------------------
    // Trading-token and write APIs
    // -------------------------------------------------------------------------

    public DnseRestResponse postPositionPnlConfigs(
            String marketType,
            String positionId,
            Object payload,
            String tradingToken,
            String version,
            boolean dryRun
    ) {
        return request(
                "POST",
                "/positions/" + required(positionId, "positionId") + "/pnl-configs",
                Map.of("marketType", required(marketType, "marketType")),
                Objects.requireNonNull(payload, "payload"),
                tradingTokenHeaders(tradingToken),
                version,
                dryRun
        );
    }

    public DnseRestResponse postPositionPnlConfigs(
            String marketType,
            String positionId,
            Object payload,
            String tradingToken
    ) {
        return postPositionPnlConfigs(
                marketType,
                positionId,
                payload,
                tradingToken,
                null,
                false
        );
    }

    public DnseRestResponse sendEmailOtp() {
        return sendEmailOtp(false);
    }

    public DnseRestResponse sendEmailOtp(boolean dryRun) {
        return request(
                "POST",
                "/registration/send-email-otp",
                Map.of(),
                null,
                Map.of(),
                null,
                dryRun
        );
    }

    public DnseRestResponse createTradingToken(String otpType, String passcode) {
        return createTradingToken(otpType, passcode, false);
    }

    public DnseRestResponse createTradingToken(
            String otpType,
            String passcode,
            boolean dryRun
    ) {
        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("otpType", required(otpType, "otpType"));
        body.put("passcode", required(passcode, "passcode"));
        return request(
                "POST",
                "/registration/trading-token",
                Map.of(),
                body,
                Map.of(),
                null,
                dryRun
        );
    }

    public DnseRestResponse postOrder(
            String accountNo,
            String marketType,
            Object payload,
            String tradingToken
    ) {
        return postOrder(
                accountNo,
                marketType,
                payload,
                tradingToken,
                "NORMAL",
                null,
                false
        );
    }

    public DnseRestResponse postOrder(
            String accountNo,
            String marketType,
            Object payload,
            String tradingToken,
            String orderCategory,
            String version,
            boolean dryRun
    ) {
        LinkedHashMap<String, Object> query = new LinkedHashMap<>();
        query.put("marketType", required(marketType, "marketType"));
        if (orderCategory != null && !orderCategory.isBlank()) {
            query.put("orderCategory", orderCategory);
        }
        return request(
                "POST",
                "/accounts/" + required(accountNo, "accountNo") + "/orders",
                query,
                Objects.requireNonNull(payload, "payload"),
                tradingTokenHeaders(tradingToken),
                version,
                dryRun
        );
    }

    public DnseRestResponse replaceOrder(
            String accountNo,
            String orderId,
            String marketType,
            Object payload,
            String tradingToken,
            String orderCategory,
            boolean dryRun
    ) {
        LinkedHashMap<String, Object> query = new LinkedHashMap<>();
        query.put("marketType", required(marketType, "marketType"));
        if (orderCategory != null && !orderCategory.isBlank()) {
            query.put("orderCategory", orderCategory);
        }
        return request(
                "PUT",
                "/accounts/" + required(accountNo, "accountNo")
                        + "/orders/" + required(orderId, "orderId"),
                query,
                Objects.requireNonNull(payload, "payload"),
                tradingTokenHeaders(tradingToken),
                null,
                dryRun
        );
    }

    public DnseRestResponse replaceOrder(
            String accountNo,
            String orderId,
            String marketType,
            Object payload,
            String tradingToken
    ) {
        return replaceOrder(
                accountNo,
                orderId,
                marketType,
                payload,
                tradingToken,
                null,
                false
        );
    }

    /** Python-compatible alias for put_order. */
    public DnseRestResponse putOrder(
            String accountNo,
            String orderId,
            String marketType,
            Object payload,
            String tradingToken,
            String orderCategory,
            boolean dryRun
    ) {
        return replaceOrder(
                accountNo,
                orderId,
                marketType,
                payload,
                tradingToken,
                orderCategory,
                dryRun
        );
    }

    public DnseRestResponse cancelOrder(
            String accountNo,
            String orderId,
            String marketType,
            String tradingToken,
            String orderCategory
    ) {
        return cancelOrder(
                accountNo,
                orderId,
                marketType,
                tradingToken,
                orderCategory,
                false
        );
    }

    public DnseRestResponse cancelOrder(
            String accountNo,
            String orderId,
            String marketType,
            String tradingToken,
            String orderCategory,
            boolean dryRun
    ) {
        LinkedHashMap<String, Object> query = new LinkedHashMap<>();
        query.put("marketType", required(marketType, "marketType"));
        if (orderCategory != null && !orderCategory.isBlank()) {
            query.put("orderCategory", orderCategory);
        }
        return request(
                "DELETE",
                "/accounts/" + required(accountNo, "accountNo")
                        + "/orders/" + required(orderId, "orderId"),
                query,
                null,
                tradingTokenHeaders(tradingToken),
                null,
                dryRun
        );
    }

    public DnseRestResponse closePosition(
            String positionId,
            String marketType,
            String tradingToken,
            String version
    ) {
        return closePosition(
                positionId,
                marketType,
                tradingToken,
                version,
                false
        );
    }

    public DnseRestResponse closePosition(
            String positionId,
            String marketType,
            String tradingToken,
            String version,
            boolean dryRun
    ) {
        return request(
                "POST",
                "/positions/" + required(positionId, "positionId") + "/close",
                Map.of("marketType", required(marketType, "marketType")),
                null,
                tradingTokenHeaders(tradingToken),
                version,
                dryRun
        );
    }

    /** Low-level signed GET helper for endpoints not yet covered by a typed convenience method. */
    public DnseRestResponse get(
            String path,
            Map<String, ?> query,
            String version,
            boolean dryRun
    ) {
        return request("GET", path, query, null, Map.of(), version, dryRun);
    }

    private DnseRestResponse request(
            String method,
            String path,
            Map<String, ?> query,
            Object body,
            Map<String, String> additionalHeaders,
            String version,
            boolean dryRun
    ) {
        required(path, "path");
        RestSignature signature = signer.sign(method, path);
        String url = buildUrl(path, query);
        String bodyJson = serializeBody(body);

        LinkedHashMap<String, String> headers = new LinkedHashMap<>();
        headers.put(signature.dateHeaderName(), signature.dateValue());
        headers.put("X-Signature", signature.authorizationValue());
        headers.put("x-api-key", config.apiKey());
        headers.put("version", version == null || version.isBlank() ? config.apiVersion() : version);
        if (bodyJson != null) headers.put("Content-Type", "application/json");
        if (additionalHeaders != null) headers.putAll(additionalHeaders);

        if (dryRun) {
            return DnseRestResponse.dryRun(
                    new RestRequestPreview(method, url, headers, bodyJson)
            );
        }

        Request.Builder requestBuilder = new Request.Builder().url(url);
        headers.forEach(requestBuilder::header);

        RequestBody requestBody = bodyJson == null ? null : RequestBody.create(bodyJson, JSON);
        if (requestBody == null && requiresRequestBody(method)) {
            requestBody = RequestBody.create(new byte[0], null);
        }
        requestBuilder.method(method, requestBody);

        try (Response response = httpClient.newCall(requestBuilder.build()).execute()) {
            ResponseBody responseBody = response.body();
            return DnseRestResponse.live(
                    response.code(),
                    responseBody == null ? "" : responseBody.string()
            );
        } catch (IOException e) {
            throw new DnseRestException(
                    "DNSE REST request failed: " + method + " " + path,
                    e
            );
        }
    }

    private String buildUrl(String path, Map<String, ?> query) {
        StringBuilder url = new StringBuilder(config.baseUrl()).append(path);
        if (query == null || query.isEmpty()) return url.toString();

        StringJoiner joiner = new StringJoiner("&");
        for (Map.Entry<String, ?> entry : query.entrySet()) {
            if (entry.getValue() == null) continue;
            joiner.add(
                    encode(entry.getKey()) + "=" + encode(String.valueOf(entry.getValue()))
            );
        }
        String encodedQuery = joiner.toString();
        if (!encodedQuery.isEmpty()) url.append('?').append(encodedQuery);
        return url.toString();
    }

    private String serializeBody(Object body) {
        if (body == null) return null;
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new DnseRestException("Unable to serialize DNSE REST request body", e);
        }
    }

    private static boolean requiresRequestBody(String method) {
        return "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method);
    }

    private static LinkedHashMap<String, Object> historyQuery(
            String boardId,
            String fromDate,
            String toDate,
            Integer limit,
            String order,
            String nextPageToken
    ) {
        LinkedHashMap<String, Object> query = new LinkedHashMap<>();
        putIfNotNull(query, "boardId", boardId);
        putIfNotNull(query, "from", fromDate);
        putIfNotNull(query, "to", toDate);
        putIfNotNull(query, "limit", limit);
        putIfNotNull(query, "order", order);
        putIfNotNull(query, "nextPageToken", nextPageToken);
        return query;
    }

    private static LinkedHashMap<String, Object> optionalBoardQuery(String boardId) {
        LinkedHashMap<String, Object> query = new LinkedHashMap<>();
        putIfNotNull(query, "boardId", boardId);
        return query;
    }

    private static void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) target.put(key, value);
    }

    private static Map<String, String> tradingTokenHeaders(String tradingToken) {
        return Map.of("trading-token", required(tradingToken, "tradingToken"));
    }

    private static Object requiredObject(Object value, String name) {
        if (value == null) throw new IllegalArgumentException(name + " must not be null");
        return value;
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }
}
