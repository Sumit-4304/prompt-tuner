package io.prompttuner;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * What a step takes in and gives back, declared as two Java records instead of a hand-written prompt.
 *
 * <pre>{@code
 * record Question(String question) {}
 * record Answer(@Desc("short factual answer") String answer) {}
 *
 * Signature<Question, Answer> qa = Signature.of(Question.class, Answer.class);
 * }</pre>
 */
public final class Signature<I, O> {

    /** One input or output field as the model sees it. */
    public record Field(String name, String description, Class<?> type) {}

    private final Class<I> inputType;
    private final Class<O> outputType;
    private final String instructions;
    private final List<Field> inputFields;
    private final List<Field> outputFields;

    private Signature(Class<I> inputType, Class<O> outputType, String instructions) {
        this.inputType = requireRecord(inputType, "input");
        this.outputType = requireRecord(outputType, "output");
        this.inputFields = fieldsOf(inputType);
        this.outputFields = fieldsOf(outputType);
        this.instructions = instructions != null ? instructions : defaultInstructions(inputFields, outputFields);
    }

    public static <I, O> Signature<I, O> of(Class<I> inputType, Class<O> outputType) {
        return new Signature<>(inputType, outputType, null);
    }

    public Signature<I, O> withInstructions(String instructions) {
        Objects.requireNonNull(instructions, "instructions");
        return new Signature<>(inputType, outputType, instructions);
    }

    public Class<I> inputType() {
        return inputType;
    }

    public Class<O> outputType() {
        return outputType;
    }

    public String instructions() {
        return instructions;
    }

    public List<Field> inputFields() {
        return inputFields;
    }

    public List<Field> outputFields() {
        return outputFields;
    }

    private static <T> Class<T> requireRecord(Class<T> type, String role) {
        Objects.requireNonNull(type, role + "Type");
        if (!type.isRecord()) {
            throw new IllegalArgumentException(
                    "The " + role + " type must be a Java record, got " + type.getName());
        }
        return type;
    }

    private static List<Field> fieldsOf(Class<?> recordType) {
        return Arrays.stream(recordType.getRecordComponents())
                .map(Signature::toField)
                .toList();
    }

    private static Field toField(RecordComponent component) {
        Desc desc = component.getAnnotation(Desc.class);
        return new Field(component.getName(), desc == null ? "" : desc.value(), component.getType());
    }

    private static String defaultInstructions(List<Field> inputs, List<Field> outputs) {
        return "Given the fields " + names(inputs) + ", produce the fields " + names(outputs) + ".";
    }

    private static String names(List<Field> fields) {
        return fields.stream().map(f -> "`" + f.name() + "`").collect(Collectors.joining(", "));
    }
}
