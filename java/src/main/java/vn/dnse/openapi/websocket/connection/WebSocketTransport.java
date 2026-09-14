package vn.dnse.openapi.websocket.connection;

import java.util.concurrent.CompletableFuture;

public interface WebSocketTransport extends AutoCloseable {
    CompletableFuture<Void> connect(String url, Listener listener);
    CompletableFuture<Void> send(byte[] payload);
    boolean isConnected();
    CompletableFuture<Void> disconnect();

    @Override
    default void close() {
        disconnect().join();
    }

    interface Listener {
        void onOpen();
        void onMessage(byte[] payload);
        void onClosing(int code, String reason);
        void onClosed(int code, String reason);
        void onFailure(Throwable error);
    }
}
