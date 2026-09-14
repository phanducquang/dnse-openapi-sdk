package vn.dnse.openapi.websocket;

/**
 * Wire encoding supported by the DNSE WebSocket gateway.
 *
 * <p>DNSE documents JSON as the human-readable development format and MessagePack as the more
 * bandwidth-efficient format for realtime processing.</p>
 */
public enum MessageEncoding {
    /** UTF-8 JSON payloads. */
    JSON("json"),
    /** Binary MessagePack payloads. */
    MSGPACK("msgpack");

    /** Value used in the WebSocket query string and channel suffix. */
    private final String wireName;

    MessageEncoding(String wireName) {
        this.wireName = wireName;
    }

    /**
     * Returns the exact lowercase value expected by DNSE on the wire.
     *
     * @return {@code json} or {@code msgpack}
     */
    public String wireName() {
        return wireName;
    }
}
