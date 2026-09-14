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

public final class OkHttpWebSocketTransport implements WebSocketTransport {
    private final OkHttpClient client;
    private volatile WebSocket webSocket;
    private final AtomicBoolean connected = new AtomicBoolean(false);

    public OkHttpWebSocketTransport(Duration connectTimeout) {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(connectTimeout.toMillis(), TimeUnit.MILLISECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build();
    }

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
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                connected.set(false);
                listener.onClosed(code, reason);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                connected.set(false);
                listener.onFailure(t);
                if (!future.isDone()) {
                    future.completeExceptionally(new DnseConnectionException("WebSocket connection failed", t));
                }
            }
        });
        return future;
    }

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

    @Override
    public boolean isConnected() {
        return connected.get();
    }

    @Override
    public CompletableFuture<Void> disconnect() {
        WebSocket socket = webSocket;
        connected.set(false);
        if (socket != null) socket.close(1000, "client shutdown");
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
        return CompletableFuture.completedFuture(null);
    }
}
