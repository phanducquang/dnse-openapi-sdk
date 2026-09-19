package vn.dnse.openapi.websocket.exception;

import java.util.List;

/** Raised when a subscription operation is invalid or the server reports a subscription-related error. */
public class DnseSubscriptionException extends DnseWebSocketException {
    private final String channel;
    private final List<String> symbols;
    private final String errorCode;
    private final boolean serverReported;

    public DnseSubscriptionException(String message) {
        this(message, null, List.of(), null, false, null);
    }

    public DnseSubscriptionException(String message, Throwable cause) {
        this(message, null, List.of(), null, false, cause);
    }

    public DnseSubscriptionException(
            String message,
            String channel,
            List<String> symbols,
            String errorCode,
            boolean serverReported
    ) {
        this(message, channel, symbols, errorCode, serverReported, null);
    }

    public DnseSubscriptionException(
            String message,
            String channel,
            List<String> symbols,
            String errorCode,
            boolean serverReported,
            Throwable cause
    ) {
        super(message, cause);
        this.channel = channel;
        this.symbols = symbols == null ? List.of() : List.copyOf(symbols);
        this.errorCode = errorCode;
        this.serverReported = serverReported;
    }

    public String channel() { return channel; }

    public List<String> symbols() { return symbols; }

    public String errorCode() { return errorCode; }

    public boolean serverReported() { return serverReported; }
}
