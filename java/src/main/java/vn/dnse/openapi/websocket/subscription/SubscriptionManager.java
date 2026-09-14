package vn.dnse.openapi.websocket.subscription;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SubscriptionManager {
    public record Entry(String channel, List<String> symbols) {
        public Entry {
            symbols = List.copyOf(symbols);
        }
    }

    private final Map<String, Entry> subscriptions = new ConcurrentHashMap<>();

    public void put(String channel, List<String> symbols) {
        subscriptions.put(channel, new Entry(channel, symbols));
    }

    public void remove(String channel) {
        subscriptions.remove(channel);
    }

    public Collection<Entry> snapshot() {
        return new ArrayList<>(subscriptions.values());
    }
}
