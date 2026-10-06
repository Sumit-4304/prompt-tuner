package io.prompttuner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.prompttuner.internal.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Turns a signature, its demos and an input into chat messages, and turns the model's reply back
 * into the output record. Inputs, demos and replies are all exchanged as JSON objects.
 */
public final class JsonPromptAdapter {

    static final String REASONING_FIELD = "reasoning";

    public <I, O> List<Message> format(Signature<I, O> signature,
                                       List<Example<I, O>> demos,
                                       I input,
                                       boolean reasoning) {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.system(systemPrompt(signature, reasoning)));
        for (Example<I, O> demo : demos) {
            messages.add(Message.user(toJson(demo.input())));
            messages.add(Message.assistant(toJson(demo.output())));
        }
        messages.add(Message.user(toJson(input)));
        return messages;
    }

    public <O> Prediction<O> parse(String raw, Class<O> outputType, boolean reasoning) {
        if (raw == null || raw.isBlank()) {
            throw new PromptTunerException("The model returned an empty reply");
        }
        JsonNode node = readJsonObject(raw);
        String thoughts = null;
        if (reasoning && node.has(REASONING_FIELD)) {
            thoughts = node.get(REASONING_FIELD).asText();
            ((ObjectNode) node).remove(REASONING_FIELD);
        }
        try {
            return new Prediction<>(Json.MAPPER.treeToValue(node, outputType), thoughts, raw);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new PromptTunerException(
                    "Reply does not match " + outputType.getSimpleName() + ": " + abbreviate(raw), e);
        }
    }

    private String systemPrompt(Signature<?, ?> signature, boolean reasoning) {
        List<String> outputKeys = new ArrayList<>();
        StringBuilder sb = new StringBuilder(signature.instructions()).append("\n\n");

        sb.append("Input fields:\n").append(describe(signature.inputFields()));
        sb.append("\nOutput fields:\n");
        if (reasoning) {
            sb.append("- ").append(REASONING_FIELD).append(": think step by step before giving the other fields\n");
            outputKeys.add(REASONING_FIELD);
        }
        sb.append(describe(signature.outputFields()));
        signature.outputFields().forEach(f -> outputKeys.add(f.name()));

        sb.append("\nYou will receive the input fields as a JSON object. Reply with one JSON object ")
          .append("containing exactly these keys, in this order: ")
          .append(outputKeys.stream().map(k -> "\"" + k + "\"").collect(Collectors.joining(", ")))
          .append(". Do not add any other text.");
        return sb.toString();
    }

    private static String describe(List<Signature.Field> fields) {
        StringBuilder sb = new StringBuilder();
        for (Signature.Field f : fields) {
            sb.append("- ").append(f.name()).append(" (").append(f.type().getSimpleName()).append(")");
            if (!f.description().isBlank()) {
                sb.append(": ").append(f.description());
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String toJson(Object value) {
        try {
            return Json.MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new PromptTunerException("Cannot write " + value.getClass().getSimpleName() + " as JSON", e);
        }
    }

    /** Accepts bare JSON, JSON inside ``` fences, or JSON surrounded by stray text. */
    private static JsonNode readJsonObject(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new PromptTunerException("No JSON object in the model reply: " + abbreviate(raw));
        }
        try {
            JsonNode node = Json.MAPPER.readTree(raw.substring(start, end + 1));
            if (!node.isObject()) {
                throw new PromptTunerException("Model reply is not a JSON object: " + abbreviate(raw));
            }
            return node;
        } catch (JsonProcessingException e) {
            throw new PromptTunerException("Invalid JSON in the model reply: " + abbreviate(raw), e);
        }
    }

    private static String abbreviate(String s) {
        return s.length() <= 300 ? s : s.substring(0, 300) + "...";
    }
}
