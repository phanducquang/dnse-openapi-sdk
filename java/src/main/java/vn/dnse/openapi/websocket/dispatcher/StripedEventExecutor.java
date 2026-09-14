package vn.dnse.openapi.websocket.dispatcher;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Dispatches callbacks through deterministic single-thread stripes.
 *
 * <p>Messages with the same symbol are hashed to the same worker and therefore cannot overtake one
 * another. Different symbols can run concurrently on different stripes. Each stripe is bounded;
 * when its queue is full the producer blocks instead of executing out of order or growing memory indefinitely.</p>
 */
public final class StripedEventExecutor implements AutoCloseable {
    /** One single-thread executor per ordering stripe. */
    private final ThreadPoolExecutor[] workers;

    /**
     * @param workerCount number of parallel stripes
     * @param queueCapacity bounded queue size for each stripe
     */
    public StripedEventExecutor(int workerCount, int queueCapacity) {
        if (workerCount <= 0) throw new IllegalArgumentException("workerCount must be > 0");
        if (queueCapacity <= 0) throw new IllegalArgumentException("queueCapacity must be > 0");

        workers = new ThreadPoolExecutor[workerCount];
        for (int i = 0; i < workerCount; i++) {
            workers[i] = new ThreadPoolExecutor(
                    1,
                    1,
                    0L,
                    TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(queueCapacity),
                    (task, executor) -> {
                        if (executor.isShutdown()) {
                            throw new RejectedExecutionException("Event executor is shut down");
                        }
                        try {
                            // Blocking here is deliberate backpressure. CallerRunsPolicy would break ordering.
                            executor.getQueue().put(task);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RejectedExecutionException("Interrupted while applying WebSocket backpressure", e);
                        }
                    }
            );
        }
    }

    /**
     * Routes one callback to the stripe derived from its ordering key (normally symbol).
     * A missing symbol uses stripe zero.
     */
    public void execute(String key, Runnable task) {
        int index = Math.floorMod(key == null ? 0 : key.hashCode(), workers.length);
        workers[index].execute(task);
    }

    /** Stops all dispatch workers and discards callbacks that have not started. */
    @Override
    public void close() {
        for (ThreadPoolExecutor worker : workers) worker.shutdownNow();
    }
}
