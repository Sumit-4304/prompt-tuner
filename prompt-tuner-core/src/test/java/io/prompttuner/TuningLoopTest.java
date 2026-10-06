package io.prompttuner;

import com.fasterxml.jackson.databind.JsonNode;
import io.prompttuner.eval.EvaluationResult;
import io.prompttuner.eval.Evaluator;
import io.prompttuner.internal.Json;
import io.prompttuner.optimize.BootstrapFewShot;
import io.prompttuner.optimize.OptimizationResult;
import io.prompttuner.optimize.RandomSearchFewShot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End to end with a fake model that only classifies well once it has seen at least two demos.
 * Proves the loop: score the plain prompt, tune, score again, and get a better program.
 */
class TuningLoopTest {

    public record Review(String text) {}

    public record Sentiment(@Desc("positive, negative or neutral") String label) {}

    /** Answers "neutral" until the prompt carries 2+ demos, then classifies by keywords. */
    static final LanguageModel FAKE_MODEL = messages -> {
        long demos = messages.stream().filter(m -> m.role() == Message.Role.ASSISTANT).count();
        String text = lastUserText(messages);
        String label = "neutral";
        if (demos >= 2) {
            if (text.contains("love") || text.contains("great")) {
                label = "positive";
            } else if (text.contains("hate") || text.contains("bad")) {
                label = "negative";
            }
        }
        return "```json\n{\"label\": \"" + label + "\"}\n```";
    };

    static final List<Example<Review, Sentiment>> DATA = List.of(
            ex("I love this phone", "positive"),
            ex("great battery life", "positive"),
            ex("love the camera", "positive"),
            ex("a great purchase", "positive"),
            ex("I hate the screen", "negative"),
            ex("bad customer support", "negative"),
            ex("hate the charger", "negative"),
            ex("really bad speakers", "negative"),
            ex("it arrived on Tuesday", "neutral"),
            ex("the box is blue", "neutral"));

    static final Metric<Review, Sentiment> METRIC = Metric.exactMatch(Sentiment::label);

    @Test
    void plainPromptScoresLow() {
        Predict<Review, Sentiment> plain = Predict.of(Review.class, Sentiment.class).withLm(FAKE_MODEL);

        EvaluationResult<Review, Sentiment> result = Evaluator.of(METRIC).evaluate(plain, DATA);

        assertEquals(0.2, result.score(), 1e-9);
        assertEquals(0, result.failures());
    }

    @Test
    void bootstrapKeepsOnlyAnswersThatPassTheMetric() {
        Predict<Review, Sentiment> plain = Predict.of(Review.class, Sentiment.class).withLm(FAKE_MODEL);

        OptimizationResult<Review, Sentiment> result = BootstrapFewShot.with(METRIC)
                .maxBootstrapped(4).maxLabeled(2).build()
                .optimize(plain, DATA);

        // The untuned model only gets the two neutral reviews right, so only those become bootstrapped demos.
        List<Example<Review, Sentiment>> demos = result.program().demos();
        assertEquals(4, demos.size());
        assertEquals("neutral", demos.get(0).output().label());
        assertEquals("neutral", demos.get(1).output().label());
    }

    @Test
    void randomSearchFindsABetterProgram() {
        Predict<Review, Sentiment> plain = Predict.of(Review.class, Sentiment.class)
                .withInstructions("Classify the sentiment of the review.")
                .withLm(FAKE_MODEL);

        OptimizationResult<Review, Sentiment> result = RandomSearchFewShot.with(METRIC)
                .candidates(3).seed(42).build()
                .optimize(plain, DATA);

        assertTrue(result.score() >= 0.9, "expected a tuned score of at least 0.9, got " + result.score());
        assertTrue(result.program().demos().size() >= 2);
        assertEquals("Classify the sentiment of the review.", result.program().signature().instructions());
        assertTrue(result.log().stream().anyMatch(line -> line.startsWith("best score=")));
    }

    private static Example<Review, Sentiment> ex(String text, String label) {
        return Example.of(new Review(text), new Sentiment(label));
    }

    private static String lastUserText(List<Message> messages) {
        Message last = messages.get(messages.size() - 1);
        try {
            JsonNode node = Json.MAPPER.readTree(last.content());
            return node.path("text").asText().toLowerCase();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
