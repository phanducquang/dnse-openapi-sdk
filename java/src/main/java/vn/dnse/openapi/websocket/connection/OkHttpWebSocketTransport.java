package vn.dnse.openapi.websocket.connection;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import vn.dnse.openapi.websocket.exception.DnseConnectionException;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * OkHttp-backed implementation of {@link WebSocketTransport}.
 *
 * <p>All outgoing SDK messages are sent as binary frames because both the Python JSON encoder and
 * MessagePack encoder produce bytes. Incoming text frames are normalized to UTF-8 bytes so the
 * protocol layer can use one decoder path.</p>
 */
public final class OkHttpWebSocketTransport implements WebSocketTransport {
    /** Dedicated OkHttp client owned by this transport instance/connection attempt. */
    private final OkHttpClient client;
    /** Current OkHttp WebSocket handle after {@link #connect(String, Listener)} is called. */
    private volatile WebSocket webSocket;
    /** True after onOpen until the socket closes/fails. */
    private final AtomicBoolean connected = new AtomicBoolean(false);
    /** Distinguishes an intentional client shutdown from an unexpected transport failure. */
    private final AtomicBoolean closing = new AtomicBoolean(false);
    /** Prevents OkHttp dispatcher/pool cleanup from running more than once. */
    private final AtomicBoolean resourcesClosed = new AtomicBoolean(false);
    /** Completes when the WebSocket close handshake finishes or the configured timeout expires. */
    private final CompletableFuture<Void> closeFuture = new CompletableFuture<>();

    /**
     * Creates a transport with no HTTP read timeout because WebSocket connections are long-lived.
     *
     * @param connectTimeout timeout for establishing the underlying connection
     */
    public OkHttpWebSocketTransport(Duration connectTimeout) {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(connectTimeout.toMillis(), TimeUnit.MILLISECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build();
    }

    /** {@inheritDoc} */
    @Override
    public CompletableFuture<Void> connect(String url, Listener listener) {
        Objects.requireNonNull(listener, "listener");
        CompletableFuture<Void> future = new CompletableFuture<>();
        Request request = new Request.Builder().url(url).build();
        this.webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                connected.set(true);
                listener.onOpen();
                future.complete(null);
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                listener.onMessage(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                listener.onMessage(bytes.toByteArray());
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                listener.onClosing(code, reason);
                // Echo the peer close frame so the RFC WebSocket close handshake can complete.
                webSocket.close(code, reason);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                connected.set(false);
                listener.onClosed(code, reason);
                closeFuture.complete(null);
                shutdownResources();
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                connected.set(false);
                listener.onFailure(t);
                if (!future.isDone()) {
                    future.completeExceptionally(new DnseConnectionException("WebSocket connection failed", t));
                }
                // During intentional shutdown a transport failure is treated as a completed close.
                if (closing.get()) {
                    closeFuture.complete(null);
                } else {
                    closeFuture.completeExceptionally(t);
                }
                shutdownResources();
            }
        });
        return future;
    }

    /** {@inheritDoc} */
    @Override
    public CompletableFuture<Void> send(byte[] payload) {
        WebSocket socket = webSocket;
        if (socket == null || !connected.get()) {
            return CompletableFuture.failedFuture(new DnseConnectionException("WebSocket is not connected"));
        }
        if (!socket.send(ByteString.of(payload))) {
            return CompletableFuture.failedFuture(new DnseConnectionException("Unable to enqueue WebSocket message"));
        }
        return CompletableFuture.completedFuture(null);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isConnected() {
        return connected.get();
    }

    /**
     * Starts a normal close (code 1000) and waits briefly for the peer to acknowledge it.
     * The five-second timeout prevents application shutdown from hanging indefinitely.
     */
    @Override
    public CompletableFuture<Void> disconnect() {
        WebSocket socket = webSocket;
        if (socket == null || !connected.get()) {
            connected.set(false);
            shutdownResources();
            return CompletableFuture.completedFuture(null);
        }

        closing.set(true);
        if (!socket.close(1000, "client shutdown")) {
            connected.set(false);
            shutdownResources();
            return CompletableFuture.completedFuture(null);
        }

        return closeFuture
                .completeOnTimeout(null, 5, TimeUnit.SECONDS)
                .whenComplete((ignored, error) -> {
                    connected.set(false);
                    shutdownResources();
                });
    }

    /** Releases threads/connections owned by this dedicated OkHttp client exactly once. */
    private void shutdownResources() {
        if (!resourcesClosed.compareAndSet(false, true)) return;
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }
}
