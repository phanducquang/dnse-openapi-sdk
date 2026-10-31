package vn.dnse.openapi.websocket;

/**
 * Generates the nonce included in a DNSE WebSocket authentication message.
 *
 * <p>The Python SDK uses a microsecond-based timestamp-like value. The interface is injectable so
 * tests can provide deterministic values and applications can replace the default strategy if needed.</p>
 */
@FunctionalInterface
public interface NonceGenerator {
    /**
     * Generates the next nonce value sent with the authentication request.
     *
     * @return nonce represented as a string
     */
    String generate();

    /**
     * Returns the Python-compatible default nonce generator based on current epoch milliseconds
     * multiplied by 1,000.
     *
     * @return default nonce generator
     */
    static NonceGenerator epochMicros() {
        return () -> String.valueOf(System.currentTimeMillis() * 1_000L);
    }
}
