package vn.dnse.openapi.rest;

/**
 * Raw DNSE REST response. The Python SDK returns status/body without typed JSON mapping, so the
 * first Java REST milestone preserves that behavior.
 */
public record DnseRestResponse(
        Integer statusCode,
        String body,
        RestRequestPreview requestPreview
) {
    public static DnseRestResponse live(int statusCode, String body) {
        return new DnseRestResponse(statusCode, body, null);
    }

    public static DnseRestResponse dryRun(RestRequestPreview preview) {
        return new DnseRestResponse(null, null, preview);
    }

    public boolean dryRun() {
        return requestPreview != null;
    }

    public boolean successful() {
        return statusCode != null && statusCode >= 200 && statusCode < 300;
    }
}
