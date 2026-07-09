# Rust Matrix Core Integration Instructions

`integrations/rust_matrix_core` owns the Flutter boundary to the shared Rust Matrix core.

Own here:
- app-facing descriptors for the Rust/Ruma Matrix protocol core
- the future `flutter_rust_bridge` generated binding handoff
- support-safe bridge readiness states that features can consume without importing provider SDKs

Do not own here:
- Weave Chat presentation state
- raw Matrix provider or homeserver credentials
- direct Synapse/MAS/provider setup flows
- generated bridge code until the native build is wired into the Flutter target

Boundary rules:
- Flutter continues to authenticate through the Weave OIDC session before calling the Weave Matrix facade.
- The shared Rust core is a protocol helper, not a provider SDK escape hatch.
- The legacy Dart Matrix SDK seam remains fenced until it can be retired without breaking E2EE and device-state behavior.

## Global Weave agent baseline

- Write agent instructions, PRs, issues, code comments, and documentation in English unless an explicit localization file requires another language.
- Follow `docs/developer-handbook.md`, `docs/gitflow-pr-workflow.md`, `docs/weave-operating-model.md`, and relevant domain docs before coding, opening PRs, merging, or declaring work complete.
- If the user asks to finish a sprint/milestone, derive acceptance from GitHub issues/milestones, repo specs/tasks, docs, CI policy, and evidence; do not require the user to restate issue acceptance criteria.
- Use protected `main`, short-lived branches, exactly one `release-notes-*` label per PR, smallest meaningful local gates, green CI, fallback review evidence, and GitHub closure verification before reporting completion.
