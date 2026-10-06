package io.prompttuner.optimize;

import io.prompttuner.Predict;

import java.util.List;

/**
 * @param program the tuned program
 * @param score   its score on the validation set, or {@code NaN} if the optimizer did not measure it
 * @param log     human-readable notes on what was tried
 */
public record OptimizationResult<I, O>(Predict<I, O> program, double score, List<String> log) {

    public boolean scored() {
        return !Double.isNaN(score);
    }
}
