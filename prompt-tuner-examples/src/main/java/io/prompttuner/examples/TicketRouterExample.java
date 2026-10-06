package io.prompttuner.examples;

import io.prompttuner.CachingLanguageModel;
import io.prompttuner.Datasets;
import io.prompttuner.Desc;
import io.prompttuner.Example;
import io.prompttuner.LanguageModel;
import io.prompttuner.Metric;
import io.prompttuner.Predict;
import io.prompttuner.eval.EvaluationResult;
import io.prompttuner.eval.Evaluator;
import io.prompttuner.optimize.OptimizationResult;
import io.prompttuner.optimize.RandomSearchFewShot;
import io.prompttuner.springai.SpringAiLanguageModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

/**
 * Tunes a support-ticket router for "LedgerLite", a made-up invoicing app, on a real model.
 *
 * <p>The team has house rules the model cannot guess: a login problem is ACCOUNT even when it
 * looks like an error, plan limits are BILLING, a bug is only HIGH when data is lost, and how-to
 * questions and feature requests are always LOW. The plain prompt does not know these rules;
 * tuning teaches them through demos picked and checked against the metric.
 *
 * <p>Works with any OpenAI-compatible endpoint. Set:
 * <ul>
 *   <li>{@code PT_API_KEY} (or {@code OPENAI_API_KEY})</li>
 *   <li>{@code PT_BASE_URL}, default {@code https://api.openai.com}. Ollama: {@code http://localhost:11434}</li>
 *   <li>{@code PT_MODEL}, default {@code gpt-4o-mini}</li>
 * </ul>
 */
public final class TicketRouterExample {

    public record Ticket(@Desc("the customer's message") String message) {}

    public record Route(
            @Desc("one of BILLING, ACCOUNT, BUG, HOW_TO, FEATURE_REQUEST") String category,
            @Desc("one of HIGH, MEDIUM, LOW") String priority) {}

    /** Getting the team right matters most, so category is worth 0.7 and priority 0.3. */
    static final Metric<Ticket, Route> ROUTING_SCORE = (example, prediction) -> {
        if (prediction == null) {
            return 0.0;
        }
        double score = 0.0;
        if (same(example.output().category(), prediction.category())) {
            score += 0.7;
        }
        if (same(example.output().priority(), prediction.priority())) {
            score += 0.3;
        }
        return score;
    };

    public static void main(String[] args) {
        String baseUrl = env("PT_BASE_URL", "https://api.openai.com");
        String model = env("PT_MODEL", "gpt-4o-mini");
        String apiKey = env("PT_API_KEY", System.getenv("OPENAI_API_KEY"));
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("Set PT_API_KEY (or OPENAI_API_KEY). For Ollama any value works, e.g. PT_API_KEY=ollama.");
            System.exit(1);
        }

        CachingLanguageModel lm = new CachingLanguageModel(openAiCompatible(baseUrl, apiKey, model));

        // 48 labeled tickets: 16 to learn from, 16 to pick the best candidate, 16 never seen during tuning
        List<Example<Ticket, Route>> all = Datasets.fromJsonl(resource("tickets.jsonl"), Ticket.class, Route.class);
        Datasets.Split<Ticket, Route> first = Datasets.split(all, 1.0 / 3, 7);
        Datasets.Split<Ticket, Route> rest = Datasets.split(first.dev(), 0.5, 7);
        List<Example<Ticket, Route>> train = first.train();
        List<Example<Ticket, Route>> val = rest.train();
        List<Example<Ticket, Route>> test = rest.dev();

        System.out.printf("Model %s at %s%n", model, baseUrl);
        System.out.printf("Examples: %d train, %d validation, %d test%n%n", train.size(), val.size(), test.size());

        Predict<Ticket, Route> plain = Predict.of(Ticket.class, Route.class)
                .withInstructions("Route this customer support ticket for LedgerLite, an invoicing app.")
                .withLm(lm);
        Evaluator<Ticket, Route> evaluator = Evaluator.of(ROUTING_SCORE).parallelism(4);

        EvaluationResult<Ticket, Route> before = evaluator.evaluate(plain, test);
        System.out.println("BEFORE tuning (test set): " + before);
        printMistakes(before);

        System.out.println("\nTuning...");
        OptimizationResult<Ticket, Route> tuned = RandomSearchFewShot.with(ROUTING_SCORE)
                .valset(val)
                .candidates(4)
                .maxBootstrapped(4)
                .maxLabeled(4)
                .parallelism(4)
                .seed(7)
                .build()
                .optimize(plain, train);
        tuned.log().forEach(line -> System.out.println("  " + line));

        EvaluationResult<Ticket, Route> after = evaluator.evaluate(tuned.program(), test);
        System.out.println("\nAFTER tuning (test set):  " + after);
        printMistakes(after);

        Path saved = Path.of("target", "tuned", "ticket-router.json");
        tuned.program().save(saved);

        System.out.printf("%nTest score %.2f -> %.2f%n", before.score(), after.score());
        System.out.printf("Model calls made: %d (cache saved %d)%n", lm.misses(), lm.hits());
        System.out.println("Tuned program saved to " + saved.toAbsolutePath());

        Route sample = tuned.program().run(new Ticket("Money was taken from my account twice for one invoice payment."));
        System.out.println("Sample: 'Money was taken from my account twice...' -> " + sample);
    }

    static LanguageModel openAiCompatible(String baseUrl, String apiKey, String model) {
        OpenAiApi api = OpenAiApi.builder().baseUrl(baseUrl).apiKey(apiKey).build();
        OpenAiChatOptions options = OpenAiChatOptions.builder().model(model).temperature(0.0).build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder().openAiApi(api).defaultOptions(options).build();
        return SpringAiLanguageModel.of(chatModel);
    }

    private static void printMistakes(EvaluationResult<Ticket, Route> result) {
        result.rows().stream()
                .filter(row -> row.score() < 1.0)
                .limit(5)
                .forEach(row -> System.out.printf("  - \"%s\"%n      expected %s/%s, got %s%n",
                        row.example().input().message(),
                        row.example().output().category(), row.example().output().priority(),
                        row.error() != null ? "error: " + row.error().getMessage()
                                : row.prediction().category() + "/" + row.prediction().priority()));
    }

    private static boolean same(String expected, String actual) {
        return actual != null && expected.trim().equalsIgnoreCase(actual.trim());
    }

    private static InputStream resource(String name) {
        InputStream in = TicketRouterExample.class.getClassLoader().getResourceAsStream(name);
        if (in == null) {
            throw new IllegalStateException("Missing resource " + name);
        }
        return in;
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
