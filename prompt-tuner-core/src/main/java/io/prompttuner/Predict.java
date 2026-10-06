package io.prompttuner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.prompttuner.internal.Json;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One model call described by a {@link Signature}. This is the part optimizers tune: its
 * instructions and its few-shot demos. Instances are immutable; every {@code with...} returns a copy.
 *
 * <pre>{@code
 * Predict<Question, Answer> qa = Predict.of(Question.class, Answer.class).withLm(model);
 * Answer a = qa.run(new Question("What is the capital of France?"));
 * }</pre>
 */
public final class Predict<I, O> implements Program<I, O> {

    static final String FORMAT = "prompttuner/v1";

    private final Signature<I, O> signature;
    private final List<Example<I, O>> demos;
    private final boolean reasoning;
    private final LanguageModel lm;
    private final JsonPromptAdapter adapter;

    private Predict(Signature<I, O> signature, List<Example<I, O>> demos, boolean reasoning,
                    LanguageModel lm, JsonPromptAdapter adapter) {
        this.signature = Objects.requireNonNull(signature, "signature");
        this.demos = List.copyOf(demos);
        this.reasoning = reasoning;
        this.lm = lm;
        this.adapter = adapter;
    }

    /** A plain step: the model answers directly. */
    public static <I, O> Predict<I, O> of(Class<I> inputType, Class<O> outputType) {
        return new Predict<>(Signature.of(inputType, outputType), List.of(), false, null, new JsonPromptAdapter());
    }

    /** A step where the model writes its reasoning before answering. Often more accurate, costs more tokens. */
    public static <I, O> Predict<I, O> chainOfThought(Class<I> inputType, Class<O> outputType) {
        return new Predict<>(Signature.of(inputType, outputType), List.of(), true, null, new JsonPromptAdapter());
    }

    public Predict<I, O> withLm(LanguageModel lm) {
        return new Predict<>(signature, demos, reasoning, Objects.requireNonNull(lm, "lm"), adapter);
    }

    public Predict<I, O> withInstructions(String instructions) {
        return new Predict<>(signature.withInstructions(instructions), demos, reasoning, lm, adapter);
    }

    public Predict<I, O> withDemos(List<Example<I, O>> demos) {
        return new Predict<>(signature, demos, reasoning, lm, adapter);
    }

    @Override
    public O run(I input) {
        return predict(input).output();
    }

    /** Like {@link #run} but also returns the reasoning and the raw reply. */
    public Prediction<O> predict(I input) {
        Objects.requireNonNull(input, "input");
        if (lm == null) {
            throw new IllegalStateException("No language model set. Call withLm(...) first.");
        }
        List<Message> messages = adapter.format(signature, demos, input, reasoning);
        String reply = lm.complete(messages);
        return adapter.parse(reply, signature.outputType(), reasoning);
    }

    /** The exact messages that would be sent for this input. Handy for debugging and reviews. */
    public List<Message> preview(I input) {
        return adapter.format(signature, demos, input, reasoning);
    }

    public Signature<I, O> signature() {
        return signature;
    }

    public List<Example<I, O>> demos() {
        return demos;
    }

    public boolean usesReasoning() {
        return reasoning;
    }

    public LanguageModel lm() {
        return lm;
    }

    /** Saves the tuned instructions and demos as JSON, so production can load them without re-tuning. */
    public void save(Path path) {
        ObjectNode root = Json.MAPPER.createObjectNode();
        root.put("format", FORMAT);
        root.put("inputType", signature.inputType().getName());
        root.put("outputType", signature.outputType().getName());
        root.put("instructions", signature.instructions());
        root.put("reasoning", reasoning);
        ArrayNode demoArray = root.putArray("demos");
        for (Example<I, O> demo : demos) {
            ObjectNode d = demoArray.addObject();
            d.set("input", Json.MAPPER.valueToTree(demo.input()));
            d.set("output", Json.MAPPER.valueToTree(demo.output()));
        }
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Json.MAPPER.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), root);
        } catch (IOException e) {
            throw new PromptTunerException("Cannot save program to " + path, e);
        }
    }

    /** Loads a program saved with {@link #save}. Call {@link #withLm} on the result before running it. */
    public static <I, O> Predict<I, O> load(Path path, Class<I> inputType, Class<O> outputType) {
        try {
            return fromJson(Json.MAPPER.readTree(path.toFile()), inputType, outputType);
        } catch (IOException e) {
            throw new PromptTunerException("Cannot load program from " + path, e);
        }
    }

    /** Same as {@link #load(Path, Class, Class)}, e.g. for a tuned program packaged on the classpath. Closes the stream. */
    public static <I, O> Predict<I, O> load(InputStream in, Class<I> inputType, Class<O> outputType) {
        try (in) {
            return fromJson(Json.MAPPER.readTree(in), inputType, outputType);
        } catch (IOException e) {
            throw new PromptTunerException("Cannot load program from stream", e);
        }
    }

    static <I, O> Predict<I, O> fromJson(JsonNode root, Class<I> inputType, Class<O> outputType) {
        if (!FORMAT.equals(root.path("format").asText())) {
            throw new PromptTunerException("Unsupported program format: " + root.path("format").asText());
        }
        checkType(root, "inputType", inputType);
        checkType(root, "outputType", outputType);
        try {
            List<Example<I, O>> demos = new ArrayList<>();
            for (JsonNode d : root.path("demos")) {
                demos.add(Example.of(Json.MAPPER.treeToValue(d.get("input"), inputType),
                        Json.MAPPER.treeToValue(d.get("output"), outputType)));
            }
            Signature<I, O> signature = Signature.of(inputType, outputType)
                    .withInstructions(root.path("instructions").asText());
            return new Predict<>(signature, demos, root.path("reasoning").asBoolean(false), null, new JsonPromptAdapter());
        } catch (IOException e) {
            throw new PromptTunerException("Saved demos do not match the given record types", e);
        }
    }

    private static void checkType(JsonNode root, String key, Class<?> expected) {
        String saved = root.path(key).asText();
        if (!saved.equals(expected.getName())) {
            throw new PromptTunerException("Program was saved for " + key + " " + saved + ", not " + expected.getName());
        }
    }
}
