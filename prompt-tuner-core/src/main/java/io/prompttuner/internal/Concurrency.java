package io.prompttuner.internal;

import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Picks the best executor for the running JVM. Internal: not part of the public API.
 *
 * <p>On Java 21+ LLM calls run on virtual threads, which cost almost nothing while they wait on
 * the network. On Java 17-20 they fall back to a fixed pool of platform threads. Either way the
 * caller still limits how many calls run at once, so provider rate limits are respected.
 */
public final class Concurrency {

    private static final Method VIRTUAL_EXECUTOR = findVirtualExecutorFactory();

    private Concurrency() {}

    public static boolean virtualThreadsAvailable() {
        return VIRTUAL_EXECUTOR != null;
    }

    public static ExecutorService newExecutor(int parallelism) {
        if (VIRTUAL_EXECUTOR != null) {
            try {
                return (ExecutorService) VIRTUAL_EXECUTOR.invoke(null);
            } catch (ReflectiveOperationException ignored) {
                // fall through to platform threads
            }
        }
        AtomicInteger counter = new AtomicInteger();
        return Executors.newFixedThreadPool(Math.max(1, parallelism), r -> {
            Thread t = new Thread(r, "prompttuner-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    private static Method findVirtualExecutorFactory() {
        try {
            return Executors.class.getMethod("newVirtualThreadPerTaskExecutor");
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
