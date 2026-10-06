package io.prompttuner.eval;

import io.prompttuner.Example;
import io.prompttuner.Metric;
import io.prompttuner.Program;
import io.prompttuner.internal.Concurrency;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

/**
 * Runs a program on every example in parallel and averages the metric. A failed call (bad reply,
 * timeout, provider error) scores 0 instead of stopping the run.
 *
 * <p>Uses virtual threads on Java 21+, a thread pool on Java 17. {@code parallelism} caps how many
 * model calls are in flight at once, whatever the thread type.
 */
public final class Evaluator<I, O> {

    private final Metric<I, O> metric;
    private final int parallelism;

    private Evaluator(Metric<I, O> metric, int parallelism) {
        this.metric = Objects.requireNonNull(metric, "metric");
        if (parallelism < 1) {
            throw new IllegalArgumentException("parallelism must be at least 1");
        }
        this.parallelism = parallelism;
    }

    public static <I, O> Evaluator<I, O> of(Metric<I, O> metric) {
        return new Evaluator<>(metric, 8);
    }

    public Evaluator<I, O> parallelism(int parallelism) {
        return new Evaluator<>(metric, parallelism);
    }

    public EvaluationResult<I, O> evaluate(Program<I, O> program, List<Example<I, O>> examples) {
        Objects.requireNonNull(program, "program");
        if (examples.isEmpty()) {
            throw new IllegalArgumentException("Cannot evaluate on an empty example list");
        }
        Semaphore permits = new Semaphore(parallelism);
        ExecutorService executor = Concurrency.newExecutor(parallelism);
        try {
            List<Future<EvaluationResult.Row<I, O>>> futures = new ArrayList<>(examples.size());
            for (Example<I, O> example : examples) {
                futures.add(executor.submit(() -> {
                    permits.acquire();
                    try {
                        return runOne(program, example);
                    } finally {
                        permits.release();
                    }
                }));
            }
            List<EvaluationResult.Row<I, O>> rows = new ArrayList<>(examples.size());
            double total = 0;
            for (Future<EvaluationResult.Row<I, O>> future : futures) {
                EvaluationResult.Row<I, O> row = future.get();
                rows.add(row);
                total += row.score();
            }
            return new EvaluationResult<>(total / rows.size(), List.copyOf(rows));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Evaluation interrupted", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Evaluation failed", e.getCause());
        } finally {
            executor.shutdownNow();
        }
    }

    private EvaluationResult.Row<I, O> runOne(Program<I, O> program, Example<I, O> example) {
        O prediction;
        try {
            prediction = program.run(example.input());
        } catch (RuntimeException e) {
            return new EvaluationResult.Row<>(example, null, 0.0, e);
        }
        double score = clamp(metric.score(example, prediction));
        return new EvaluationResult.Row<>(example, prediction, score, null);
    }

    private static double clamp(double score) {
        if (Double.isNaN(score)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, score));
    }
}
