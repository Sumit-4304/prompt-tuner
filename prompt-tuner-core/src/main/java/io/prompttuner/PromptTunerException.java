package io.prompttuner;

/** Thrown when a model reply cannot be used or a program cannot be run, saved or loaded. */
public class PromptTunerException extends RuntimeException {

    public PromptTunerException(String message) {
        super(message);
    }

    public PromptTunerException(String message, Throwable cause) {
        super(message, cause);
    }
}
