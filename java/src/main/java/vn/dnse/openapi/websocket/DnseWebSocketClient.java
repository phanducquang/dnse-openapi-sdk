package vn.dnse.openapi.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.dnse.openapi.websocket.auth.WebSocketAuthManager;
import vn.dnse.openapi.websocket.codec.JsonMessageCodec;
import vn.dnse.openapi.websocket.codec.MessageCodec;
import vn.dnse.openapi.websocket.codec.MessagePackCodec;
import vn.dnse.openapi.websocket.connection.OkHttpWebSocketTransport;
import vn.dnse.openapi.websocket.connection.WebSocketTransport;
import vn.dnse.openapi.websocket.dispatcher.BackpressureEvent;
import vn.dnse.openapi.websocket.dispatcher.DispatcherStats;
import vn.dnse.openapi.websocket.dispatcher.MessageMapper;
import vn.dnse.openapi.websocket.dispatcher.StripedEventExecutor;
import vn.dnse.openapi.websocket.exception.DnseAuthenticationException;
import vn.dnse.openapi.websocket.exception.DnseConnectionException;
import vn.dnse.openapi.websocket.exception.DnseSubscriptionException;
import vn.dnse.openapi.websocket.exception.DnseWebSocketException;
import vn.dnse.openapi.websocket.metrics.DnseWebSocketMetricsListener;
import vn.dnse.openapi.websocket.model.*;
import vn.dnse.openapi.websocket.subscription.BulkSubscriptionResult;
import vn.dnse.openapi.websocket.subscription.ChannelBuilder;
import vn.dnse.openapi.websocket.subscription.Subscription;
import vn.dnse.openapi.websocket.subscription.SubscriptionConfirmation;
import vn.dnse.openapi.websocket.subscription.SubscriptionManager;
import vn.dnse.openapi.websocket.subscription.SubscriptionOptions;
import vn.dnse.openapi.websocket.subscription.SubscriptionReconciliationResult;
import vn.dnse.openapi.websocket.subscription.SubscriptionResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.CancellationException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * High-level DNSE OpenAPI WebSocket client for realtime market data and private account events.
 *
 * <p>The client owns connection/authentication state, subscriptions, heartbeat, typed message mapping,
 * per-symbol ordered callback dispatch, startup retry (when enabled), automatic runtime reconnect,
 * re-authentication, subscription restoration and runtime trade-universe reconciliation.</p>
 */
