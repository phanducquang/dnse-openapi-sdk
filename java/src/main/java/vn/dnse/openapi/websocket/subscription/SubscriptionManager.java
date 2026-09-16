package vn.dnse.openapi.websocket.subscription;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe local registry of subscriptions that must be restored after reconnect. */
public final class SubscriptionManager {
    public record Entry(String channel, List<String> symbols, int restoreBatchSize) {
        public Entry {
            symbols = List.copyOf(symbols);
            if (restoreBatchSize <= 0) throw new IllegalArgumentException("restoreBatchSize must be > 0");
        }
    }

    private final Map<String, Entry> subscriptions = new ConcurrentHashMap<>();

    /** Stores/replaces the complete symbol set for a channel and restores it in one batch. */
    public void put(String channel, List<String> symbols) {
        subscriptions.put(channel, new Entry(channel, symbols, Math.max(1, symbols.size())));
    }

    /** Adds symbols while preserving earlier batches and the safest (smallest) restore batch size. */
    public void addSymbols(String channel, List<String> symbols, int restoreBatchSize) {
        if (restoreBatchSize <= 0) throw new IllegalArgumentException("restoreBatchSize must be > 0");
        subscriptions.compute(channel, (ignored, current) -> {
            if (current == null) return new Entry(channel, symbols, restoreBatchSize);

            int safeBatchSize = Math.min(current.restoreBatchSize(), restoreBatchSize);
            if (current.symbols().isEmpty() || symbols.isEmpty()) {
                return new Entry(channel, current.symbols(), safeBatchSize);
            }

            LinkedHashSet<String> merged = new LinkedHashSet<>(current.symbols());
            merged.addAll(symbols);
            return new Entry(channel, List.copyOf(merged), safeBatchSize);
        });
    }

    public void addSymbols(String channel, List<String> symbols) {
        addSymbols(channel, symbols, Math.max(1, symbols.size()));
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
            return remaining.isEmpty()
                    ? null
                    : new Entry(channel, remaining, current.restoreBatchSize());
        });
    }

    public Collection<Entry> snapshot() {
        return new ArrayList<>(subscriptions.values());
    }
}
