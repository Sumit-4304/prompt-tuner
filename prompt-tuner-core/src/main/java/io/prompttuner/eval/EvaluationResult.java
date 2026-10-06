package io.prompttuner.eval;

import io.prompttuner.Example;

import java.util.List;

/**
 * The average score of a program on a set of examples, plus every individual result.
 *
 * @param score average score, 0.0 to 1.0
 * @param rows  one row per example, in the original order
 */
public record EvaluationResult<I, O>(double score, List<Row<I, O>> rows) {

    /**
     * @param prediction what the program returned, or {@code null} if it failed
     * @param error      why it failed, or {@code null}
     */
    public record Row<I, O>(Example<I, O> example, O prediction, double score, Throwable error) {}

    public long failures() {
        return rows.stream().filter(r -> r.error() != null).count();
    }

    @Override
    public String toString() {
        return String.format("score=%.3f on %d examples (%d failed)", score, rows.size(), failures());
    }
}
