package io.prompttuner.springai;

import io.prompttuner.LanguageModel;
import io.prompttuner.Message;
import io.prompttuner.PromptTunerException;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Objects;

/**
 * Runs PromptTuner programs on any Spring AI {@link ChatModel}: OpenAI, Bedrock, Anthropic,
 * Azure, Ollama, and the rest.
 *
 * <pre>{@code
 * LanguageModel lm = SpringAiLanguageModel.of(chatModel,
 *         ChatOptions.builder().temperature(0.0).build());
 * }</pre>
 */
public final class SpringAiLanguageModel implements LanguageModel {

    private final ChatModel chatModel;
    private final ChatOptions options;

    private SpringAiLanguageModel(ChatModel chatModel, ChatOptions options) {
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel");
        this.options = options;
    }

    /** Uses the chat model's default options. */
    public static SpringAiLanguageModel of(ChatModel chatModel) {
        return new SpringAiLanguageModel(chatModel, null);
    }

    /** Uses the given options (model, temperature, max tokens) for every call. Temperature 0 suits tuning. */
    public static SpringAiLanguageModel of(ChatModel chatModel, ChatOptions options) {
        return new SpringAiLanguageModel(chatModel, Objects.requireNonNull(options, "options"));
    }

    @Override
    public String complete(List<Message> messages) {
        List<org.springframework.ai.chat.messages.Message> converted = messages.stream()
                .map(SpringAiLanguageModel::convert)
                .toList();
        Prompt prompt = options == null ? new Prompt(converted) : new Prompt(converted, options);
        ChatResponse response = chatModel.call(prompt);
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new PromptTunerException("Spring AI returned no result");
        }
        return response.getResult().getOutput().getText();
    }

    private static org.springframework.ai.chat.messages.Message convert(Message message) {
        return switch (message.role()) {
            case SYSTEM -> new SystemMessage(message.content());
            case USER -> new UserMessage(message.content());
            case ASSISTANT -> new AssistantMessage(message.content());
        };
    }
}
