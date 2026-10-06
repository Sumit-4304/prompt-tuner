package io.prompttuner;

import java.util.Objects;

/** One chat message sent to a {@link LanguageModel}. */
public record Message(Role role, String content) {

    public enum Role { SYSTEM, USER, ASSISTANT }

    public Message {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(content, "content");
    }

    public static Message system(String content) {
        return new Message(Role.SYSTEM, content);
    }

    public static Message user(String content) {
        return new Message(Role.USER, content);
    }

    public static Message assistant(String content) {
        return new Message(Role.ASSISTANT, content);
    }
}
