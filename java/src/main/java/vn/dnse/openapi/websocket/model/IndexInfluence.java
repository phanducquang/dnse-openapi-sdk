package vn.dnse.openapi.websocket.model;

import java.util.List;

public record IndexInfluence(
        String indexName,
        List<IndexInfluenceItem> data,
        long receivedAtEpochMillis
) {}
