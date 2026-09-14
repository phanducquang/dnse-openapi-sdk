package vn.dnse.openapi.websocket;

public enum MessageEncoding {
    JSON("json"),
    MSGPACK("msgpack");

    private final String wireName;

    MessageEncoding(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
