package vn.dnse.openapi.websocket.subscription;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Handle returned for one active DNSE channel subscription. */
public interface Subscription {
    /** @return exact channel name sent to DNSE, for example {@code tick.G1.json} */
    String channel();
    /** @return immutable list of symbols associated with this subscription */
    List<String> symbols();
    /** Sends an unsubscribe message for this handle and updates reconnect state after send succeeds. */
    CompletableFuture<Void> unsubscribe();
}
