package io.prompttuner;

import io.prompttuner.eval.EvaluationResult;
import io.prompttuner.eval.Evaluator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PredictTest {

    public record Question(@Desc("a trivia question") String question) {}

    public record Answer(@Desc("short factual answer") String answer, int confidence) {}

    @Test
    void promptListsFieldsDemosAndJsonInstruction() {
        Predict<Question, Answer> qa = Predict.of(Question.class, Answer.class)
                .withDemos(List.of(Example.of(new Question("2+2?"), new Answer("4", 9))));

        List<Message> messages = qa.preview(new Question("Capital of France?"));

        assertEquals(4, messages.size());
        String system = messages.get(0).content();
        assertTrue(system.contains("Given the fields `question`, produce the fields `answer`, `confidence`."));
        assertTrue(system.contains("- question (String): a trivia question"));
        assertTrue(system.contains("- confidence (int)"));
        assertTrue(system.contains("\"answer\", \"confidence\""));
        assertEquals("{\"answer\":\"4\",\"confidence\":9}", messages.get(2).content());
        assertEquals("{\"question\":\"Capital of France?\"}", messages.get(3).content());
    }

    @Test
    void chainOfThoughtSeparatesReasoningFromOutput() {
        LanguageModel lm = m -> "Sure! {\"reasoning\": \"France's capital is Paris.\", \"answer\": \"Paris\", \"confidence\": 8}";
        Predict<Question, Answer> qa = Predict.chainOfThought(Question.class, Answer.class).withLm(lm);

        Prediction<Answer> p = qa.predict(new Question("Capital of France?"));

        assertEquals(new Answer("Paris", 8), p.output());
        assertEquals("France's capital is Paris.", p.reasoning());
        assertTrue(qa.preview(new Question("x")).get(0).content().contains("\"reasoning\", \"answer\""));
    }

    @Test
    void plainPredictIgnoresStrayReasoning() {
        Predict<Question, Answer> qa = Predict.of(Question.class, Answer.class)
                .withLm(m -> "{\"answer\": \"Paris\", \"confidence\": 7, \"extra\": true}");

        Prediction<Answer> p = qa.predict(new Question("Capital of France?"));

        assertEquals(new Answer("Paris", 7), p.output());
        assertNull(p.reasoning());
    }

    @Test
    void unusableRepliesFailClearly() {
        Predict<Question, Answer> noJson = Predict.of(Question.class, Answer.class).withLm(m -> "I don't know");
        Predict<Question, Answer> empty = Predict.of(Question.class, Answer.class).withLm(m -> "  ");
        Predict<Question, Answer> noLm = Predict.of(Question.class, Answer.class);

        assertThrows(PromptTunerException.class, () -> noJson.run(new Question("?")));
        assertThrows(PromptTunerException.class, () -> empty.run(new Question("?")));
        assertThrows(IllegalStateException.class, () -> noLm.run(new Question("?")));
    }

    @Test
    void saveAndLoadKeepInstructionsAndDemos(@TempDir Path dir) throws Exception {
        Predict<Question, Answer> tuned = Predict.chainOfThought(Question.class, Answer.class)
                .withInstructions("Answer trivia in one word.")
                .withDemos(List.of(Example.of(new Question("2+2?"), new Answer("4", 9))));
        Path file = dir.resolve("programs/qa.json");

        tuned.save(file);
        Predict<Question, Answer> loaded = Predict.load(file, Question.class, Answer.class);

        assertTrue(Files.readString(file).contains("prompttuner/v1"));
        assertEquals("Answer trivia in one word.", loaded.signature().instructions());
        assertEquals(tuned.demos(), loaded.demos());
        assertTrue(loaded.usesReasoning());
        assertEquals(tuned.demos(), Predict.load(Files.newInputStream(file), Question.class, Answer.class).demos());
        assertThrows(PromptTunerException.class, () -> Predict.load(file, Answer.class, Question.class));
    }

    @Test
    void evaluatorScoresFailuresAsZeroAndKeepsOrder() {
        LanguageModel flaky = m -> m.get(m.size() - 1).content().contains("\"b\"")
                ? "not json"
                : "{\"answer\":\"Paris\",\"confidence\":5}";
        Predict<Question, Answer> qa = Predict.of(Question.class, Answer.class).withLm(flaky);
        List<Example<Question, Answer>> examples = List.of(
                Example.of(new Question("a"), new Answer("Paris", 0)),
                Example.of(new Question("b"), new Answer("Paris", 0)));

        EvaluationResult<Question, Answer> result = Evaluator.of(Metric.<Question, Answer>exactMatch(Answer::answer))
                .parallelism(4)
                .evaluate(qa, examples);

        assertEquals(0.5, result.score(), 1e-9);
        assertEquals(1, result.failures());
        assertEquals("a", result.rows().get(0).example().input().question());
        assertNotNull(result.rows().get(1).error());
    }

    @Test
    void cacheSkipsRepeatedCalls() {
        AtomicInteger calls = new AtomicInteger();
        CachingLanguageModel cached = new CachingLanguageModel(m -> {
            calls.incrementAndGet();
            return "{\"answer\":\"Paris\",\"confidence\":5}";
        });
        Predict<Question, Answer> qa = Predict.of(Question.class, Answer.class).withLm(cached);

        qa.run(new Question("Capital of France?"));
        qa.run(new Question("Capital of France?"));
        qa.run(new Question("Capital of Spain?"));

        assertEquals(2, calls.get());
        assertEquals(1, cached.hits());
    }

    @Test
    void datasetsLoadJsonlAndSplit(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("qa.jsonl");
        Files.writeString(file, String.join("\n",
                "{\"input\":{\"question\":\"q1\"},\"output\":{\"answer\":\"a1\",\"confidence\":1}}",
                "",
                "{\"input\":{\"question\":\"q2\"},\"output\":{\"answer\":\"a2\",\"confidence\":2}}",
                "{\"input\":{\"question\":\"q3\"},\"output\":{\"answer\":\"a3\",\"confidence\":3}}",
                "{\"input\":{\"question\":\"q4\"},\"output\":{\"answer\":\"a4\",\"confidence\":4}}"));

        List<Example<Question, Answer>> all = Datasets.fromJsonl(file, Question.class, Answer.class);
        Datasets.Split<Question, Answer> split = Datasets.split(all, 0.75, 1L);

        assertEquals(all, Datasets.fromJsonl(Files.newInputStream(file), Question.class, Answer.class));
        assertEquals(4, all.size());
        assertEquals(new Answer("a2", 2), all.get(1).output());
        assertEquals(3, split.train().size());
        assertEquals(1, split.dev().size());
    }

    @Test
    void signatureRequiresRecords() {
        assertThrows(IllegalArgumentException.class, () -> Signature.of(String.class, Answer.class));
    }
}
