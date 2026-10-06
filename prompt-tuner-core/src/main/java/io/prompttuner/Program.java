package io.prompttuner;

/** Anything that turns an input record into an output record, usually by calling a model. */
@FunctionalInterface
public interface Program<I, O> {

    O run(I input);
}
