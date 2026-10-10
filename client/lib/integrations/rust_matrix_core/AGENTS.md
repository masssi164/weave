# Rust Matrix Core Integration Instructions

`integrations/rust_matrix_core` owns the Flutter boundary to the shared Rust Matrix core.

Own here:
- app-facing descriptors for the Rust/Ruma Matrix protocol core
- the generated `flutter_rust_bridge` binding handoff to Matrix SDK 0.18
- support-safe bridge readiness states that features can consume without importing provider SDKs

Do not own here:
- Weave Chat presentation state
- server-side Synapse/MAS/provider setup flows
- persistence or independent refresh of the Weave member bearer outside auth

Boundary rules:
- Flutter uses the normal Weave member OIDC/PKCE session at the Weave Matrix facade after one sign-in. Auth owns bearer refresh and persistence; the Rust SDK receives only the current authorized bearer in memory.
- The Matrix SDK connects to the Weave Matrix Client-Server northbound endpoint from OrgManifest v2, never directly to the selected southbound Chat provider.
- Current Weave member, organization and Chat capability checks gate Matrix session creation and recovery; an uncertain or revoked grant fails closed without deleting the client E2EE store.
- The installed Matrix device identity and encrypted Rust crypto store survive member token refresh and app restart. A failed or revoked member grant drops live Matrix access without deleting recovery material.
- Distinct Matrix OAuth clients, dynamic registration and independent third-party client authentication are deferred from the current release profile.
- The native Rust Matrix SDK owns Matrix transport, sync and E2EE. The legacy Dart Matrix SDK seam remains retired.

## Global Weave agent baseline

- Write agent instructions, PRs, issues, code comments, and documentation in English unless an explicit localization file requires another language.
- Follow `docs/developer-handbook.md`, `docs/gitflow-pr-workflow.md`, `docs/weave-operating-model.md`, and relevant domain docs before coding, opening PRs, merging, or declaring work complete.
- If the user asks to finish a sprint/milestone, derive acceptance from GitHub issues/milestones, repo specs/tasks, docs, CI policy, and evidence; do not require the user to restate issue acceptance criteria.
- Use protected `main`, short-lived branches, exactly one `release-notes-*` label per PR, smallest meaningful local gates, green CI, fallback review evidence, and GitHub closure verification before reporting completion.
