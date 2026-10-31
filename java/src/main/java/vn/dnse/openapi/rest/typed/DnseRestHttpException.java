package vn.dnse.openapi.rest.typed;

import vn.dnse.openapi.rest.DnseRestException;

/** Raised by typed REST APIs when DNSE returns a non-2xx HTTP status. */
public final class DnseRestHttpException extends DnseRestException {
    private final int statusCode;
    private final String responseBody;

    public DnseRestHttpException(int statusCode, String responseBody) {
        super("DNSE REST request returned HTTP " + statusCode);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public int statusCode() {
        return statusCode;
    }

    public String responseBody() {
        return responseBody;
    }
}
