package io.prompttuner.optimize;

import io.prompttuner.Example;
import io.prompttuner.Metric;
import io.prompttuner.Predict;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Lets a teacher program answer the training examples and keeps the answers that pass the metric
 * as demos ("bootstrapped" demos). Then tops up with plain labeled examples.
 *
 * <p>Why it helps: demos written by the model itself match the format and style the model
 * produces, and every kept demo has been checked against your metric.
 *
 * <pre>{@code
 * var result = BootstrapFewShot.with(metric).maxBootstrapped(4).maxLabeled(4).build()
 *         .optimize(student, trainset);
 * }</pre>
 */
public final class BootstrapFewShot<I, O> implements Optimizer<I, O> {

    private final Metric<I, O> metric;
    private final Predict<I, O> teacher;
    private final int maxBootstrapped;
    private final int maxLabeled;
    private final double passThreshold;
    private final long seed;

    private BootstrapFewShot(Builder<I, O> b) {
        this.metric = b.metric;
        this.teacher = b.teacher;
        this.maxBootstrapped = b.maxBootstrapped;
        this.maxLabeled = b.maxLabeled;
        this.passThreshold = b.passThreshold;
        this.seed = b.seed;
    }

    public static <I, O> Builder<I, O> with(Metric<I, O> metric) {
        return new Builder<>(metric);
    }

    @Override
    public OptimizationResult<I, O> optimize(Predict<I, O> student, List<Example<I, O>> trainset) {
        Predict<I, O> runner = teacher != null ? teacher : student;
        List<Example<I, O>> order = new ArrayList<>(trainset);
        Collections.shuffle(order, new Random(seed));

        List<Example<I, O>> bootstrapped = new ArrayList<>();
        List<Example<I, O>> unused = new ArrayList<>();
        List<String> log = new ArrayList<>();
        int tried = 0;
        int failed = 0;

        for (Example<I, O> example : order) {
            if (bootstrapped.size() >= maxBootstrapped) {
                unused.add(example);
                continue;
            }
            tried++;
            try {
                O prediction = runner.run(example.input());
                if (metric.score(example, prediction) >= passThreshold) {
                    bootstrapped.add(Example.of(example.input(), prediction));
                } else {
                    unused.add(example);
                }
            } catch (RuntimeException e) {
                failed++;
                unused.add(example);
            }
        }

        List<Example<I, O>> labeled = unused.subList(0, Math.min(maxLabeled, unused.size()));
        List<Example<I, O>> demos = new ArrayList<>(bootstrapped);
        demos.addAll(labeled);

        log.add(String.format("bootstrap (seed=%d): ran %d examples, %d passed, %d errors; demos = %d bootstrapped + %d labeled",
                seed, tried, bootstrapped.size(), failed, bootstrapped.size(), labeled.size()));
        return new OptimizationResult<>(student.withDemos(demos), Double.NaN, log);
    }

    public static final class Builder<I, O> {
        private final Metric<I, O> metric;
        private Predict<I, O> teacher;
        private int maxBootstrapped = 4;
        private int maxLabeled = 4;
        private double passThreshold = 1.0;
        private long seed = 0L;

        private Builder(Metric<I, O> metric) {
            this.metric = Objects.requireNonNull(metric, "metric");
        }

        /** Use a stronger (or differently configured) program to write the demos. Defaults to the student. */
        public Builder<I, O> teacher(Predict<I, O> teacher) {
            this.teacher = teacher;
            return this;
        }

        public Builder<I, O> maxBootstrapped(int n) {
            this.maxBootstrapped = requireNonNegative(n);
            return this;
        }

        public Builder<I, O> maxLabeled(int n) {
            this.maxLabeled = requireNonNegative(n);
            return this;
        }

        /** Minimum metric score for a teacher answer to become a demo. Default 1.0. */
        public Builder<I, O> passThreshold(double threshold) {
            this.passThreshold = threshold;
            return this;
        }

        public Builder<I, O> seed(long seed) {
            this.seed = seed;
            return this;
        }

        public BootstrapFewShot<I, O> build() {
            return new BootstrapFewShot<>(this);
        }

        private static int requireNonNegative(int n) {
            if (n < 0) {
                throw new IllegalArgumentException("must not be negative: " + n);
            }
            return n;
        }
    }
}
