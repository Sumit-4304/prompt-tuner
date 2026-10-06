# Contributing to PromptTuner

Thanks for helping! Bug reports, ideas, docs and code are all welcome.

## Before you start

- For anything bigger than a small fix, open an issue first so we can agree on the approach.
- Security problems: follow [SECURITY.md](SECURITY.md), not a public issue.

## Development

Requirements: Java 17+ and Maven 3.9+.

```bash
mvn verify
```

Tests must not call a real model. Use a fake `LanguageModel` (it is a one-method interface);
see `TuningLoopTest` for an example.

## Pull requests

1. Fork the repo and create a branch from `main`.
2. Keep the change focused, and add or update tests.
3. Make sure `mvn verify` passes on Java 17.
4. Never commit API keys, tokens or real customer data. Example data must be made up.
5. Describe what changed and why in the pull request.

By submitting a contribution you agree that it is licensed under the
[Apache License 2.0](LICENSE), the same license as the project.

## Code style

- Match the surrounding code: immutable objects, records for data, small public API.
- Public classes and methods get a short Javadoc saying what they are for.
- No new runtime dependencies in `prompt-tuner-core` without discussion.

## Conduct

Be respectful and constructive. We follow the
[Contributor Covenant](https://www.contributor-covenant.org/version/2/1/code_of_conduct/).
Report unacceptable behavior privately to the maintainer through GitHub.
