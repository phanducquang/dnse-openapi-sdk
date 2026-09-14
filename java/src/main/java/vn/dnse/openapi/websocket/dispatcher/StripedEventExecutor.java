package vn.dnse.openapi.websocket.dispatcher;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class StripedEventExecutor implements AutoCloseable {
    private final ThreadPoolExecutor[] workers;

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
                            executor.getQueue().put(task);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RejectedExecutionException("Interrupted while applying WebSocket backpressure", e);
                        }
                    }
            );
        }
    }

    public void execute(String key, Runnable task) {
        int index = Math.floorMod(key == null ? 0 : key.hashCode(), workers.length);
        workers[index].execute(task);
    }

    @Override
    public void close() {
        for (ThreadPoolExecutor worker : workers) worker.shutdownNow();
    }
}
