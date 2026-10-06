package io.prompttuner;

import java.util.Objects;

/**
 * An input with the output you consider correct. Used for training, scoring, and as few-shot demos.
 */
public record Example<I, O>(I input, O output) {

    public Example {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(output, "output");
    }

    public static <I, O> Example<I, O> of(I input, O output) {
        return new Example<>(input, output);
    }
}
