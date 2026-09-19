package vn.dnse.openapi.rest;

import java.util.Map;

/** Request details returned for a dry-run call; no network request is executed. */
public record RestRequestPreview(
        String method,
        String url,
        Map<String, String> headers,
        String body
) {
    public RestRequestPreview {
        headers = Map.copyOf(headers);
    }
}
