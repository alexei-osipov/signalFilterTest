package test.filter.api;

import test.filter.impl.AtomicLockFilter;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This is a small app that demonstrates how a {@link Filter} can be used.
 *
 * If you want to score some extra points you can implement JUnit tests for your implementation.
 *
 * @author Alexei Osipov
 */
public class FilterTest {
    private static final int numberOfSignalsPerProducer = 300000;
    private static final int numberOfSignalsProducers = 20;

    private static class RandomFilter implements Filter {
        private final Random rnd = new Random();

        /** @param N maximum number of signals per last 100 seconds */
        private RandomFilter (int N) {
            // this dummy implementation ignores the limit parameter
        }

        @Override
        public boolean isSignalAllowed() {
            return rnd.nextBoolean();
        }
    }

    private static class TestProducer extends Thread {
        private final Filter filter;
        private final AtomicInteger totalPassed;

        private TestProducer(Filter filter, AtomicInteger totalPassed) {
            this.filter = filter;
            this.totalPassed = totalPassed;
        }

        @Override
        public void run() {
            Random rnd = new Random ();
            try {
                for (int j = 0; j < numberOfSignalsPerProducer; j++) {
                    if (filter.isSignalAllowed())
                        totalPassed.incrementAndGet();
                    Thread.sleep(rnd.nextInt(1));
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main (String ... args) throws InterruptedException {
        final int N = 1000000;
        Filter filter = new AtomicLockFilter(N); //TODO: replace by your implementation

        long startTime = System.currentTimeMillis();

        AtomicInteger totalPassed = new AtomicInteger();
        Thread [] producers = new Thread[numberOfSignalsProducers];
        for (int i=0; i < producers.length; i++)
            producers[i] = new TestProducer(filter, totalPassed);

        for (Thread producer : producers)
            producer.start();

        for (Thread producer : producers)
            producer.join();

        long endTime = System.currentTimeMillis();

        System.out.println("Filter allowed " + totalPassed + " signals out of " + (numberOfSignalsPerProducer * numberOfSignalsProducers) + " in " + (endTime - startTime) + " ms");
    }

}
