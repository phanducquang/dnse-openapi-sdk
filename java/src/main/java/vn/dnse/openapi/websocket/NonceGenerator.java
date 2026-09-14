package vn.dnse.openapi.websocket;

@FunctionalInterface
public interface NonceGenerator {
    String generate();

    static NonceGenerator epochMicros() {
        return () -> String.valueOf(System.currentTimeMillis() * 1_000L);
    }
}
