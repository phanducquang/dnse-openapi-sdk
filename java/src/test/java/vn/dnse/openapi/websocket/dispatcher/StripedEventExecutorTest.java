package vn.dnse.openapi.websocket.dispatcher;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StripedEventExecutorTest {

    @Test
    void preservesOrderingWhenQueueIsFull() throws Exception {
        try (StripedEventExecutor executor = new StripedEventExecutor(1, 1)) {
            List<Integer> received = Collections.synchronizedList(new ArrayList<>());
            CountDownLatch firstStarted = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);
            CountDownLatch completed = new CountDownLatch(3);

            executor.execute("FPT", () -> {
                firstStarted.countDown();
                await(releaseFirst);
                received.add(1);
                completed.countDown();
            });
            assertTrue(firstStarted.await(1, TimeUnit.SECONDS));

            executor.execute("FPT", () -> {
                received.add(2);
                completed.countDown();
            });

            Thread thirdSubmitter = new Thread(() -> executor.execute("FPT", () -> {
                received.add(3);
                completed.countDown();
            }));
            thirdSubmitter.start();

            Thread.sleep(Duration.ofMillis(50).toMillis());
            assertTrue(thirdSubmitter.isAlive(), "third submission must apply backpressure instead of running out of order");

            releaseFirst.countDown();
            thirdSubmitter.join(1_000);
            assertFalse(thirdSubmitter.isAlive());
            assertTrue(completed.await(1, TimeUnit.SECONDS));
            assertEquals(List.of(1, 2, 3), received);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
