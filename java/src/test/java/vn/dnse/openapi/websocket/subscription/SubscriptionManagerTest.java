package vn.dnse.openapi.websocket.subscription;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubscriptionManagerTest {

    @Test
    void partialUnsubscribeKeepsRemainingSymbolsForReconnect() {
        SubscriptionManager manager = new SubscriptionManager();
        manager.put("tick.G1.json", List.of("FPT", "VNM", "HPG"));

        manager.removeSymbols("tick.G1.json", List.of("VNM"));

        SubscriptionManager.Entry entry = manager.snapshot().iterator().next();
        assertEquals(List.of("FPT", "HPG"), entry.symbols());
    }

    @Test
    void unsubscribingLastSymbolsRemovesSubscription() {
        SubscriptionManager manager = new SubscriptionManager();
        manager.put("tick.G1.json", List.of("FPT"));

        manager.removeSymbols("tick.G1.json", List.of("FPT"));

        assertTrue(manager.snapshot().isEmpty());
    }

    @Test
    void channelOnlySubscriptionIsRemovedByEmptyUnsubscribe() {
        SubscriptionManager manager = new SubscriptionManager();
        manager.put("account", List.of());

        manager.removeSymbols("account", List.of());

        assertTrue(manager.snapshot().isEmpty());
    }

    @Test
    void mergesBulkBatchesAndRetainsRestoreBatchSize() {
        SubscriptionManager manager = new SubscriptionManager();

        manager.addSymbols("tick.G1.json", List.of("FPT", "VNM"), 2);
        manager.addSymbols("tick.G1.json", List.of("HPG", "SSI"), 2);
        manager.addSymbols("tick.G1.json", List.of("VCB"), 2);

        SubscriptionManager.Entry entry = manager.snapshot().iterator().next();
        assertEquals(List.of("FPT", "VNM", "HPG", "SSI", "VCB"), entry.symbols());
        assertEquals(2, entry.restoreBatchSize());
    }
}
