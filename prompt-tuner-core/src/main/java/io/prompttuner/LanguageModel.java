package io.prompttuner;

import java.util.List;

/**
 * The only thing PromptTuner needs from an LLM: take a chat and return the reply text.
 *
 * <p>Use an adapter module (for example {@code prompt-tuner-spring-ai}) to plug in a real
 * provider, or implement this yourself in a few lines.
 */
@FunctionalInterface
public interface LanguageModel {

    String complete(List<Message> messages);
}
