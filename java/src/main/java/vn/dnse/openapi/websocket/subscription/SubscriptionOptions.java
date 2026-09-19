package vn.dnse.openapi.websocket.subscription;

/** Options for bulk subscriptions. */
public record SubscriptionOptions(int batchSize) {
    public static final int DEFAULT_BATCH_SIZE = 200;

    public SubscriptionOptions {
        if (batchSize <= 0) throw new IllegalArgumentException("batchSize must be > 0");
    }

    public static SubscriptionOptions defaults() {
        return new SubscriptionOptions(DEFAULT_BATCH_SIZE);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int batchSize = DEFAULT_BATCH_SIZE;

        public Builder batchSize(int value) {
            this.batchSize = value;
            return this;
        }

        public SubscriptionOptions build() {
            return new SubscriptionOptions(batchSize);
        }
    }
}
