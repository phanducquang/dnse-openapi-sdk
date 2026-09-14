package vn.dnse.openapi.websocket.subscription;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe local registry of subscriptions that must be restored after reconnect.
 */
public final class SubscriptionManager {
    /**
     * Immutable reconnect snapshot for one channel.
     * @param channel exact DNSE channel name
     * @param symbols symbols that remain subscribed on that channel; may be empty for channel-only feeds
     */
    public record Entry(String channel, List<String> symbols) {
        public Entry {
            symbols = List.copyOf(symbols);
        }
    }

    /** Current subscription state keyed by channel name. */
    private final Map<String, Entry> subscriptions = new ConcurrentHashMap<>();

    /** Stores/replaces the complete symbol set for a channel. */
    public void put(String channel, List<String> symbols) {
        subscriptions.put(channel, new Entry(channel, symbols));
    }

    /** Removes an entire channel from the reconnect registry. */
    public void remove(String channel) {
        subscriptions.remove(channel);
    }

    /**
     * Removes only the requested symbols while retaining any symbols still active on the channel.
     * Empty-symbol subscriptions such as account/order channels are removed when unsubscribed.
     */
    public void removeSymbols(String channel, List<String> symbols) {
        subscriptions.computeIfPresent(channel, (ignored, current) -> {
            if (symbols.isEmpty() || current.symbols().isEmpty()) {
                return null;
            }

            List<String> remaining = new ArrayList<>(current.symbols());
            remaining.removeAll(symbols);
            return remaining.isEmpty() ? null : new Entry(channel, remaining);
        });
    }

    /** @return defensive snapshot used when re-subscribing after reconnect */
    public Collection<Entry> snapshot() {
        return new ArrayList<>(subscriptions.values());
    }
}
