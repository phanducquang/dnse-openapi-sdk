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
import vn.dnse.openapi.websocket.model.Ohlc;
import vn.dnse.openapi.websocket.model.Quote;
import vn.dnse.openapi.websocket.model.SecurityDefinition;
import vn.dnse.openapi.websocket.model.Trade;
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

public final class DnseWebSocketClient implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(DnseWebSocketClient.class);

    private final DnseWebSocketConfig config;
    private final MessageCodec codec;
    private final WebSocketAuthManager authManager;
    private final SubscriptionManager subscriptions = new SubscriptionManager();
    private final StripedEventExecutor dispatcher;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Map<Class<?>, CopyOnWriteArrayList<Consumer<?>>> handlers = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Consumer<Throwable>> errorHandlers = new CopyOnWriteArrayList<>();
    private final AtomicReference<ConnectionState> state = new AtomicReference<>(ConnectionState.DISCONNECTED);
    private final AtomicBoolean intentionallyClosed = new AtomicBoolean(false);
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
        return connectAndAuthenticate().thenRun(this::startHeartbeat);
    }

    private CompletableFuture<Void> connectAndAuthenticate() {
        this.transport = new OkHttpWebSocketTransport(config.connectTimeout());
        this.authenticationFuture = new CompletableFuture<>();
        String url = config.baseUrl() + "/v1/stream?encoding=" + config.encoding().wireName();
        return transport.connect(url, new TransportListener()).thenCompose(ignored -> authenticationFuture);
    }

    public Subscription subscribeTrades(List<String> symbols, String boardId) {
        return subscribe(ChannelBuilder.trades(boardId, config.encoding()), symbols);
    }

    public Subscription subscribeQuotes(List<String> symbols, String boardId) {
        return subscribe(ChannelBuilder.quotes(boardId, config.encoding()), symbols);
    }

    public Subscription subscribeOhlc(List<String> symbols, String resolution) {
        return subscribe(ChannelBuilder.ohlc(resolution, config.encoding()), symbols);
    }

    public Subscription subscribeSecurityDefinitions(List<String> symbols, String boardId) {
        return subscribe(ChannelBuilder.securityDefinition(boardId, config.encoding()), symbols);
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
        return send(Map.of("action", "unsubscribe", "channels", List.of(Map.of("name", channel, "symbols", symbols))))
                .thenRun(() -> subscriptions.remove(channel));
    }

    public void onTrade(Consumer<Trade> handler) { on(Trade.class, handler); }
    public void onQuote(Consumer<Quote> handler) { on(Quote.class, handler); }
    public void onOhlc(Consumer<Ohlc> handler) { on(Ohlc.class, handler); }
    public void onSecurityDefinition(Consumer<SecurityDefinition> handler) { on(SecurityDefinition.class, handler); }
    public void onError(Consumer<Throwable> handler) { errorHandlers.add(handler); }

    public <T> void on(Class<T> type, Consumer<T> handler) {
        handlers.computeIfAbsent(type, ignored -> new CopyOnWriteArrayList<>()).add(handler);
    }

    public ConnectionState state() { return state.get(); }
    public String sessionId() { return sessionId; }
    public Instant lastPongAt() { return lastPongAt; }
    public boolean isHealthy() {
        if (state.get() != ConnectionState.AUTHENTICATED || transport == null || !transport.isConnected()) return false;
        if (config.heartbeatInterval().isZero() || config.heartbeatInterval().isNegative()) return true;
        return lastPongAt.plus(config.heartbeatInterval().multipliedBy(2)).isAfter(Instant.now(config.clock()));
    }

    private CompletableFuture<Void> send(Object message) {
        return transport.send(codec.encode(message));
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
        Object mapped = MessageMapper.map(data, receivedAt);
        if (mapped != null) {
            String symbol = MessageMapper.symbol(data);
            dispatcher.execute(symbol, () -> emit(mapped));
        }
    }

    private void failAuthentication(Throwable error) {
        state.set(ConnectionState.DISCONNECTED);
        if (authenticationFuture != null && !authenticationFuture.isDone()) authenticationFuture.completeExceptionally(error);
        emitError(error);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void emit(Object event) {
        List<Consumer<?>> list = handlers.get(event.getClass());
        if (list == null) return;
        for (Consumer handler : list) {
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
        int attempt = ++reconnectAttempt;
        if (attempt > config.reconnectPolicy().maxRetries()) {
            emitError(new RuntimeException("Max reconnect attempts exceeded", cause));
            return;
        }
        state.set(ConnectionState.RECONNECTING);
        long delay = config.reconnectPolicy().delayForAttempt(attempt).toMillis();
        scheduler.schedule(() -> {
            connectAndAuthenticate().thenRun(() -> {
                reconnectAttempt = 0;
                restoreSubscriptions();
            }).exceptionally(error -> { scheduleReconnect(error); return null; });
        }, delay, TimeUnit.MILLISECONDS);
    }

    private void restoreSubscriptions() {
        for (SubscriptionManager.Entry entry : subscriptions.snapshot()) {
            send(Map.of("action", "subscribe", "channels", List.of(Map.of("name", entry.channel(), "symbols", entry.symbols()))));
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
            if (!intentionallyClosed.get()) scheduleReconnect(new RuntimeException("WebSocket closed: " + code + " " + reason));
        }
        @Override public void onFailure(Throwable error) { if (!intentionallyClosed.get()) scheduleReconnect(error); }
    }
}
