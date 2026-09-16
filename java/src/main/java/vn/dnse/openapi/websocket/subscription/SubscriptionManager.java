package vn.dnse.openapi.websocket.subscription;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe local registry of subscriptions that must be restored after reconnect. */
public final class SubscriptionManager {
    public record Entry(String channel, List<String> symbols) {
        public Entry {
            symbols = List.copyOf(symbols);
        }
    }

    private final Map<String, Entry> subscriptions = new ConcurrentHashMap<>();

    /** Stores/replaces the complete symbol set for a channel. */
    public void put(String channel, List<String> symbols) {
        subscriptions.put(channel, new Entry(channel, symbols));
    }

    /**
     * Adds symbols to an existing channel without losing earlier batches. Empty-symbol channel-only
     * subscriptions remain represented by an empty list.
     */
    public void addSymbols(String channel, List<String> symbols) {
        subscriptions.compute(channel, (ignored, current) -> {
            if (current == null) return new Entry(channel, symbols);
            if (current.symbols().isEmpty() || symbols.isEmpty()) return current;

            LinkedHashSet<String> merged = new LinkedHashSet<>(current.symbols());
            merged.addAll(symbols);
            return new Entry(channel, List.copyOf(merged));
        });
    }

    public void remove(String channel) {
        subscriptions.remove(channel);
    }

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

    public Collection<Entry> snapshot() {
        return new ArrayList<>(subscriptions.values());
    }
}
