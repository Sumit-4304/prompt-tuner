package io.prompttuner;

import java.util.Objects;
import java.util.function.Function;

/**
 * Says how good a prediction is for one example, from 0.0 (wrong) to 1.0 (perfect).
 * This is how you tell PromptTuner what "good" means for your feature.
 */
@FunctionalInterface
public interface Metric<I, O> {

    double score(Example<I, O> example, O prediction);

    /** 1.0 when the chosen field matches the expected value, ignoring case and surrounding spaces. */
    static <I, O> Metric<I, O> exactMatch(Function<O, ?> field) {
        Objects.requireNonNull(field, "field");
        return (example, prediction) -> {
            if (prediction == null) {
                return 0.0;
            }
            return normalize(field.apply(example.output())).equals(normalize(field.apply(prediction))) ? 1.0 : 0.0;
        };
    }

    private static String normalize(Object value) {
        return value == null ? "" : value.toString().trim().toLowerCase();
    }
}
