package vn.dnse.openapi.websocket.subscription;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface Subscription {
    String channel();
    List<String> symbols();
    CompletableFuture<Void> unsubscribe();
}