public final class DnseWebSocketClient implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(DnseWebSocketClient.class);

    public static final List<String> DEFAULT_BOARDS = List.of("G1", "G3", "G4", "G7", "T1", "T2", "T3", "T4", "T6");
    public static final List<String> DEFAULT_QUOTE_BOARDS = List.of("G1", "G2", "G3", "G4", "G5", "G6", "G7");
    public static final List<String> DEFAULT_OHLC_RESOLUTIONS = List.of("1", "3", "5", "15", "30", "1H", "1D", "1W");

    private final DnseWebSocketConfig config;
    private final MessageCodec codec;
    private final WebSocketAuthManager authManager;
    private final SubscriptionManager subscriptions = new SubscriptionManager();
    private final StripedEventExecutor dispatcher;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Map<Class<?>, CopyOnWriteArrayList<Consumer<?>>> handlers = new ConcurrentHashMap<>();
    private final Map<String, CopyOnWriteArrayList<Consumer<Object>>> eventHandlers = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Consumer<Throwable>> errorHandlers = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<ConnectionStateEvent>> stateHandlers = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<BackpressureEvent>> backpressureHandlers = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<DnseWebSocketMetricsListener> metricsListeners = new CopyOnWriteArrayList<>();
    private final AtomicReference<ConnectionState> state = new AtomicReference<>(ConnectionState.DISCONNECTED);
    private final AtomicBoolean intentionallyClosed = new AtomicBoolean(false);
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean(false);
    private final AtomicBoolean initialConnectionInProgress = new AtomicBoolean(false);
    private final AtomicBoolean heartbeatStarted = new AtomicBoolean(false);

    private volatile WebSocketTransport transport;
    private volatile String sessionId;
    private volatile Instant lastPongAt = Instant.EPOCH;
    private volatile CompletableFuture<Void> authenticationFuture;
    private volatile CompletableFuture<Void> initialConnectionFuture;
    private volatile int reconnectAttempt;

    public DnseWebSocketClient(DnseWebSocketConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.codec = config.encoding() == MessageEncoding.JSON ? new JsonMessageCodec() : new MessagePackCodec();
        this.authManager = new WebSocketAuthManager(config.apiKey(), config.apiSecret(), config.clock(), config.nonceGenerator());
        this.dispatcher = new StripedEventExecutor(config.dispatchWorkers(), config.queueCapacity(), this::emitBackpressure);
    }

    public static DnseWebSocketConfig.Builder builder() { return DnseWebSocketConfig.builder(); }

    /**
     * Connects and authenticates. In {@link InitialConnectionPolicy#RETRY} mode, retryable startup
     * failures use {@link ReconnectPolicy} max-retries/backoff before this future fails.
     */
    public synchronized CompletableFuture<Void> connect() {
        if (initialConnectionInProgress.get()) {
            return initialConnectionFuture;
        }
        if (state.get() == ConnectionState.AUTHENTICATED && transport != null && transport.isConnected()) {
            return CompletableFuture.completedFuture(null);
        }

        intentionallyClosed.set(false);
        reconnectAttempt = 0;
        reconnectScheduled.set(false);
        sessionId = null;
        initialConnectionInProgress.set(true);
        CompletableFuture<Void> result = new CompletableFuture<>();
        initialConnectionFuture = result;
        transitionTo(ConnectionState.CONNECTING, null);
        attemptInitialConnection(0, result);
        return result.thenRun(this::startHeartbeat);
    }

    private void attemptInitialConnection(int retriesUsed, CompletableFuture<Void> result) {
        connectAndAuthenticate().whenComplete((ignored, error) -> {
            if (result.isDone()) return;
            if (error == null) {
                initialConnectionInProgress.set(false);
                reconnectAttempt = 0;
                result.complete(null);
                return;
            }

            Throwable cause = unwrap(error);
            closeFailedTransport();
            if (!shouldRetryInitialConnection(cause, retriesUsed)) {
                initialConnectionInProgress.set(false);
                transitionTo(ConnectionState.DISCONNECTED, cause);
                result.completeExceptionally(cause);
                return;
            }

            int attempt = retriesUsed + 1;
            reconnectAttempt = attempt;
            transitionTo(ConnectionState.RECONNECTING, cause);
            emitMetrics(listener -> listener.onReconnect(attempt));
            long delay = config.reconnectPolicy().delayForAttempt(attempt).toMillis();
            scheduler.schedule(() -> {
                if (intentionallyClosed.get() || result.isDone()) {
                    if (!result.isDone()) result.completeExceptionally(new CancellationException("DNSE connection was closed during startup retry"));
                    initialConnectionInProgress.set(false);
                    return;
                }
                sessionId = null;
                transitionTo(ConnectionState.CONNECTING, null);
                attemptInitialConnection(attempt, result);
            }, delay, TimeUnit.MILLISECONDS);
        });
    }

    private boolean shouldRetryInitialConnection(Throwable cause, int retriesUsed) {
        if (config.initialConnectionPolicy() != InitialConnectionPolicy.RETRY) return false;
        if (!config.reconnectPolicy().enabled()) return false;
        if (cause instanceof DnseAuthenticationException) return false;
        return retriesUsed < config.reconnectPolicy().maxRetries();
    }

    private void closeFailedTransport() {
        WebSocketTransport current = transport;
        if (current != null && current.isConnected()) {
            current.disconnect().exceptionally(error -> {
                log.debug("Failed to close unsuccessful DNSE connection attempt", error);
                return null;
            });
        }
    }

    private CompletableFuture<Void> connectAndAuthenticate() {
        OkHttpWebSocketTransport candidate = new OkHttpWebSocketTransport(config.connectTimeout());
        CompletableFuture<Void> authFuture = new CompletableFuture<>();
        this.transport = candidate;
        this.authenticationFuture = authFuture;
        String url = config.baseUrl() + "/v1/stream?encoding=" + config.encoding().wireName();
        return candidate.connect(url, new TransportListener(candidate, authFuture))
                .thenCompose(ignored -> authFuture);
    }

    public List<Subscription> subscribeTrades(List<String> symbols) {
        return subscribeAcross(symbols, DEFAULT_BOARDS, board -> ChannelBuilder.trades(board, config.encoding()));
    }

    public Subscription subscribeTrades(List<String> symbols, String boardId) {
        return subscribe(ChannelBuilder.trades(boardId, config.encoding()), symbols);
    }

    public CompletableFuture<SubscriptionResult> subscribeTradesAsync(List<String> symbols, String boardId) {
        return subscribeAsync(ChannelBuilder.trades(boardId, config.encoding()), symbols);
    }

    /** Bulk trade subscription keyed by board id with deduplication and bounded batches. */
    public BulkSubscriptionResult subscribeTrades(Map<String, List<String>> symbolsByBoard, SubscriptionOptions options) {
        return joinSubscriptionFuture(subscribeTradesAsync(symbolsByBoard, options));
    }

    public CompletableFuture<BulkSubscriptionResult> subscribeTradesAsync(
            Map<String, List<String>> symbolsByBoard,
            SubscriptionOptions options
    ) {
        Objects.requireNonNull(symbolsByBoard, "symbolsByBoard");
        Map<String, List<String>> symbolsByChannel = new LinkedHashMap<>();
        symbolsByBoard.forEach((board, symbols) ->
                symbolsByChannel.put(ChannelBuilder.trades(board, config.encoding()), symbols));
        return subscribeBulkAsync(symbolsByChannel, options);
    }

    /**
     * Reconciles the active trade universe against the desired symbols grouped by board. Only the
     * delta is sent: newly desired symbols are subscribed and stale symbols are unsubscribed.
     */
    public SubscriptionReconciliationResult reconcileTrades(
            Map<String, List<String>> desiredSymbolsByBoard,
            SubscriptionOptions options
    ) {
        return joinSubscriptionFuture(reconcileTradesAsync(desiredSymbolsByBoard, options));
    }

    public CompletableFuture<SubscriptionReconciliationResult> reconcileTradesAsync(
            Map<String, List<String>> desiredSymbolsByBoard,
            SubscriptionOptions options
    ) {
        Objects.requireNonNull(desiredSymbolsByBoard, "desiredSymbolsByBoard");
        Objects.requireNonNull(options, "options");
        if (state.get() != ConnectionState.AUTHENTICATED) {
            return CompletableFuture.failedFuture(new DnseSubscriptionException("Must authenticate before reconciling subscriptions"));
        }

        Map<String, List<String>> desiredByChannel = new LinkedHashMap<>();
        desiredSymbolsByBoard.forEach((board, symbols) -> desiredByChannel.put(
                ChannelBuilder.trades(board, config.encoding()),
                deduplicate(Objects.requireNonNull(symbols, "symbols"))
        ));

        Map<String, SubscriptionManager.Entry> currentTradeChannels = new LinkedHashMap<>();
        for (SubscriptionManager.Entry entry : subscriptions.snapshot()) {
            if (isTradeChannel(entry.channel())) currentTradeChannels.put(entry.channel(), entry);
        }

        Set<String> channels = new LinkedHashSet<>(desiredByChannel.keySet());
        channels.addAll(currentTradeChannels.keySet());

        int[] desiredCount = {0};
        int[] addedCount = {0};
        int[] removedCount = {0};
        int[] unchangedCount = {0};
        int[] subscribeOperations = {0};
        int[] unsubscribeOperations = {0};
        int[] activeChannels = {0};
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);

        for (String channel : channels) {
            List<String> desired = desiredByChannel.getOrDefault(channel, List.of());
            SubscriptionManager.Entry currentEntry = currentTradeChannels.get(channel);
            List<String> current = currentEntry == null ? List.of() : currentEntry.symbols();
            boolean currentChannelOnly = currentEntry != null && current.isEmpty();

            List<String> added = difference(desired, current);
            List<String> removed = difference(current, desired);
            int unchanged = intersectionSize(current, desired);

            desiredCount[0] += desired.size();
            addedCount[0] += added.size();
            removedCount[0] += removed.size();
            unchangedCount[0] += unchanged;
            if (!desired.isEmpty()) activeChannels[0]++;

            if (currentChannelOnly) {
                chain = chain.thenCompose(ignored -> unsubscribeChannel(channel, List.of()));
                unsubscribeOperations[0]++;
            }

            for (List<String> batch : batches(added, options.batchSize())) {
                chain = chain.thenCompose(ignored ->
                        subscribeAsyncInternal(channel, batch, options.batchSize()).thenAccept(result -> {}));
                subscribeOperations[0]++;
            }

            for (List<String> batch : batches(removed, options.batchSize())) {
                chain = chain.thenCompose(ignored -> unsubscribeChannel(channel, batch));
                unsubscribeOperations[0]++;
            }

            chain = chain.thenRun(() -> {
                if (desired.isEmpty()) subscriptions.remove(channel);
                else subscriptions.put(channel, desired, options.batchSize());
            });
        }

        return chain.thenApply(ignored -> new SubscriptionReconciliationResult(
                desiredCount[0],
                addedCount[0],
                removedCount[0],
                unchangedCount[0],
                subscribeOperations[0],
                unsubscribeOperations[0],
                activeChannels[0]
        ));
    }

    public List<Subscription> subscribeTradeExtra(List<String> symbols) {
        return subscribeAcross(symbols, DEFAULT_BOARDS, board -> ChannelBuilder.tradeExtra(board, config.encoding()));
    }

    public Subscription subscribeTradeExtra(List<String> symbols, String boardId) {
        return subscribe(ChannelBuilder.tradeExtra(boardId, config.encoding()), symbols);
    }

    public List<Subscription> subscribeExpectedPrice(List<String> symbols) {
        return subscribeAcross(symbols, DEFAULT_BOARDS, board -> ChannelBuilder.expectedPrice(board, config.encoding()));
    }

    public Subscription subscribeExpectedPrice(List<String> symbols, String boardId) {
        return subscribe(ChannelBuilder.expectedPrice(boardId, config.encoding()), symbols);
    }

    public List<Subscription> subscribeQuotes(List<String> symbols) {
        return subscribeAcross(symbols, DEFAULT_QUOTE_BOARDS, board -> ChannelBuilder.quotes(board, config.encoding()));
    }

    public Subscription subscribeQuotes(List<String> symbols, String boardId) {
        return subscribe(ChannelBuilder.quotes(boardId, config.encoding()), symbols);
    }

    public List<Subscription> subscribeSecurityDefinitions(List<String> symbols) {
        return subscribeAcross(symbols, DEFAULT_BOARDS, board -> ChannelBuilder.securityDefinition(board, config.encoding()));
    }

    public Subscription subscribeSecurityDefinitions(List<String> symbols, String boardId) {
        return subscribe(ChannelBuilder.securityDefinition(boardId, config.encoding()), symbols);
    }

    public List<Subscription> subscribeOhlc(List<String> symbols) {
        return subscribeAcross(symbols, DEFAULT_OHLC_RESOLUTIONS, resolution -> ChannelBuilder.ohlc(resolution, config.encoding()));
    }

    public Subscription subscribeOhlc(List<String> symbols, String resolution) {
        return subscribe(ChannelBuilder.ohlc(resolution, config.encoding()), symbols);
    }

    public List<Subscription> subscribeOhlcClosed(List<String> symbols) {
        return subscribeAcross(symbols, DEFAULT_OHLC_RESOLUTIONS, resolution -> ChannelBuilder.ohlcClosed(resolution, config.encoding()));
    }

    public Subscription subscribeOhlcClosed(List<String> symbols, String resolution) {
        return subscribe(ChannelBuilder.ohlcClosed(resolution, config.encoding()), symbols);
    }

    public Subscription subscribeForeignTrading(List<String> symbols) {
        return subscribeForeignTrading(symbols, "*");
    }

    public Subscription subscribeForeignTrading(List<String> symbols, String boardId) {
        return subscribe(ChannelBuilder.foreignTrading(boardId, config.encoding()), symbols);
    }

    public Subscription subscribeMarketIndex(String indexName) {
        return subscribe(ChannelBuilder.marketIndex(indexName, config.encoding()), List.of());
    }

    public Subscription subscribeEstimatedMarketIndex(String indexName) {
        return subscribe(ChannelBuilder.estimatedMarketIndex(indexName, config.encoding()), List.of());
    }

    public Subscription subscribeMarketIndexInfluence(String indexName, int resolution) {
        return subscribe(ChannelBuilder.marketIndexInfluence(indexName, resolution, config.encoding()), List.of());
    }

    public Subscription subscribeSession(String productGroupId) {
        return subscribeSession(productGroupId, "*");
    }

    public Subscription subscribeSession(String productGroupId, String boardId) {
        return subscribe(ChannelBuilder.session(productGroupId, boardId, config.encoding()), List.of());
    }

    public Subscription subscribeOrderEvents() { return subscribeOrderEvents("STOCK"); }
    public Subscription subscribeOrderEvents(String marketType) { return subscribe(ChannelBuilder.order(marketType, config.encoding()), List.of()); }
    public Subscription subscribeBrokerOrderEvents(String investorId) { return subscribeBrokerOrderEvents(investorId, "STOCK"); }
    public Subscription subscribeBrokerOrderEvents(String investorId, String marketType) { return subscribe(ChannelBuilder.brokerOrder(marketType, investorId, config.encoding()), List.of()); }
    public Subscription subscribePositionEvents() { return subscribePositionEvents("STOCK"); }
    public Subscription subscribePositionEvents(String marketType) { return subscribe(ChannelBuilder.position(marketType, config.encoding()), List.of()); }
    public Subscription subscribeBrokerPositionEvents(String investorId) { return subscribeBrokerPositionEvents(investorId, "STOCK"); }
    public Subscription subscribeBrokerPositionEvents(String investorId, String marketType) { return subscribe(ChannelBuilder.brokerPosition(marketType, investorId, config.encoding()), List.of()); }
    public Subscription subscribeOrders() { return subscribe(ChannelBuilder.orders(), List.of()); }
    public Subscription subscribePositions() { return subscribe(ChannelBuilder.positions(), List.of()); }
    public Subscription subscribeAccount() { return subscribe(ChannelBuilder.account(), List.of()); }

    /**
     * Low-level synchronous subscription API. Completion means the WebSocket transport accepted the
     * message and reconnect state was updated; DNSE's Python SDK does not consume a subscribe ACK.
     */
    public Subscription subscribe(String channel, List<String> symbols) {
        ensureAuthenticated(channel, symbols);
        return joinSubscriptionFuture(subscribeAsyncInternal(channel, symbols, Math.max(1, symbols.size()))).subscription();
    }

    public CompletableFuture<SubscriptionResult> subscribeAsync(String channel, List<String> symbols) {
        if (state.get() != ConnectionState.AUTHENTICATED) {
            return CompletableFuture.failedFuture(new DnseSubscriptionException(
                    "Must authenticate before subscribing", channel, symbols, null, false));
        }
        return subscribeAsyncInternal(channel, symbols, Math.max(1, symbols.size()));
    }

    public BulkSubscriptionResult subscribeBulk(Map<String, List<String>> symbolsByChannel, SubscriptionOptions options) {
        return joinSubscriptionFuture(subscribeBulkAsync(symbolsByChannel, options));
    }

    public CompletableFuture<BulkSubscriptionResult> subscribeBulkAsync(
            Map<String, List<String>> symbolsByChannel,
            SubscriptionOptions options
    ) {
        Objects.requireNonNull(symbolsByChannel, "symbolsByChannel");
        Objects.requireNonNull(options, "options");
        if (state.get() != ConnectionState.AUTHENTICATED) {
            return CompletableFuture.failedFuture(new DnseSubscriptionException("Must authenticate before subscribing"));
        }

        int requestedSymbols = symbolsByChannel.values().stream().mapToInt(List::size).sum();
        List<Subscription> handles = new ArrayList<>();
        int[] subscribedSymbols = {0};
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);

        for (Map.Entry<String, List<String>> entry : symbolsByChannel.entrySet()) {
            String channel = Objects.requireNonNull(entry.getKey(), "channel");
            List<String> symbols = deduplicate(Objects.requireNonNull(entry.getValue(), "symbols"));

            if (symbols.isEmpty()) {
                chain = chain.thenCompose(ignored ->
                        subscribeAsyncInternal(channel, List.of(), options.batchSize()).thenAccept(result ->
                                handles.add(result.subscription())));
                continue;
            }

            for (List<String> batch : batches(symbols, options.batchSize())) {
                chain = chain.thenCompose(ignored ->
                        subscribeAsyncInternal(channel, batch, options.batchSize()).thenAccept(result -> {
                            handles.add(result.subscription());
                            subscribedSymbols[0] += batch.size();
                        }));
            }
        }

        return chain.thenApply(ignored -> new BulkSubscriptionResult(
                requestedSymbols,
                subscribedSymbols[0],
                handles.size(),
                handles
        ));
    }

    public CompletableFuture<Void> unsubscribeChannel(String channel, List<String> symbols) {
        List<String> copy = List.copyOf(symbols);
        CompletableFuture<Void> result = new CompletableFuture<>();
        send(Map.of("action", "unsubscribe", "channels", List.of(Map.of("name", channel, "symbols", copy))))
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        result.completeExceptionally(new DnseSubscriptionException(
                                "Failed to send unsubscribe for " + channel,
                                channel,
                                copy,
                                null,
                                false,
                                unwrap(error)
                        ));
                        return;
                    }
                    subscriptions.removeSymbols(channel, copy);
                    emitMetrics(listener -> listener.onSubscriptionRemoved(channel, copy.size()));
                    result.complete(null);
                });
        return result;
    }

    public void onTrade(Consumer<Trade> handler) { onEvent("trade", handler); }
    public void onTradeExtra(Consumer<TradeExtra> handler) { onEvent("trade_extra", handler); }
    public void onExpectedPrice(Consumer<ExpectedPrice> handler) { onEvent("expected_price", handler); }
    public void onQuote(Consumer<Quote> handler) { onEvent("quote", handler); }
    public void onOhlc(Consumer<Ohlc> handler) { onEvent("ohlc", handler); }
    public void onOhlcClosed(Consumer<Ohlc> handler) { onEvent("ohlc_closed", handler); }
    public void onSecurityDefinition(Consumer<SecurityDefinition> handler) { onEvent("security_definition", handler); }
    public void onForeignTrading(Consumer<ForeignInvestor> handler) { onEvent("foreign", handler); }
    public void onMarketIndex(Consumer<MarketIndex> handler) { onEvent("market_index", handler); }
    public void onEstimatedMarketIndex(Consumer<EstimatedMarketIndex> handler) { onEvent("estimated_market_index", handler); }
    public void onMarketIndexInfluence(Consumer<IndexInfluence> handler) { onEvent("market_index_influence", handler); }
    public void onOrderEvent(Consumer<Order> handler) { onEvent("order_event", handler); }
    public void onPositionEvent(Consumer<Position> handler) { onEvent("position_event", handler); }
    public void onSession(Consumer<Session> handler) { onEvent("session", handler); }
    public void onAccount(Consumer<AccountUpdate> handler) { onEvent("account", handler); }
    public void onError(Consumer<Throwable> handler) { errorHandlers.add(Objects.requireNonNull(handler, "handler")); }
    public void onStateChanged(Consumer<ConnectionStateEvent> handler) { stateHandlers.add(Objects.requireNonNull(handler, "handler")); }
    public void onBackpressure(Consumer<BackpressureEvent> handler) { backpressureHandlers.add(Objects.requireNonNull(handler, "handler")); }
    public void onMetrics(DnseWebSocketMetricsListener listener) { metricsListeners.add(Objects.requireNonNull(listener, "listener")); }

    public <T> void on(Class<T> type, Consumer<T> handler) {
        handlers.computeIfAbsent(type, ignored -> new CopyOnWriteArrayList<>()).add(handler);
    }

    @SuppressWarnings("unchecked")
    private <T> void onEvent(String eventName, Consumer<T> handler) {
        eventHandlers.computeIfAbsent(eventName, ignored -> new CopyOnWriteArrayList<>())
                .add(event -> handler.accept((T) event));
    }

    public ConnectionState state() { return state.get(); }
    public String sessionId() { return sessionId; }
    public Instant lastPongAt() { return lastPongAt; }
    public DispatcherStats dispatcherStats() { return dispatcher.stats(); }

    public boolean isHealthy() {
        if (state.get() != ConnectionState.AUTHENTICATED || transport == null || !transport.isConnected()) return false;
        if (config.heartbeatInterval().isZero() || config.heartbeatInterval().isNegative()) return true;
        return lastPongAt.plus(config.heartbeatInterval().multipliedBy(2)).isAfter(Instant.now(config.clock()));
    }

    private List<Subscription> subscribeAcross(List<String> symbols, List<String> keys, Function<String, String> channelFactory) {
        return keys.stream().map(key -> subscribe(channelFactory.apply(key), symbols)).toList();
    }

    private CompletableFuture<SubscriptionResult> subscribeAsyncInternal(String channel, List<String> symbols, int restoreBatchSize) {
        List<String> copy = List.copyOf(symbols);
        CompletableFuture<SubscriptionResult> result = new CompletableFuture<>();
        send(Map.of("action", "subscribe", "channels", List.of(Map.of("name", channel, "symbols", copy))))
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        result.completeExceptionally(new DnseSubscriptionException(
                                "Failed to send subscription for " + channel,
                                channel,
                                copy,
                                null,
                                false,
                                unwrap(error)
                        ));
                        return;
                    }
                    subscriptions.addSymbols(channel, copy, restoreBatchSize);
                    Subscription subscription = subscriptionHandle(channel, copy);
                    emitMetrics(listener -> listener.onSubscriptionAdded(channel, copy.size()));
                    result.complete(new SubscriptionResult(
                            subscription,
                            SubscriptionConfirmation.TRANSPORT_ACCEPTED,
                            Instant.now(config.clock())
                    ));
                });
        return result;
    }

    private Subscription subscriptionHandle(String channel, List<String> symbols) {
        return new Subscription() {
            @Override public String channel() { return channel; }
            @Override public List<String> symbols() { return symbols; }
            @Override public CompletableFuture<Void> unsubscribe() { return unsubscribeChannel(channel, symbols); }
        };
    }

    private CompletableFuture<Void> send(Object message) {
        WebSocketTransport current = transport;
        if (current == null) return CompletableFuture.failedFuture(new IllegalStateException("WebSocket transport is not initialized"));
        return current.send(codec.encode(message));
    }

    private void handleMessage(byte[] payload) {
        JsonNode data = codec.decode(payload);
        String action = firstText(data, "action", "a");
        String eventName = MessageMapper.eventName(data);
        String metricName = eventName != null ? eventName : (action != null ? action : "welcome");
        emitMetrics(listener -> listener.onMessageReceived(metricName));

        if (state.get() == ConnectionState.CONNECTED) {
            sessionId = firstText(data, "session_id", "sid");
            transitionTo(ConnectionState.AUTHENTICATING, null);
            send(authManager.createAuthMessage()).exceptionally(error -> { failAuthentication(error); return null; });
            return;
        }

        if (state.get() == ConnectionState.AUTHENTICATING) {
            if ("auth_success".equals(action)) {
                transitionTo(ConnectionState.AUTHENTICATED, null);
                lastPongAt = Instant.now(config.clock());
                CompletableFuture<Void> future = authenticationFuture;
                if (future != null && !future.isDone()) future.complete(null);
                return;
            }
            if ("auth_error".equals(action) || "error".equals(action)) {
                failAuthentication(new DnseAuthenticationException("Authentication failed: " + firstText(data, "message", "msg")));
                return;
            }
            failAuthentication(new DnseAuthenticationException("Unexpected authentication response: " + action));
            return;
        }

        if ("ping".equals(action)) {
            send(Map.of("action", "pong"));
            return;
        }
        if ("pong".equals(action)) {
            lastPongAt = Instant.now(config.clock());
            return;
        }
        if ("error".equals(action)) {
            emitError(toServerError(data));
            return;
        }

        long receivedAt = Instant.now(config.clock()).toEpochMilli();
        Object mapped = MessageMapper.map(data, receivedAt);
        if (mapped != null && eventName != null) {
            String symbol = MessageMapper.symbol(data);
            dispatcher.execute(symbol, () -> {
                emit(eventName, mapped);
                emitMetrics(listener -> listener.onMessageDispatched(eventName));
            });
        }
    }

    private void failAuthentication(Throwable error) {
        transitionTo(ConnectionState.DISCONNECTED, error);
        CompletableFuture<Void> future = authenticationFuture;
        if (future != null && !future.isDone()) future.completeExceptionally(error);
        emitError(error);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void emit(String eventName, Object event) {
        List<Consumer<Object>> eventList = eventHandlers.get(eventName);
        if (eventList != null) {
            for (Consumer<Object> handler : eventList) {
                try { handler.accept(event); } catch (Throwable error) { emitError(error); }
            }
        }

        List<Consumer<?>> classList = handlers.get(event.getClass());
        if (classList == null) return;
        for (Consumer handler : classList) {
            try { handler.accept(event); } catch (Throwable error) { emitError(error); }
        }
    }

    private void emitError(Throwable error) {
        if (errorHandlers.isEmpty()) log.error("DNSE WebSocket error", error);
        for (Consumer<Throwable> handler : errorHandlers) {
            try { handler.accept(error); } catch (Throwable callbackError) { log.error("DNSE error callback failed", callbackError); }
        }
    }

    private void transitionTo(ConnectionState next, Throwable cause) {
        ConnectionState previous = state.getAndSet(next);
        if (previous == next && cause == null) return;
        ConnectionStateEvent event = new ConnectionStateEvent(previous, next, sessionId, Instant.now(config.clock()), cause);
        for (Consumer<ConnectionStateEvent> handler : stateHandlers) {
            try { handler.accept(event); } catch (Throwable error) { emitError(error); }
        }
    }

    private void emitBackpressure(BackpressureEvent event) {
        for (Consumer<BackpressureEvent> handler : backpressureHandlers) {
            try { handler.accept(event); } catch (Throwable error) { emitError(error); }
        }
        emitMetrics(listener -> listener.onBackpressure(event));
    }

    private void emitMetrics(Consumer<DnseWebSocketMetricsListener> callback) {
        for (DnseWebSocketMetricsListener listener : metricsListeners) {
            try { callback.accept(listener); } catch (Throwable error) { log.warn("DNSE metrics listener failed", error); }
        }
    }

    private void startHeartbeat() {
        if (config.heartbeatInterval().isZero() || config.heartbeatInterval().isNegative()) return;
        if (!heartbeatStarted.compareAndSet(false, true)) return;
        scheduler.scheduleAtFixedRate(() -> {
            if (state.get() == ConnectionState.AUTHENTICATED && transport != null && transport.isConnected()) {
                send(Map.of("action", "ping")).exceptionally(error -> { emitError(error); return null; });
            }
        }, config.heartbeatInterval().toMillis(), config.heartbeatInterval().toMillis(), TimeUnit.MILLISECONDS);
    }

    private void scheduleReconnect(Throwable cause) {
        if (intentionallyClosed.get() || !config.reconnectPolicy().enabled()) {
            emitError(cause);
            return;
        }
        if (!reconnectScheduled.compareAndSet(false, true)) return;

        int attempt = ++reconnectAttempt;
        if (attempt > config.reconnectPolicy().maxRetries()) {
            reconnectScheduled.set(false);
            transitionTo(ConnectionState.DISCONNECTED, cause);
            emitError(new RuntimeException("Max reconnect attempts exceeded", cause));
            return;
        }

        transitionTo(ConnectionState.RECONNECTING, cause);
        emitMetrics(listener -> listener.onReconnect(attempt));
        long delay = config.reconnectPolicy().delayForAttempt(attempt).toMillis();
        scheduler.schedule(() -> {
            reconnectScheduled.set(false);
            sessionId = null;
            transitionTo(ConnectionState.CONNECTING, null);
            connectAndAuthenticate().thenRun(() -> {
                reconnectAttempt = 0;
                restoreSubscriptions();
            }).exceptionally(error -> { scheduleReconnect(unwrap(error)); return null; });
        }, delay, TimeUnit.MILLISECONDS);
    }

    private void restoreSubscriptions() {
        for (SubscriptionManager.Entry entry : subscriptions.snapshot()) {
            if (entry.symbols().isEmpty()) {
                sendSubscriptionForRestore(entry.channel(), List.of());
                continue;
            }
            for (List<String> batch : batches(entry.symbols(), entry.restoreBatchSize())) {
                sendSubscriptionForRestore(entry.channel(), batch);
            }
        }
    }

    private void sendSubscriptionForRestore(String channel, List<String> symbols) {
        send(Map.of("action", "subscribe", "channels", List.of(Map.of("name", channel, "symbols", List.copyOf(symbols)))))
                .exceptionally(error -> {
                    emitError(new DnseSubscriptionException(
                            "Failed to restore subscription for " + channel,
                            channel,
                            symbols,
                            null,
                            false,
                            unwrap(error)
                    ));
                    return null;
                });
    }

    private DnseWebSocketException toServerError(JsonNode data) {
        String message = firstText(data, "message", "msg");
        if (message == null) message = "Unknown server error";
        String code = firstText(data, "code", "error_code", "errorCode");
        String channel = firstText(data, "channel", "name");
        JsonNode channels = data.get("channels");
        if (channel == null && channels != null && channels.isArray() && !channels.isEmpty()) {
            channel = firstText(channels.get(0), "name", "channel");
        }
        if (channel != null) {
            return new DnseSubscriptionException(message, channel, readSymbols(data, channels), code, true);
        }
        return new DnseWebSocketException(code == null ? message : code + ": " + message);
    }

    private static List<String> readSymbols(JsonNode data, JsonNode channels) {
        JsonNode symbols = data.get("symbols");
        if ((symbols == null || !symbols.isArray()) && channels != null && channels.isArray() && !channels.isEmpty()) {
            symbols = channels.get(0).get("symbols");
        }
        if (symbols == null || !symbols.isArray()) return List.of();
        List<String> result = new ArrayList<>();
        symbols.forEach(value -> result.add(value.asText()));
        return List.copyOf(result);
    }

    private boolean isTradeChannel(String channel) {
        return channel != null
                && channel.startsWith("tick.")
                && channel.endsWith("." + config.encoding().wireName());
    }

    private static List<String> deduplicate(List<String> symbols) {
        return List.copyOf(new LinkedHashSet<>(symbols));
    }

    private static List<String> difference(List<String> left, List<String> right) {
        Set<String> rightSet = new LinkedHashSet<>(right);
        return left.stream().filter(symbol -> !rightSet.contains(symbol)).toList();
    }

    private static int intersectionSize(List<String> left, List<String> right) {
        Set<String> rightSet = new LinkedHashSet<>(right);
        return (int) left.stream().filter(rightSet::contains).distinct().count();
    }

    private static List<List<String>> batches(List<String> symbols, int batchSize) {
        if (symbols.isEmpty()) return List.of();
        List<List<String>> result = new ArrayList<>();
        for (int from = 0; from < symbols.size(); from += batchSize) {
            int to = Math.min(from + batchSize, symbols.size());
            result.add(List.copyOf(symbols.subList(from, to)));
        }
        return result;
    }

    private static String firstText(JsonNode node, String... fields) {
        if (node == null) return null;
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull()) return value.asText();
        }
        return null;
    }

    private void ensureAuthenticated(String channel, List<String> symbols) {
        if (state.get() != ConnectionState.AUTHENTICATED) {
            throw new DnseSubscriptionException("Must authenticate before subscribing", channel, symbols, null, false);
        }
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static <T> T joinSubscriptionFuture(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException error) {
            if (error.getCause() instanceof RuntimeException runtime) throw runtime;
            throw error;
        }
    }

    public CompletableFuture<Void> disconnect() {
        intentionallyClosed.set(true);
        reconnectScheduled.set(false);
        initialConnectionInProgress.set(false);
        CompletableFuture<Void> pendingInitial = initialConnectionFuture;
        if (pendingInitial != null && !pendingInitial.isDone()) {
            pendingInitial.completeExceptionally(new CancellationException("DNSE connection was explicitly closed"));
        }
        transitionTo(ConnectionState.CLOSING, null);
        CompletableFuture<Void> result = transport == null ? CompletableFuture.completedFuture(null) : transport.disconnect();
        return result.whenComplete((ignored, error) -> transitionTo(ConnectionState.CLOSED, error));
    }

    @Override
    public void close() {
        disconnect().join();
        scheduler.shutdownNow();
        dispatcher.close();
    }

    private final class TransportListener implements WebSocketTransport.Listener {
        private final WebSocketTransport source;
        private final CompletableFuture<Void> authFuture;

        private TransportListener(WebSocketTransport source, CompletableFuture<Void> authFuture) {
            this.source = source;
            this.authFuture = authFuture;
        }

        private boolean stale() {
            return transport != source;
        }

        @Override
        public void onOpen() {
            if (!stale()) transitionTo(ConnectionState.CONNECTED, null);
        }

        @Override
        public void onMessage(byte[] payload) {
            if (!stale()) handleMessage(payload);
        }

        @Override
        public void onClosing(int code, String reason) {
            if (!stale()) log.info("WebSocket closing: {} {}", code, reason);
        }

        @Override
        public void onClosed(int code, String reason) {
            if (stale() || intentionallyClosed.get()) return;
            RuntimeException cause = new DnseConnectionException("WebSocket closed: " + code + " " + reason);
            if (!authFuture.isDone()) {
                authFuture.completeExceptionally(cause);
                return;
            }
            if (code == 1000 || code == 1001) {
                transitionTo(ConnectionState.DISCONNECTED, null);
                return;
            }
            scheduleReconnect(cause);
        }

        @Override
        public void onFailure(Throwable error) {
            if (stale() || intentionallyClosed.get()) return;
            if (!authFuture.isDone()) {
                authFuture.completeExceptionally(new DnseConnectionException("WebSocket failed before authentication completed", error));
                return;
            }
            if (!initialConnectionInProgress.get()) scheduleReconnect(error);
        }
    }
}
