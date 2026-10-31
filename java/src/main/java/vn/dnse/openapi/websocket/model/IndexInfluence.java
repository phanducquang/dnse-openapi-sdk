package vn.dnse.openapi.websocket.model;

import java.util.List;

/**
 * Collection of per-symbol contributions to one market index.
 *
 * @param indexName index identifier from upstream field {@code index_name}
 * @param data immutable list of constituent contribution rows
 * @param receivedAtEpochMillis local SDK receipt time
 */
public record IndexInfluence(
        String indexName,
        List<IndexInfluenceItem> data,
        long receivedAtEpochMillis
) {}
