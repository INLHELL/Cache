package cache.impl.concurrent;

import cache.api.Cache;
import cache.api.Cache.TYPE;
import cache.impl.DefaultCache;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Created by user on 26.05.2015.
 */
public class LRUBasicConcurrentCacheTest {
    private static final int NUMBER_OF_WRITERS = 4;
    private static final int NUMBER_OF_READERS = 3;
    private static final Cache<Integer, String> cache = new DefaultCache<>(TYPE.LRU, 30_000);
    private static final CountDownLatch startWritersLatch = new CountDownLatch(1);
    private static final CountDownLatch startFirstWriterLatch = new CountDownLatch(1);
    private static final CountDownLatch endFirstWriterLatch = new CountDownLatch(1);
    private static final CountDownLatch endWritersLatch = new CountDownLatch(2);
    private static final CountDownLatch startLastWriterLatch = new CountDownLatch(1);
    private static final CountDownLatch endLastWriterLatch = new CountDownLatch(1);
    private static final CountDownLatch endToucherLatch = new CountDownLatch(1);
    private static final ExecutorService executor = Executors.newFixedThreadPool(NUMBER_OF_WRITERS + NUMBER_OF_READERS);
    private static final Set<String> finalResultSet = new ConcurrentSkipListSet<>();


    @Before
    public void setUp() throws InterruptedException, ExecutionException {
        // 1. Put 20_000 elements to cache
        executor.submit(new Writer(0, 10_000, startWritersLatch, endWritersLatch));
        executor.submit(new Writer(10_000, 20_000, startWritersLatch, endWritersLatch));
        // 2. Start writers simultaneously
        startWritersLatch.countDown();
        // 3.Wait before first two writers finish their job
        endWritersLatch.await();
        // 4.Put anther 10_000 elements to cache (cache will be fully loaded)
        executor.submit(new Writer(20_000, 30_000, startFirstWriterLatch, endFirstWriterLatch));
        // 5. Start third writer
        startFirstWriterLatch.countDown();
        // 6. Wait for third writer
        endFirstWriterLatch.await();
        // 7. Touch elements from 0 to 10_000 (they will be made 'most recently used' in cache)
        executor.submit(new Toucher(0, 10_000));
        // 8. Wait until toucher thread done its work
        endToucherLatch.await();
        // 9. Submit another two writer threads that will put to cache 20_000 elements
        // 10_000 to 20_000 will be removed and 20_000 to 30_000 as well
        executor.submit(new Writer(30_000, 40_000, startLastWriterLatch, endLastWriterLatch));
        executor.submit(new Writer(40_000, 50_000, startLastWriterLatch, endLastWriterLatch));
        startLastWriterLatch.countDown();
        endLastWriterLatch.await();
        // 10. Collect all elements from cache
        try {
            Thread.sleep(100L);
        } catch (InterruptedException e) {
            Assert.fail("InterruptedException should not be thrown ");
        }
        final Future<Set<String>> firstFuture = executor.submit(new Reader(0, 10_000));
        final Future<Set<String>> secondFuture = executor.submit(new Reader(30_000, 40_000));
        final Future<Set<String>> thirdFuture = executor.submit(new Reader(40_000, 50_000));
        executor.shutdown();

        finalResultSet.addAll(firstFuture.get());
        finalResultSet.addAll(secondFuture.get());
        finalResultSet.addAll(thirdFuture.get());
    }

    @Test
    public void testAllElementsWereInsertedToCache() {
        Assert.assertTrue(finalResultSet.size() == 30_000);
        for (int i = 0; i < 10_000; i++) {
            Assert.assertNotNull(cache.get(i));
            Assert.assertEquals(String.valueOf(i), cache.get(i));
        }
        for (int i = 30_000; i < 50_000; i++) {
            Assert.assertNotNull(cache.get(i));
            Assert.assertEquals(String.valueOf(i), cache.get(i));
        }
    }

    private static class Writer implements Callable<Void> {
        private final int startRangeIncl;
        private final int endRangeExcl;
        private final CountDownLatch startLatch;
        private final CountDownLatch endLatch;

        public Writer(int startRangeIncl, int endRangeExcl, CountDownLatch startLatch, CountDownLatch endLatch) {
            this.startRangeIncl = startRangeIncl;
            this.endRangeExcl = endRangeExcl;
            this.startLatch = startLatch;
            this.endLatch = endLatch;
        }

        @Override
        public Void call() throws Exception {
            this.startLatch.await();
            for (int i = this.startRangeIncl; i < this.endRangeExcl; i++) {
                cache.put(i, String.valueOf(i));
            }
            this.endLatch.countDown();
            return null;
        }
    }

    private static class Reader implements Callable<Set<String>> {
        private final int startRangeIncl;
        private final int endRangeExcl;

        public Reader(int startRangeIncl, int endRangeExcl) {
            this.startRangeIncl = startRangeIncl;
            this.endRangeExcl = endRangeExcl;
        }

        @Override
        public Set<String> call() throws Exception {
            final Set<String> resultSet = new HashSet<>();
            for (int i = this.startRangeIncl; i < this.endRangeExcl; i++) {
                resultSet.add(cache.get(i));
            }
            return resultSet;
        }
    }

    private static class Toucher implements Callable<Void> {
        private final int startRangeIncl;
        private final int endRangeExcl;

        public Toucher(int startRangeIncl, int endRangeExcl) {
            this.startRangeIncl = startRangeIncl;
            this.endRangeExcl = endRangeExcl;
        }

        @Override
        public Void call() {
            for (int i = this.startRangeIncl; i < this.endRangeExcl; i++) {
                cache.get(i);
            }
            endToucherLatch.countDown();
            return null;
        }
    }
}
