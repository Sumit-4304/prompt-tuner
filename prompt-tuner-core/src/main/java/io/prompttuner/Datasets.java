package io.prompttuner;

import com.fasterxml.jackson.databind.JsonNode;
import io.prompttuner.internal.Json;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Loading and splitting example sets. */
public final class Datasets {

    /** Examples split into a set to tune on and a set to measure on. */
    public record Split<I, O>(List<Example<I, O>> train, List<Example<I, O>> dev) {}

    private Datasets() {}

    /**
     * Reads one example per line: {@code {"input": {...}, "output": {...}}}. Blank lines are skipped.
     */
    public static <I, O> List<Example<I, O>> fromJsonl(Path path, Class<I> inputType, Class<O> outputType) {
        try {
            return parseJsonl(Files.readAllLines(path, StandardCharsets.UTF_8), path.toString(), inputType, outputType);
        } catch (IOException e) {
            throw new PromptTunerException("Cannot read " + path, e);
        }
    }

    /** Same as {@link #fromJsonl(Path, Class, Class)}, e.g. for a classpath resource. Closes the stream. */
    public static <I, O> List<Example<I, O>> fromJsonl(InputStream in, Class<I> inputType, Class<O> outputType) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return parseJsonl(reader.lines().toList(), "stream", inputType, outputType);
        } catch (IOException e) {
            throw new PromptTunerException("Cannot read examples from stream", e);
        }
    }

    private static <I, O> List<Example<I, O>> parseJsonl(List<String> lines, String source,
                                                         Class<I> inputType, Class<O> outputType) {
        List<Example<I, O>> examples = new ArrayList<>();
        int lineNo = 0;
        try {
            for (String line : lines) {
                lineNo++;
                if (line.isBlank()) {
                    continue;
                }
                JsonNode node = Json.MAPPER.readTree(line);
                if (!node.has("input") || !node.has("output")) {
                    throw new PromptTunerException(source + " line " + lineNo + ": needs \"input\" and \"output\"");
                }
                examples.add(Example.of(Json.MAPPER.treeToValue(node.get("input"), inputType),
                        Json.MAPPER.treeToValue(node.get("output"), outputType)));
            }
        } catch (IOException e) {
            throw new PromptTunerException("Cannot parse " + source + " line " + lineNo, e);
        }
        return examples;
    }

    /** Shuffles with the seed, then puts {@code trainFraction} of the examples in train and the rest in dev. */
    public static <I, O> Split<I, O> split(List<Example<I, O>> examples, double trainFraction, long seed) {
        if (trainFraction <= 0 || trainFraction >= 1) {
            throw new IllegalArgumentException("trainFraction must be between 0 and 1, got " + trainFraction);
        }
        List<Example<I, O>> shuffled = new ArrayList<>(examples);
        Collections.shuffle(shuffled, new Random(seed));
        int cut = (int) Math.round(shuffled.size() * trainFraction);
        return new Split<>(List.copyOf(shuffled.subList(0, cut)), List.copyOf(shuffled.subList(cut, shuffled.size())));
    }
}
