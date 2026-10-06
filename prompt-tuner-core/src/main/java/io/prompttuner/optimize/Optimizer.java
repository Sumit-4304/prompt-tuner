package io.prompttuner.optimize;

import io.prompttuner.Example;
import io.prompttuner.Predict;

import java.util.List;

/**
 * Finds better instructions and/or demos for a {@link Predict} using training examples.
 * The returned program keeps the student's language model.
 */
public interface Optimizer<I, O> {

    OptimizationResult<I, O> optimize(Predict<I, O> student, List<Example<I, O>> trainset);
}
