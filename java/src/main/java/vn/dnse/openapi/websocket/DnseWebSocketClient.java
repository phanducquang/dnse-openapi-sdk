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
import vn.dnse.openapi.websocket.dispatcher.MessageMapper;
import vn.dnse.openapi.websocket.dispatcher.StripedEventExecutor;
import vn.dnse.openapi.websocket.exception.DnseAuthenticationException;
import vn.dnse.openapi.websocket.exception.DnseSubscriptionException;
import vn.dnse.openapi.websocket.model.*;
import vn.dnse.openapi.websocket.subscription.ChannelBuilder;
import vn.dnse.openapi.websocket.subscription.Subscription;
import vn.dnse.openapi.websocket.subscription.SubscriptionManager;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

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
    private final AtomicReference<ConnectionState> state = new AtomicReference<>(ConnectionState.DISCONNECTED);
    private final AtomicBoolean intentionallyClosed = new AtomicBoolean(false);
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean(false);
    private volatile WebSocketTransport transport;
    private volatile String sessionId;
    private volatile Instant lastPongAt = Instant.EPOCH;
    private volatile CompletableFuture<Void> authenticationFuture;
    private volatile int reconnectAttempt;

    public DnseWebSocketClient(DnseWebSocketConfig config) {
        this.config = config;
        this.codec = config.encoding() == MessageEncoding.JSON ? new JsonMessageCodec() : new MessagePackCodec();
        this.authManager = new WebSocketAuthManager(config.apiKey(), config.apiSecret(), config.clock(), config.nonceGenerator());
        this.dispatcher = new StripedEventExecutor(config.dispatchWorkers(), config.queueCapacity());
    }

    public static DnseWebSocketConfig.Builder builder() { return DnseWebSocketConfig.builder(); }

    public CompletableFuture<Void> connect() {
        intentionallyClosed.set(false);
        state.set(ConnectionState.CONNECTING);
        reconnectAttempt = 0;
        reconnectScheduled.set(false);
        return connectAndAuthenticate().thenRun(this::startHeartbeat);
    }

    private CompletableFuture<Void> connectAndAuthenticate() {
        this.transport = new OkHttpWebSocketTransport(config.connectTimeout());
        this.authenticationFuture = new CompletableFuture<>();
        String url = config.baseUrl() + "/v1/stream?encoding=" + config.encoding().wireName();
        return transport.connect(url, new TransportListener()).thenCompose(ignored -> authenticationFuture);
    }

    public List<Subscription> subscribeTrades(List<String> symbols) {
        return subscribeAcross(symbols, DEFAULT_BOARDS, board -> ChannelBuilder.trades(board, config.encoding()));
    }

    public Subscription subscribeTrades(List<String> symbols, String boardId) {
        return subscribe(ChannelBuilder.trades(boardId, config.encoding()), symbols);
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

    public Subscription subscribeOrderEvents() {
        return subscribeOrderEvents("STOCK");
    }

    public Subscription subscribeOrderEvents(String marketType) {
        return subscribe(ChannelBuilder.order(marketType, config.encoding()), List.of());
    }

    public Subscription subscribeBrokerOrderEvents(String investorId) {
        return subscribeBrokerOrderEvents(investorId, "STOCK");
    }

    public Subscription subscribeBrokerOrderEvents(String investorId, String marketType) {
        return subscribe(ChannelBuilder.brokerOrder(marketType, investorId, config.encoding()), List.of());
    }

    public Subscription subscribePositionEvents() {
        return subscribePositionEvents("STOCK");
    }

    public Subscription subscribePositionEvents(String marketType) {
        return subscribe(ChannelBuilder.position(marketType, config.encoding()), List.of());
    }

    public Subscription subscribeBrokerPositionEvents(String investorId) {
        return subscribeBrokerPositionEvents(investorId, "STOCK");
    }

    public Subscription subscribeBrokerPositionEvents(String investorId, String marketType) {
        return subscribe(ChannelBuilder.brokerPosition(marketType, investorId, config.encoding()), List.of());
    }

    public Subscription subscribeOrders() {
        return subscribe(ChannelBuilder.orders(), List.of());
    }

    public Subscription subscribePositions() {
        return subscribe(ChannelBuilder.positions(), List.of());
    }

    public Subscription subscribeAccount() {
        return subscribe(ChannelBuilder.account(), List.of());
    }

    public Subscription subscribe(String channel, List<String> symbols) {
        if (state.get() != ConnectionState.AUTHENTICATED) throw new DnseSubscriptionException("Must authenticate before subscribing");
        List<String> copy = List.copyOf(symbols);
        send(Map.of("action", "subscribe", "channels", List.of(Map.of("name", channel, "symbols", copy)))).join();
        subscriptions.put(channel, copy);
        return new Subscription() {
            @Override public String channel() { return channel; }
            @Override public List<String> symbols() { return copy; }
            @Override public CompletableFuture<Void> unsubscribe() { return unsubscribeChannel(channel, copy); }
        };
    }

    public CompletableFuture<Void> unsubscribeChannel(String channel, List<String> symbols) {
        List<String> copy = List.copyOf(symbols);
        return send(Map.of("action", "unsubscribe", "channels", List.of(Map.of("name", channel, "symbols", copy))))
                .thenRun(() -> subscriptions.removeSymbols(channel, copy));
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
    public void onError(Consumer<Throwable> handler) { errorHandlers.add(handler); }

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
    public boolean isHealthy() {
        if (state.get() != ConnectionState.AUTHENTICATED || transport == null || !transport.isConnected()) return false;
        if (config.heartbeatInterval().isZero() || config.heartbeatInterval().isNegative()) return true;
        return lastPongAt.plus(config.heartbeatInterval().multipliedBy(2)).isAfter(Instant.now(config.clock()));
    }

    private List<Subscription> subscribeAcross(List<String> symbols, List<String> keys, Function<String, String> channelFactory) {
        return keys.stream().map(key -> subscribe(channelFactory.apply(key), symbols)).toList();
    }

    private CompletableFuture<Void> send(Object message) {
        WebSocketTransport current = transport;
        if (current == null) return CompletableFuture.failedFuture(new IllegalStateException("WebSocket transport is not initialized"));
        return current.send(codec.encode(message));
    }

    private void handleMessage(byte[] payload) {
        JsonNode data = codec.decode(payload);
        String action = firstText(data, "action", "a");
        if (state.get() == ConnectionState.CONNECTED) {
            sessionId = firstText(data, "session_id", "sid");
            state.set(ConnectionState.AUTHENTICATING);
            send(authManager.createAuthMessage()).exceptionally(error -> { failAuthentication(error); return null; });
            return;
        }
        if (state.get() == ConnectionState.AUTHENTICATING) {
            if ("auth_success".equals(action)) {
                state.set(ConnectionState.AUTHENTICATED);
                lastPongAt = Instant.now(config.clock());
                authenticationFuture.complete(null);
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
            emitError(new RuntimeException(firstText(data, "message", "msg")));
            return;
        }
        long receivedAt = Instant.now(config.clock()).toEpochMilli();
        String eventName = MessageMapper.eventName(data);
        Object mapped = MessageMapper.map(data, receivedAt);
        if (mapped != null && eventName != null) {
            String symbol = MessageMapper.symbol(data);
            dispatcher.execute(symbol, () -> emit(eventName, mapped));
        }
    }

    private void failAuthentication(Throwable error) {
        state.set(ConnectionState.DISCONNECTED);
        if (authenticationFuture != null && !authenticationFuture.isDone()) authenticationFuture.completeExceptionally(error);
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
        for (Consumer<Throwable> handler : errorHandlers) handler.accept(error);
    }

    private void startHeartbeat() {
        if (config.heartbeatInterval().isZero() || config.heartbeatInterval().isNegative()) return;
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
            state.set(ConnectionState.DISCONNECTED);
            emitError(new RuntimeException("Max reconnect attempts exceeded", cause));
            return;
        }
        state.set(ConnectionState.RECONNECTING);
        long delay = config.reconnectPolicy().delayForAttempt(attempt).toMillis();
        scheduler.schedule(() -> {
            reconnectScheduled.set(false);
            connectAndAuthenticate().thenRun(() -> {
                reconnectAttempt = 0;
                restoreSubscriptions();
            }).exceptionally(error -> { scheduleReconnect(error); return null; });
        }, delay, TimeUnit.MILLISECONDS);
    }

    private void restoreSubscriptions() {
        for (SubscriptionManager.Entry entry : subscriptions.snapshot()) {
            send(Map.of("action", "subscribe", "channels", List.of(Map.of("name", entry.channel(), "symbols", entry.symbols()))))
                    .exceptionally(error -> { emitError(error); return null; });
        }
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull()) return value.asText();
        }
        return null;
    }

    public CompletableFuture<Void> disconnect() {
        intentionallyClosed.set(true);
        reconnectScheduled.set(false);
        state.set(ConnectionState.CLOSING);
        CompletableFuture<Void> result = transport == null ? CompletableFuture.completedFuture(null) : transport.disconnect();
        return result.whenComplete((ignored, error) -> state.set(ConnectionState.CLOSED));
    }

    @Override
    public void close() {
        disconnect().join();
        scheduler.shutdownNow();
        dispatcher.close();
    }

    private final class TransportListener implements WebSocketTransport.Listener {
        @Override public void onOpen() { state.set(ConnectionState.CONNECTED); }
        @Override public void onMessage(byte[] payload) { handleMessage(payload); }
        @Override public void onClosing(int code, String reason) { log.info("WebSocket closing: {} {}", code, reason); }
        @Override public void onClosed(int code, String reason) {
            if (intentionallyClosed.get()) return;
            if (code == 1000 || code == 1001) {
                state.set(ConnectionState.DISCONNECTED);
                return;
            }
            scheduleReconnect(new RuntimeException("WebSocket closed: " + code + " " + reason));
        }
        @Override public void onFailure(Throwable error) { if (!intentionallyClosed.get()) scheduleReconnect(error); }
    }
}
