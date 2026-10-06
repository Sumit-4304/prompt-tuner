package io.prompttuner;

/**
 * What a {@link Predict} call produced.
 *
 * @param output    the parsed output record
 * @param reasoning the model's step-by-step reasoning, or {@code null} when reasoning is off
 * @param raw       the reply text exactly as the model returned it
 */
public record Prediction<O>(O output, String reasoning, String raw) {}
