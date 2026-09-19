package vn.dnse.openapi.rest;

import java.util.UUID;

/** Generates nonce values used in DNSE REST request signatures. */
@FunctionalInterface
public interface RestNonceGenerator {
    String next();

    static RestNonceGenerator uuidHex() {
        return () -> UUID.randomUUID().toString().replace("-", "");
    }
}
