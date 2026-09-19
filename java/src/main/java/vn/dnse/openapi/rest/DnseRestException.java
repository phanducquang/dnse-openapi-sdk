package vn.dnse.openapi.rest;

/** Runtime exception raised by the DNSE REST transport/signing layer. */
public class DnseRestException extends RuntimeException {
    public DnseRestException(String message) {
        super(message);
    }

    public DnseRestException(String message, Throwable cause) {
        super(message, cause);
    }
}
