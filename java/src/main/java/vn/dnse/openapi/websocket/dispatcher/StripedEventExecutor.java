package vn.dnse.openapi.websocket.dispatcher;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class StripedEventExecutor implements AutoCloseable {
    private final ExecutorService[] workers;

    public StripedEventExecutor(int workerCount, int queueCapacity) {
        workers = new ExecutorService[workerCount];
        for (int i = 0; i < workerCount; i++) {
            workers[i] = new ThreadPoolExecutor(
                    1, 1, 0L, TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(queueCapacity),
                    new ThreadPoolExecutor.CallerRunsPolicy());
        }
    }

    public void execute(String key, Runnable task) {
        int index = Math.floorMod(key == null ? 0 : key.hashCode(), workers.length);
        workers[index].execute(task);
    }

    @Override
    public void close() {
        for (ExecutorService worker : workers) worker.shutdownNow();
    }
}
