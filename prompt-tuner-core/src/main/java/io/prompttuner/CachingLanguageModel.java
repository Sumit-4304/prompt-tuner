package io.prompttuner;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Remembers replies for identical message lists. Tuning sends the same prompt many times, so this
 * cuts cost and makes runs repeatable. Best used with temperature 0. In-memory only for now.
 */
public final class CachingLanguageModel implements LanguageModel {

    private final LanguageModel delegate;
    private final Map<List<Message>, String> cache = new ConcurrentHashMap<>();
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();

    public CachingLanguageModel(LanguageModel delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public String complete(List<Message> messages) {
        List<Message> key = List.copyOf(messages);
        String cached = cache.get(key);
        if (cached != null) {
            hits.incrementAndGet();
            return cached;
        }
        misses.incrementAndGet();
        String reply = delegate.complete(key);
        if (reply != null) {
            cache.put(key, reply);
        }
        return reply;
    }

    public long hits() {
        return hits.get();
    }

    /** Number of calls that actually reached the underlying model. */
    public long misses() {
        return misses.get();
    }

    public void clear() {
        cache.clear();
    }
}
