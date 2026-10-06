package io.prompttuner.optimize;

import io.prompttuner.Example;
import io.prompttuner.Predict;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** The simplest optimizer: picks {@code k} of your labeled examples at random as demos. No model calls. */
public final class LabeledFewShot<I, O> implements Optimizer<I, O> {

    private final int k;
    private final long seed;

    public LabeledFewShot(int k, long seed) {
        if (k < 0) {
            throw new IllegalArgumentException("k must not be negative");
        }
        this.k = k;
        this.seed = seed;
    }

    public LabeledFewShot(int k) {
        this(k, 0L);
    }

    @Override
    public OptimizationResult<I, O> optimize(Predict<I, O> student, List<Example<I, O>> trainset) {
        List<Example<I, O>> shuffled = new ArrayList<>(trainset);
        Collections.shuffle(shuffled, new Random(seed));
        List<Example<I, O>> demos = shuffled.subList(0, Math.min(k, shuffled.size()));
        return new OptimizationResult<>(student.withDemos(demos), Double.NaN,
                List.of("labeled few-shot: picked " + demos.size() + " demos (seed=" + seed + ")"));
    }
}
