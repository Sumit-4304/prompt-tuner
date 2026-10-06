package io.prompttuner.optimize;

import io.prompttuner.Example;
import io.prompttuner.Metric;
import io.prompttuner.Predict;
import io.prompttuner.eval.Evaluator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * The tuning loop: builds several candidate programs (no demos, labeled demos, and bootstrapped
 * demos from different shuffles), scores each one on a validation set, and returns the best.
 *
 * <p>This is the optimizer to start with. Its result carries a real score you can compare across
 * models and prompt changes.
 */
public final class RandomSearchFewShot<I, O> implements Optimizer<I, O> {

    private final Metric<I, O> metric;
    private final List<Example<I, O>> valset;
    private final int candidates;
    private final int maxBootstrapped;
    private final int maxLabeled;
    private final int parallelism;
    private final long seed;

    private RandomSearchFewShot(Builder<I, O> b) {
        this.metric = b.metric;
        this.valset = b.valset;
        this.candidates = b.candidates;
        this.maxBootstrapped = b.maxBootstrapped;
        this.maxLabeled = b.maxLabeled;
        this.parallelism = b.parallelism;
        this.seed = b.seed;
    }

    public static <I, O> Builder<I, O> with(Metric<I, O> metric) {
        return new Builder<>(metric);
    }

    @Override
    public OptimizationResult<I, O> optimize(Predict<I, O> student, List<Example<I, O>> trainset) {
        List<Example<I, O>> scoringSet = valset != null ? valset : trainset;
        Evaluator<I, O> evaluator = Evaluator.of(metric).parallelism(parallelism);
        Random random = new Random(seed);
        List<String> log = new ArrayList<>();

        Predict<I, O> best = student;
        double bestScore = -1;

        for (int i = 0; i < candidates + 2; i++) {
            OptimizationResult<I, O> candidate;
            String label;
            if (i == 0) {
                candidate = new OptimizationResult<>(student, Double.NaN, List.of());
                label = "as given (" + student.demos().size() + " demos)";
            } else if (i == 1) {
                candidate = new LabeledFewShot<I, O>(maxLabeled, seed).optimize(student, trainset);
                label = "labeled only";
            } else {
                long candidateSeed = seed + i;
                int bootstrapped = 1 + random.nextInt(Math.max(1, maxBootstrapped));
                candidate = BootstrapFewShot.with(metric)
                        .maxBootstrapped(bootstrapped)
                        .maxLabeled(maxLabeled)
                        .seed(candidateSeed)
                        .build()
                        .optimize(student, trainset);
                label = "bootstrap seed=" + candidateSeed;
                log.addAll(candidate.log());
            }

            double score = evaluator.evaluate(candidate.program(), scoringSet).score();
            log.add(String.format("candidate %d (%s, %d demos): score=%.3f",
                    i, label, candidate.program().demos().size(), score));
            if (score > bestScore) {
                bestScore = score;
                best = candidate.program();
            }
        }

        log.add(String.format("best score=%.3f with %d demos", bestScore, best.demos().size()));
        return new OptimizationResult<>(best, bestScore, log);
    }

    public static final class Builder<I, O> {
        private final Metric<I, O> metric;
        private List<Example<I, O>> valset;
        private int candidates = 6;
        private int maxBootstrapped = 4;
        private int maxLabeled = 4;
        private int parallelism = 8;
        private long seed = 0L;

        private Builder(Metric<I, O> metric) {
            this.metric = Objects.requireNonNull(metric, "metric");
        }

        /** Examples used to score candidates. Defaults to the training set; a separate set gives a fairer score. */
        public Builder<I, O> valset(List<Example<I, O>> valset) {
            this.valset = List.copyOf(valset);
            return this;
        }

        /** Number of bootstrapped candidates, on top of the "as given" and "labeled only" baselines. */
        public Builder<I, O> candidates(int n) {
            if (n < 0) {
                throw new IllegalArgumentException("candidates must not be negative");
            }
            this.candidates = n;
            return this;
        }

        public Builder<I, O> maxBootstrapped(int n) {
            this.maxBootstrapped = n;
            return this;
        }

        public Builder<I, O> maxLabeled(int n) {
            this.maxLabeled = n;
            return this;
        }

        /** Maximum model calls in flight while scoring. Keep it under your provider's rate limit. */
        public Builder<I, O> parallelism(int n) {
            this.parallelism = n;
            return this;
        }

        public Builder<I, O> seed(long seed) {
            this.seed = seed;
            return this;
        }

        public RandomSearchFewShot<I, O> build() {
            return new RandomSearchFewShot<>(this);
        }
    }
}
