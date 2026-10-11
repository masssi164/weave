# Contributing to Weave

## Licence and sign-off

Unless an existing file notice or [Third-party notices](THIRD_PARTY_NOTICES.md) specifies another licence, contributions to Weave are submitted under **EUPL-1.2-or-later**. Contributors retain their copyright; there is no copyright assignment or contributor licence agreement.

Use the [Developer Certificate of Origin 1.1](https://developercertificate.org/) and add a sign-off to each contribution commit with `git commit -s`:

```text
Signed-off-by: Your Name <your-email@example.com>
```

A sign-off certifies the statements in DCO 1.1. Sign only when you can make those statements, including the right to submit the work under the applicable licence. Do not sign for another contributor.

The vendored Matrix SDK crypto code and the upstream-oriented patch series follow the exceptions in [Third-party notices](THIRD_PARTY_NOTICES.md). Preserve existing notices and upstream contribution compatibility.

## Third-party and AI-assisted contributions

Identify the source and licence of substantially incorporated third-party material. Do not copy code, documentation, media, or data without the necessary rights.

AI-assisted development, research, documentation, and communication are welcome. Disclose material assistance in the pull request. The human contributor remains responsible for understanding the contribution, checking correctness and licensing, and supplying reproducible validation. Model output is not evidence of authorship or licence provenance. Do not include secrets or private conversations in provenance records.

## Development workflow

Start from the current integration lane described in [Core development workflow](docs/development/core-workflow.md). For a substantial change, open or reuse an issue and state the intended scope and acceptance criteria before implementation.

Follow [the developer handbook](docs/developer-handbook.md), [PR workflow](docs/gitflow-pr-workflow.md), and the relevant scoped repository instructions. Keep changes focused, run the smallest applicable checks, and record their actual results in the pull request.
