package vn.dnse.openapi.websocket.dispatcher;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Dispatches callbacks through deterministic single-thread stripes.
 *
 * <p>Messages with the same symbol are hashed to the same worker and therefore cannot overtake one
 * another. Different symbols can run concurrently on different stripes. Each stripe is bounded;
 * when its queue is full the producer blocks instead of executing out of order or growing memory indefinitely.</p>
 */
public final class StripedEventExecutor implements AutoCloseable {
    private final ThreadPoolExecutor[] workers;
    private final int queueCapacity;
    private final Consumer<BackpressureEvent> backpressureListener;
    private final AtomicLong blockedSubmissions = new AtomicLong();
    private final AtomicLong totalBlockedNanos = new AtomicLong();

    public StripedEventExecutor(int workerCount, int queueCapacity) {
        this(workerCount, queueCapacity, ignored -> { });
    }

    public StripedEventExecutor(
            int workerCount,
            int queueCapacity,
            Consumer<BackpressureEvent> backpressureListener
    ) {
        if (workerCount <= 0) throw new IllegalArgumentException("workerCount must be > 0");
        if (queueCapacity <= 0) throw new IllegalArgumentException("queueCapacity must be > 0");

        this.queueCapacity = queueCapacity;
        this.backpressureListener = Objects.requireNonNull(backpressureListener, "backpressureListener");
        this.workers = new ThreadPoolExecutor[workerCount];

        for (int i = 0; i < workerCount; i++) {
            final int workerIndex = i;
            workers[i] = new ThreadPoolExecutor(
                    1,
                    1,
                    0L,
                    TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(queueCapacity),
                    (task, executor) -> applyBackpressure(workerIndex, task, executor)
            );
        }
    }

    public void execute(String key, Runnable task) {
        int index = Math.floorMod(key == null ? 0 : key.hashCode(), workers.length);
        workers[index].execute(task);
    }

    public DispatcherStats stats() {
        long queued = 0;
        int active = 0;
        int maxQueueSize = 0;
        for (ThreadPoolExecutor worker : workers) {
            int queueSize = worker.getQueue().size();
            queued += queueSize;
            active += worker.getActiveCount();
            maxQueueSize = Math.max(maxQueueSize, queueSize);
        }

        long totalCapacity = (long) workers.length * queueCapacity;
        double utilization = totalCapacity == 0 ? 0.0 : (double) queued / totalCapacity;
        return new DispatcherStats(
                workers.length,
                queued,
                totalCapacity,
                active,
                maxQueueSize,
                utilization,
                blockedSubmissions.get(),
                Duration.ofNanos(totalBlockedNanos.get())
        );
    }

    private void applyBackpressure(int workerIndex, Runnable task, ThreadPoolExecutor executor) {
        if (executor.isShutdown()) {
            throw new RejectedExecutionException("Event executor is shut down");
        }

        int queueSize = executor.getQueue().size();
        long startedAt = System.nanoTime();
        try {
            executor.getQueue().put(task);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RejectedExecutionException("Interrupted while applying WebSocket backpressure", e);
        } finally {
            long blockedNanos = Math.max(0L, System.nanoTime() - startedAt);
            blockedSubmissions.incrementAndGet();
            totalBlockedNanos.addAndGet(blockedNanos);
            backpressureListener.accept(new BackpressureEvent(
                    workerIndex,
                    queueSize,
                    queueCapacity,
                    Duration.ofNanos(blockedNanos)
            ));
        }
    }

    @Override
    public void close() {
        for (ThreadPoolExecutor worker : workers) worker.shutdownNow();
    }
}
