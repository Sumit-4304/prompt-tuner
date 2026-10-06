# PromptTuner

[![CI](https://github.com/Sumit-4304/prompt-tuner/actions/workflows/ci.yml/badge.svg)](https://github.com/Sumit-4304/prompt-tuner/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

**Stop guessing prompts. Measure them and let the library find a better one.**

PromptTuner is a Java library that tunes the prompts behind your AI features automatically.
You describe what goes in and what comes out, give it some examples and a scoring rule, and it
tries different prompt setups, scores each one on your examples, and keeps the best.
The result is a prompt that is *measured* to work, saved as a file your app loads.

It brings the core idea of [DSPy](https://github.com/stanfordnlp/dspy) (Python) to Java and Spring.

## Why

Most AI features run on hand-written prompts. That means:

- **Guesswork**: nobody knows if prompt version 7 is better than version 5.
- **No measurement**: it works on the three cases you tried, and fails on others.
- **Breaks on model change**: a new or cheaper model needs the prompt re-tuned by hand.

With PromptTuner, "good" is defined by your examples and your metric, and every tuned program
comes with a score.

## Quick start

```java
// 1. Say what goes in and what comes out (plain Java records)
record Review(String text) {}
record Sentiment(@Desc("positive, negative or neutral") String label) {}

// 2. Connect a model (any Spring AI ChatModel: OpenAI, Bedrock, Anthropic, Ollama, ...)
LanguageModel lm = new CachingLanguageModel(
        SpringAiLanguageModel.of(chatModel, ChatOptions.builder().temperature(0.0).build()));

Predict<Review, Sentiment> classify = Predict.of(Review.class, Sentiment.class)
        .withInstructions("Classify the sentiment of the review.")
        .withLm(lm);

// 3. Give examples and a scoring rule
List<Example<Review, Sentiment>> data = Datasets.fromJsonl(Path.of("reviews.jsonl"), Review.class, Sentiment.class);
Datasets.Split<Review, Sentiment> split = Datasets.split(data, 0.7, 42);
Metric<Review, Sentiment> metric = Metric.exactMatch(Sentiment::label);

// 4. Measure the starting point, tune, and measure again
double before = Evaluator.of(metric).evaluate(classify, split.dev()).score();

OptimizationResult<Review, Sentiment> tuned = RandomSearchFewShot.with(metric)
        .valset(split.dev())
        .build()
        .optimize(classify, split.train());

System.out.printf("before %.2f -> after %.2f%n", before, tuned.score());
tuned.program().save(Path.of("src/main/resources/prompts/sentiment.json"));

// 5. In production: load the tuned program, no re-tuning
Predict<Review, Sentiment> prod = Predict.load(Path.of("prompts/sentiment.json"), Review.class, Sentiment.class)
        .withLm(lm);
Sentiment s = prod.run(new Review("Battery life is great"));
```

`reviews.jsonl` holds one example per line:

```json
{"input": {"text": "I love this phone"}, "output": {"label": "positive"}}
```

## Try it on a real model

`prompt-tuner-examples` tunes a support-ticket router for a made-up invoicing app, using 48
labeled tickets (16 train, 16 validation, 16 test). The team's house rules (login problems are
ACCOUNT, bugs are HIGH only when data is lost, how-to and feature requests are always LOW) are not
in the prompt; tuning has to teach them.

It works with any OpenAI-compatible endpoint:

| Provider | `PT_BASE_URL` | `PT_MODEL` | `PT_API_KEY` |
| --- | --- | --- | --- |
| OpenAI (default) | `https://api.openai.com` | `gpt-4o-mini` | your key |
| Ollama (free, local) | `http://localhost:11434` | e.g. `llama3.1:8b` | any value |
| Any OpenAI-compatible API | its base URL | its model id | its key |

```bash
mvn -q install -DskipTests
PT_API_KEY=sk-... mvn -q -pl prompt-tuner-examples exec:java
```

It prints the test score before and after tuning, the mistakes, every candidate it tried, how
many model calls it made, and saves the tuned program to `target/tuned/ticket-router.json`.

## Building blocks

| Piece | What it is |
| --- | --- |
| `Signature` | The inputs and outputs of a step, taken from two records. `@Desc` explains a field to the model. |
| `Predict` | One model call. `Predict.chainOfThought(...)` makes the model reason before answering. |
| `Example` | An input with its correct output. |
| `Metric` | Scores a prediction from 0.0 to 1.0. This is where you define "good". |
| `Evaluator` | Runs a program on many examples in parallel and averages the score. |
| `LabeledFewShot` | Optimizer: picks some of your examples as demos. |
| `BootstrapFewShot` | Optimizer: lets the model answer your examples and keeps the answers that pass your metric as demos. |
| `RandomSearchFewShot` | Optimizer: builds several candidates, scores each one, and returns the best. **Start here.** |
| `CachingLanguageModel` | Skips repeated identical calls. Saves cost and makes tuning repeatable. |

## Requirements

- Java 17 or later. On Java 21+ model calls run on virtual threads automatically.
- `prompt-tuner-core` has one dependency (Jackson). `prompt-tuner-spring-ai` adds Spring AI 1.0.

## Modules

| Module | Purpose |
| --- | --- |
| `prompt-tuner-core` | Signatures, predictors, metrics, evaluation, optimizers |
| `prompt-tuner-spring-ai` | Use any Spring AI `ChatModel` as the model |

## Roadmap

- **0.1 (now)**: `Predict`, chain of thought, evaluation, labeled / bootstrap / random-search optimizers, save and load, Spring AI adapter
- **0.2**: instruction optimizer (the model proposes better instructions, a search picks the best), LangChain4j adapter, tools (ReAct), Spring Boot starter with Micrometer metrics
- **0.3**: reflective optimizer (GEPA-style), multi-step programs, HTML evaluation reports, persistent cache

## Build

```bash
mvn test
```

## Contributing and security

Contributions are welcome: see [CONTRIBUTING.md](CONTRIBUTING.md).
Found a security problem? Please report it privately as described in [SECURITY.md](SECURITY.md).

## License

[Apache License 2.0](LICENSE).

## Credits

The ideas behind PromptTuner (signatures, bootstrapped few-shot demos, metric-driven optimization)
come from [DSPy](https://github.com/stanfordnlp/dspy) by Stanford NLP. PromptTuner is an independent
Java implementation and is not affiliated with the DSPy project.
