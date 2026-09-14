package vn.dnse.openapi.websocket.connection;

import java.util.concurrent.CompletableFuture;

/**
 * Transport abstraction used by the high-level client so protocol logic is independent of OkHttp.
 */
public interface WebSocketTransport extends AutoCloseable {
    /** Opens the socket and starts forwarding transport callbacks to the listener. */
    CompletableFuture<Void> connect(String url, Listener listener);
    /** Sends one already-encoded binary protocol payload. */
    CompletableFuture<Void> send(byte[] payload);
    /** @return whether the underlying WebSocket is currently open */
    boolean isConnected();
    /** Initiates a graceful close and completes after the close handshake/resources are finished. */
    CompletableFuture<Void> disconnect();

    /** Synchronous AutoCloseable bridge used by try-with-resources callers. */
    @Override
    default void close() {
        disconnect().join();
    }

    /** Receives low-level WebSocket lifecycle events from the transport implementation. */
    interface Listener {
        /** Called after the WebSocket handshake succeeds. */
        void onOpen();
        /** Called for every text/binary WebSocket message, normalized to raw bytes. */
        void onMessage(byte[] payload);
        /** Called when the peer starts the closing handshake. */
        void onClosing(int code, String reason);
        /** Called when the closing handshake has completed. */
        void onClosed(int code, String reason);
        /** Called when the transport terminates because of an I/O/protocol failure. */
        void onFailure(Throwable error);
    }
}
