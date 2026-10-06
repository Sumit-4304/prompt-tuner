package io.prompttuner.springai;

import io.prompttuner.Message;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpringAiLanguageModelTest {

    @Test
    void convertsRolesAndReturnsReplyText() {
        AtomicReference<Prompt> seen = new AtomicReference<>();
        ChatModel chatModel = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                seen.set(prompt);
                return new ChatResponse(List.of(new Generation(new AssistantMessage("{\"answer\":\"Paris\"}"))));
            }
        };

        String reply = SpringAiLanguageModel.of(chatModel).complete(List.of(
                Message.system("Answer briefly."),
                Message.user("{\"question\":\"Capital of Italy?\"}"),
                Message.assistant("{\"answer\":\"Rome\"}"),
                Message.user("{\"question\":\"Capital of France?\"}")));

        assertEquals("{\"answer\":\"Paris\"}", reply);
        List<MessageType> types = seen.get().getInstructions().stream().map(m -> m.getMessageType()).toList();
        assertEquals(List.of(MessageType.SYSTEM, MessageType.USER, MessageType.ASSISTANT, MessageType.USER), types);
    }
}
