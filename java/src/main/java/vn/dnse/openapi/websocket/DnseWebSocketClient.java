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

/**
 * High-level DNSE OpenAPI WebSocket client for realtime market data and private account events.
 *
 * <p>The client owns connection/authentication state, channel subscriptions, heartbeat, typed message
 * mapping, per-symbol ordered callback dispatch, and automatic reconnect with re-authentication and
 * re-subscription. DNSE market-data symbols should be supplied in uppercase.</p>
 *
 * <p>DNSE currently documents a maximum WebSocket connection lifetime of roughly eight hours, so
 * long-running applications should leave reconnect enabled rather than assuming one socket lives forever.</p>
 */
public final class DnseWebSocketClient implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(DnseWebSocketClient.class);

    /** Default boards used by the Python SDK for trade-like market-data subscriptions. */
    public static final List<String> DEFAULT_BOARDS = List.of("G1", "G3", "G4", "G7", "T1", "T2", "T3", "T4", "T6");
    /** Board set historically used by the Python SDK for top-price/quote subscriptions. */
    public static final List<String> DEFAULT_QUOTE_BOARDS = List.of("G1", "G2", "G3", "G4", "G5", "G6", "G7");
    /** Candle resolutions supported by the Python SDK/DNSE market-data channels. */
    public static final List<String> DEFAULT_OHLC_RESOLUTIONS = List.of("1", "3", "5", "15", "30", "1H", "1D", "1W");

    /** Immutable connection/protocol settings. */
    private final DnseWebSocketConfig config;
    /** JSON or MessagePack codec selected from {@link DnseWebSocketConfig#encoding()}. */
    private final MessageCodec codec;
    /** Produces signed authentication messages without exposing the API secret on the wire. */
    private final WebSocketAuthManager authManager;
    /** Tracks subscriptions so they can be restored after reconnect. */
    private final SubscriptionManager subscriptions = new SubscriptionManager();
    /** Preserves event order for the same symbol while allowing cross-symbol parallelism. */
    private final StripedEventExecutor dispatcher;
    /** Runs heartbeat and delayed reconnect tasks. */
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    /** Generic handlers registered by Java model class. */
    private final Map<Class<?>, CopyOnWriteArrayList<Consumer<?>>> handlers = new ConcurrentHashMap<>();
    /** Protocol event-name handlers; required to distinguish event types that share one Java model class. */
    private final Map<String, CopyOnWriteArrayList<Consumer<Object>>> eventHandlers = new ConcurrentHashMap<>();
    /** Error callbacks supplied by the application. */
    private final CopyOnWriteArrayList<Consumer<Throwable>> errorHandlers = new CopyOnWriteArrayList<>();
    /** Current high-level connection/authentication lifecycle state. */
    private final AtomicReference<ConnectionState> state = new AtomicReference<>(ConnectionState.DISCONNECTED);
    /** Prevents reconnect when close/disconnect was explicitly requested by the application. */
    private final AtomicBoolean intentionallyClosed = new AtomicBoolean(false);
    /** Deduplicates failure/close callbacks so only one reconnect attempt is scheduled at a time. */
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean(false);
    /** Transport for the current connection attempt; replaced whenever a reconnect starts. */
    private volatile WebSocketTransport transport;
    /** Session identifier received in the DNSE welcome message. */
    private volatile String sessionId;
    /** Local time of the latest successful auth/pong used by {@link #isHealthy()}. */
    private volatile Instant lastPongAt = Instant.EPOCH;
    /** Completes the public connect/reconnect chain only after authentication succeeds. */
    private volatile CompletableFuture<Void> authenticationFuture;
    /** One-based retry counter used to compute reconnect backoff. */
    private volatile int reconnectAttempt;

    /** Creates a client from validated immutable configuration. */
    public DnseWebSocketClient(DnseWebSocketConfig config) {
        this.config = config;
        this.codec = config.encoding() == MessageEncoding.JSON ? new JsonMessageCodec() : new MessagePackCodec();
        this.authManager = new WebSocketAuthManager(config.apiKey(), config.apiSecret(), config.clock(), config.nonceGenerator());
        this.dispatcher = new StripedEventExecutor(config.dispatchWorkers(), config.queueCapacity());
    }

    /** Convenience entry point for building {@link DnseWebSocketConfig}. */
    public static DnseWebSocketConfig.Builder builder() { return DnseWebSocketConfig.builder(); }

    /**
     * Opens the WebSocket, waits for the server welcome message, authenticates, then starts heartbeat.
     *
     * @return future completed only after state reaches {@link ConnectionState#AUTHENTICATED}
     */
    public CompletableFuture<Void> connect() {
        intentionallyClosed.set(false);
        state.set(ConnectionState.CONNECTING);
        reconnectAttempt = 0;
        reconnectScheduled.set(false);
        return connectAndAuthenticate().thenRun(this::startHeartbeat);
    }

    /** Creates a fresh transport and completes only after the welcome/auth exchange succeeds. */
    private CompletableFuture<Void> connectAndAuthenticate() {
        this.transport = new OkHttpWebSocketTransport(config.connectTimeout());
        this.authenticationFuture = new CompletableFuture<>();
        String url = config.baseUrl() + "/v1/stream?encoding=" + config.encoding().wireName();
        return transport.connect(url, new TransportListener()).thenCompose(ignored -> authenticationFuture);
    }

    /** Subscribes realtime matched trades on every Python-SDK default board. */
    public List<Subscription> subscribeTrades(List<String> symbols) {
        return subscribeAcross(symbols, DEFAULT_BOARDS, board -> ChannelBuilder.trades(board, config.encoding()));
    }

    /** Subscribes realtime matched trades for one board, e.g. G1 odd/even market board selection. */
    public Subscription subscribeTrades(List<String> symbols, String boardId) {
        return subscribe(ChannelBuilder.trades(boardId, config.encoding()), symbols);
    }

    /** Subscribes enhanced trade data (direction/average price) on all default boards. */
    public List<Subscription> subscribeTradeExtra(List<String> symbols) {
        return subscribeAcross(symbols, DEFAULT_BOARDS, board -> ChannelBuilder.tradeExtra(board, config.encoding()));
    }

    /** Subscribes enhanced trade data on one board. */
    public Subscription subscribeTradeExtra(List<String> symbols, String boardId) {
        return subscribe(ChannelBuilder.tradeExtra(boardId, config.encoding()), symbols);
    }

    /** Subscribes ATO/ATC indicative match price on all default boards. */
    public List<Subscription> subscribeExpectedPrice(List<String> symbols) {
        return subscribeAcross(symbols, DEFAULT_BOARDS, board -> ChannelBuilder.expectedPrice(board, config.encoding()));
    }

    /** Subscribes ATO/ATC indicative match price on one board. */
    public Subscription subscribeExpectedPrice(List<String> symbols, String boardId) {
        return subscribe(ChannelBuilder.expectedPrice(boardId, config.encoding()), symbols);
    }

    /** Subscribes market depth/top-price updates across the Python quote-board set. */
    public List<Subscription> subscribeQuotes(List<String> symbols) {
        return subscribeAcross(symbols, DEFAULT_QUOTE_BOARDS, board -> ChannelBuilder.quotes(board, config.encoding()));
    }

    /** Subscribes market depth/top-price updates on one board. */
    public Subscription subscribeQuotes(List<String> symbols, String boardId) {
        return subscribe(ChannelBuilder.quotes(boardId, config.encoding()), symbols);
    }

    /** Subscribes security-reference/status data across default boards. */
    public List<Subscription> subscribeSecurityDefinitions(List<String> symbols) {
        return subscribeAcross(symbols, DEFAULT_BOARDS, board -> ChannelBuilder.securityDefinition(board, config.encoding()));
    }

    /** Subscribes security-reference/status data on one board. */
    public Subscription subscribeSecurityDefinitions(List<String> symbols, String boardId) {
        return subscribe(ChannelBuilder.securityDefinition(boardId, config.encoding()), symbols);
    }

    /** Subscribes forming OHLC candles for all standard resolutions. */
    public List<Subscription> subscribeOhlc(List<String> symbols) {
        return subscribeAcross(symbols, DEFAULT_OHLC_RESOLUTIONS, resolution -> ChannelBuilder.ohlc(resolution, config.encoding()));
    }

    /** Subscribes forming OHLC candles for one resolution such as 1, 15, 1H or 1D. */
    public Subscription subscribeOhlc(List<String> symbols, String resolution) {
        return subscribe(ChannelBuilder.ohlc(resolution, config.encoding()), symbols);
    }

    /** Subscribes finalized/closed candles for all standard resolutions. */
    public List<Subscription> subscribeOhlcClosed(List<String> symbols) {
        return subscribeAcross(symbols, DEFAULT_OHLC_RESOLUTIONS, resolution -> ChannelBuilder.ohlcClosed(resolution, config.encoding()));
    }

    /** Subscribes finalized/closed candles for one resolution. */
    public Subscription subscribeOhlcClosed(List<String> symbols, String resolution) {
        return subscribe(ChannelBuilder.ohlcClosed(resolution, config.encoding()), symbols);
    }

    /** Subscribes foreign-investor activity using the wildcard board selector. */
    public Subscription subscribeForeignTrading(List<String> symbols) {
        return subscribeForeignTrading(symbols, "*");
    }

    /** Subscribes foreign-investor buy/sell/room data for one board. */
    public Subscription subscribeForeignTrading(List<String> symbols, String boardId) {
        return subscribe(ChannelBuilder.foreignTrading(boardId, config.encoding()), symbols);
    }

    /** Subscribes a market index such as VNINDEX, VN30, HNX or UPCOM. */
    public Subscription subscribeMarketIndex(String indexName) {
        return subscribe(ChannelBuilder.marketIndex(indexName, config.encoding()), List.of());
    }

    /** Subscribes estimated market-index data; DNSE documentation currently describes VN30. */
    public Subscription subscribeEstimatedMarketIndex(String indexName) {
        return subscribe(ChannelBuilder.estimatedMarketIndex(indexName, config.encoding()), List.of());
    }

    /** Subscribes the Python-SDK index-influence feed at the requested resolution. */
    public Subscription subscribeMarketIndexInfluence(String indexName, int resolution) {
        return subscribe(ChannelBuilder.marketIndexInfluence(indexName, resolution, config.encoding()), List.of());
    }

    /** Subscribes session-state updates for a product group across all boards. */
    public Subscription subscribeSession(String productGroupId) {
        return subscribeSession(productGroupId, "*");
    }

    /** Subscribes session-state updates for one product group and board. */
    public Subscription subscribeSession(String productGroupId, String boardId) {
        return subscribe(ChannelBuilder.session(productGroupId, boardId, config.encoding()), List.of());
    }

    /** Subscribes private order events for the default STOCK market type. */
    public Subscription subscribeOrderEvents() {
        return subscribeOrderEvents("STOCK");
    }

    /** Subscribes private order events for the requested market type. */
    public Subscription subscribeOrderEvents(String marketType) {
        return subscribe(ChannelBuilder.order(marketType, config.encoding()), List.of());
    }

    /** Subscribes broker order events for one investor using the default STOCK market type. */
    public Subscription subscribeBrokerOrderEvents(String investorId) {
        return subscribeBrokerOrderEvents(investorId, "STOCK");
    }

    /** Subscribes broker order events for one investor and market type. */
    public Subscription subscribeBrokerOrderEvents(String investorId, String marketType) {
        return subscribe(ChannelBuilder.brokerOrder(marketType, investorId, config.encoding()), List.of());
    }

    /** Subscribes private position events for the default STOCK market type. */
    public Subscription subscribePositionEvents() {
        return subscribePositionEvents("STOCK");
    }

    /** Subscribes private position events for the requested market type. */
    public Subscription subscribePositionEvents(String marketType) {
        return subscribe(ChannelBuilder.position(marketType, config.encoding()), List.of());
    }

    /** Subscribes broker position events for one investor using default STOCK market type. */
    public Subscription subscribeBrokerPositionEvents(String investorId) {
        return subscribeBrokerPositionEvents(investorId, "STOCK");
    }

    /** Subscribes broker position events for one investor and market type. */
    public Subscription subscribeBrokerPositionEvents(String investorId, String marketType) {
        return subscribe(ChannelBuilder.brokerPosition(marketType, investorId, config.encoding()), List.of());
    }

    /** Subscribes the Python SDK's general/legacy orders channel. */
    public Subscription subscribeOrders() {
        return subscribe(ChannelBuilder.orders(), List.of());
    }

    /** Subscribes the Python SDK's general/legacy positions channel. */
    public Subscription subscribePositions() {
        return subscribe(ChannelBuilder.positions(), List.of());
    }

    /** Subscribes account balance/buying-power updates. */
    public Subscription subscribeAccount() {
        return subscribe(ChannelBuilder.account(), List.of());
    }

    /**
     * Low-level subscription API for custom/forward-compatible channel names.
     * The subscription is recorded only after the wire message is successfully enqueued.
     */
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

    /**
     * Sends an unsubscribe request and removes only those symbols from reconnect state.
     */
    public CompletableFuture<Void> unsubscribeChannel(String channel, List<String> symbols) {
        List<String> copy = List.copyOf(symbols);
        return send(Map.of("action", "unsubscribe", "channels", List.of(Map.of("name", channel, "symbols", copy))))
                .thenRun(() -> subscriptions.removeSymbols(channel, copy));
    }

    /** Registers a handler for matched trade events. */
    public void onTrade(Consumer<Trade> handler) { onEvent("trade", handler); }
    /** Registers a handler for enhanced trade events. */
    public void onTradeExtra(Consumer<TradeExtra> handler) { onEvent("trade_extra", handler); }
    /** Registers a handler for ATO/ATC indicative-price events. */
    public void onExpectedPrice(Consumer<ExpectedPrice> handler) { onEvent("expected_price", handler); }
    /** Registers a handler for market-depth quote events. */
    public void onQuote(Consumer<Quote> handler) { onEvent("quote", handler); }
    /** Registers a handler for forming OHLC candles. */
    public void onOhlc(Consumer<Ohlc> handler) { onEvent("ohlc", handler); }
    /** Registers a handler for finalized OHLC candles. */
    public void onOhlcClosed(Consumer<Ohlc> handler) { onEvent("ohlc_closed", handler); }
    /** Registers a handler for security definition/status data. */
    public void onSecurityDefinition(Consumer<SecurityDefinition> handler) { onEvent("security_definition", handler); }
    /** Registers a handler for foreign-investor activity. */
    public void onForeignTrading(Consumer<ForeignInvestor> handler) { onEvent("foreign", handler); }
    /** Registers a handler for market-index updates. */
    public void onMarketIndex(Consumer<MarketIndex> handler) { onEvent("market_index", handler); }
    /** Registers a handler for estimated market-index updates. */
    public void onEstimatedMarketIndex(Consumer<EstimatedMarketIndex> handler) { onEvent("estimated_market_index", handler); }
    /** Registers a handler for index-influence updates. */
    public void onMarketIndexInfluence(Consumer<IndexInfluence> handler) { onEvent("market_index_influence", handler); }
    /** Registers a handler for private order events. */
    public void onOrderEvent(Consumer<Order> handler) { onEvent("order_event", handler); }
    /** Registers a handler for private position events. */
    public void onPositionEvent(Consumer<Position> handler) { onEvent("position_event", handler); }
    /** Registers a handler for trading-session status changes. */
    public void onSession(Consumer<Session> handler) { onEvent("session", handler); }
    /** Registers a handler for account-value updates. */
    public void onAccount(Consumer<AccountUpdate> handler) { onEvent("account", handler); }
    /** Registers a callback for protocol, mapping, callback and connection failures. */
    public void onError(Consumer<Throwable> handler) { errorHandlers.add(handler); }

    /**
     * Registers a generic handler keyed by Java event class. Event-name handlers are preferred when
     * one model class represents multiple wire event names (for example OHLC/open vs closed candle).
     */
    public <T> void on(Class<T> type, Consumer<T> handler) {
        handlers.computeIfAbsent(type, ignored -> new CopyOnWriteArrayList<>()).add(handler);
    }

    /** Internal type-safe facade over the event-name callback registry. */
    @SuppressWarnings("unchecked")
    private <T> void onEvent(String eventName, Consumer<T> handler) {
        eventHandlers.computeIfAbsent(eventName, ignored -> new CopyOnWriteArrayList<>())
                .add(event -> handler.accept((T) event));
    }

    /** @return current high-level lifecycle state */
    public ConnectionState state() { return state.get(); }
    /** @return session id from the latest welcome message, or null before welcome */
    public String sessionId() { return sessionId; }
    /** @return local time of the latest auth success or application pong */
    public Instant lastPongAt() { return lastPongAt; }

    /**
     * Checks whether the socket is authenticated and heartbeat activity is recent enough.
     * A disabled heartbeat treats any authenticated/open socket as healthy.
     */
    public boolean isHealthy() {
        if (state.get() != ConnectionState.AUTHENTICATED || transport == null || !transport.isConnected()) return false;
        if (config.heartbeatInterval().isZero() || config.heartbeatInterval().isNegative()) return true;
        return lastPongAt.plus(config.heartbeatInterval().multipliedBy(2)).isAfter(Instant.now(config.clock()));
    }

    /** Creates the same subscription for a sequence of boards/resolutions. */
    private List<Subscription> subscribeAcross(List<String> symbols, List<String> keys, Function<String, String> channelFactory) {
        return keys.stream().map(key -> subscribe(channelFactory.apply(key), symbols)).toList();
    }

    /** Encodes and sends one protocol message through the current transport. */
    private CompletableFuture<Void> send(Object message) {
        WebSocketTransport current = transport;
        if (current == null) return CompletableFuture.failedFuture(new IllegalStateException("WebSocket transport is not initialized"));
        return current.send(codec.encode(message));
    }

    /**
     * Central inbound protocol state machine: welcome -> auth, auth response, heartbeat, error, event mapping.
     */
    private void handleMessage(byte[] payload) {
        JsonNode data = codec.decode(payload);
        String action = firstText(data, "action", "a");
        if (state.get() == ConnectionState.CONNECTED) {
            // The first protocol message after socket open is the DNSE welcome/session message.
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
            // DNSE requires a timely pong for server-originated pings.
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

    /** Fails the in-flight authentication future and routes the error to application handlers. */
    private void failAuthentication(Throwable error) {
        state.set(ConnectionState.DISCONNECTED);
        if (authenticationFuture != null && !authenticationFuture.isDone()) authenticationFuture.completeExceptionally(error);
        emitError(error);
    }

    /** Delivers one mapped event to event-name handlers first, then generic class handlers. */
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

    /** Logs unhandled errors or fans them out to all registered application error callbacks. */
    private void emitError(Throwable error) {
        if (errorHandlers.isEmpty()) log.error("DNSE WebSocket error", error);
        for (Consumer<Throwable> handler : errorHandlers) handler.accept(error);
    }

    /**
     * Starts proactive application heartbeat. DNSE also sends server pings periodically; the client
     * responds to those separately in {@link #handleMessage(byte[])}.
     */
    private void startHeartbeat() {
        if (config.heartbeatInterval().isZero() || config.heartbeatInterval().isNegative()) return;
        scheduler.scheduleAtFixedRate(() -> {
            if (state.get() == ConnectionState.AUTHENTICATED && transport != null && transport.isConnected()) {
                send(Map.of("action", "ping")).exceptionally(error -> { emitError(error); return null; });
            }
        }, config.heartbeatInterval().toMillis(), config.heartbeatInterval().toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * Schedules one deduplicated reconnect attempt using exponential backoff.
     * Successful authentication resets the retry counter and restores local subscriptions.
     */
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

    /** Replays every locally active channel after reconnect/authentication succeeds. */
    private void restoreSubscriptions() {
        for (SubscriptionManager.Entry entry : subscriptions.snapshot()) {
            send(Map.of("action", "subscribe", "channels", List.of(Map.of("name", entry.channel(), "symbols", entry.symbols()))))
                    .exceptionally(error -> { emitError(error); return null; });
        }
    }

    /** Returns the first present text value among protocol aliases such as action/a or session_id/sid. */
    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull()) return value.asText();
        }
        return null;
    }

    /**
     * Intentionally closes the active transport and suppresses automatic reconnect.
     *
     * @return future completed when transport close cleanup finishes
     */
    public CompletableFuture<Void> disconnect() {
        intentionallyClosed.set(true);
        reconnectScheduled.set(false);
        state.set(ConnectionState.CLOSING);
        CompletableFuture<Void> result = transport == null ? CompletableFuture.completedFuture(null) : transport.disconnect();
        return result.whenComplete((ignored, error) -> state.set(ConnectionState.CLOSED));
    }

    /** Gracefully disconnects, then releases scheduler and event-dispatch threads owned by the client. */
    @Override
    public void close() {
        disconnect().join();
        scheduler.shutdownNow();
        dispatcher.close();
    }

    /** Adapts low-level transport callbacks to the high-level protocol state machine. */
    private final class TransportListener implements WebSocketTransport.Listener {
        /** Marks the TCP/WebSocket connection open; DNSE authentication has not happened yet. */
        @Override public void onOpen() { state.set(ConnectionState.CONNECTED); }
        /** Decodes and handles every inbound protocol payload. */
        @Override public void onMessage(byte[] payload) { handleMessage(payload); }
        /** Records the peer's close intent; the transport performs the actual close response. */
        @Override public void onClosing(int code, String reason) { log.info("WebSocket closing: {} {}", code, reason); }
        /** Normal closes stop; abnormal closes trigger reconnect when enabled. */
        @Override public void onClosed(int code, String reason) {
            if (intentionallyClosed.get()) return;
            if (code == 1000 || code == 1001) {
                state.set(ConnectionState.DISCONNECTED);
                return;
            }
            scheduleReconnect(new RuntimeException("WebSocket closed: " + code + " " + reason));
        }
        /** Transport failures trigger the same deduplicated reconnect path as abnormal closes. */
        @Override public void onFailure(Throwable error) { if (!intentionallyClosed.get()) scheduleReconnect(error); }
    }
}
