# Security Policy

## Supported versions

PromptTuner is in early development. Security fixes go into the latest release only.

| Version | Supported |
| --- | --- |
| 0.1.x | Yes |

## Reporting a vulnerability

**Please do not open a public issue for security problems.**

Report privately through GitHub:
**Security** tab → **Report a vulnerability**
(<https://github.com/Sumit-4304/prompt-tuner/security/advisories/new>).

Include what you found, how to reproduce it, and the impact you expect. You will get a first
reply within 7 days. Once a fix is released, we will credit you in the advisory unless you
prefer to stay anonymous.

## Things to know when using PromptTuner

- **API keys.** PromptTuner never stores or logs API keys. Pass them through environment
  variables or your secret manager, never in source code or in saved program files.
- **Saved programs contain your examples.** A tuned program file (`Predict.save`) includes the
  demo inputs and outputs it picked from your training data. Do not tune on personal or
  confidential data unless the saved file is protected like that data.
- **Model replies are untrusted input.** Replies are parsed into your output records with
  Jackson. Validate the values before acting on them, as you would any user input.
- **Prompt injection.** Text inside your inputs reaches the model. Do not give a tuned program
  authority to take actions based only on model output.
