package vn.dnse.openapi.websocket.subscription;

/**
 * Summary of one runtime subscription reconciliation operation.
 *
 * @param desiredSymbols total unique symbols requested after reconciliation
 * @param addedSymbols symbols newly subscribed
 * @param removedSymbols symbols unsubscribed because they are no longer desired
 * @param unchangedSymbols symbols already subscribed and retained
 * @param subscribeOperations number of subscribe wire messages sent
 * @param unsubscribeOperations number of unsubscribe wire messages sent
 * @param activeChannels number of channels that remain subscribed after reconciliation
 */
public record SubscriptionReconciliationResult(
        int desiredSymbols,
        int addedSymbols,
        int removedSymbols,
        int unchangedSymbols,
        int subscribeOperations,
        int unsubscribeOperations,
        int activeChannels
) {
}
