package net.blazemc.connectiondetector.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ConcurrentLoginGuardTest {

    @Test
    void secondConnectionForTheSamePremiumUsernameIsRejected() {
        ConcurrentLoginGuard guard = new ConcurrentLoginGuard();
        assertTrue(guard.tryAcquire("Blaz3Craft", "first"));
        assertFalse(guard.tryAcquire("blaz3craft", "second"));
        assertTrue(guard.tryAcquire("Blaz3Craft", "first"));
        guard.release("Blaz3Craft", "first");
        assertTrue(guard.tryAcquire("Blaz3Craft", "second"));
    }

    @Test
    void expiredHoldDoesNotBlockTheRealAccount() throws InterruptedException {
        ConcurrentLoginGuard guard = new ConcurrentLoginGuard(Duration.ofMillis(25));
        assertTrue(guard.tryAcquire("Blaz3Craft", "stale"));
        Thread.sleep(40);
        assertTrue(guard.tryAcquire("Blaz3Craft", "fresh"));
    }

    @Test
    void concurrentAttemptsAllowOnlyOneWinner() throws Exception {
        ConcurrentLoginGuard guard = new ConcurrentLoginGuard();
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger winners = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            String id = "c" + i;
            futures.add(pool.submit(() -> {
                start.await();
                if (guard.tryAcquire("Blaz3Craft", id)) {
                    winners.incrementAndGet();
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get(5, TimeUnit.SECONDS);
        }
        pool.shutdownNow();
        assertEquals(1, winners.get());
    }
}
