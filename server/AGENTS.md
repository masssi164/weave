# Weave Server Agent Rules

The server owns Weave product facades, authorization, audit, provider boundaries, and support-safe errors.

- Keep provider access behind server-side facades. The client must never call raw providers for release behavior.
- Before adding a provider write, add permission checks, audit events, failure semantics, and contract tests.
- User writes for v0.1 are allowed only when they are explicit, attributable, authorized, and auditable.
- Agent/team writes remain out of v0.1 until the sandboxing/tool-whitelist ADR is accepted.
- Do not leak provider secrets, service tokens, raw URLs with credentials, cookies, raw downstream errors, room IDs, event IDs, filenames, usernames, or display names in support-safe responses.
- Context/Space authorization is the gate for workspace-scoped provider data.
- Prefer Cucumber/JUnit and facade contract tests for product behavior; map release-grade behavior back to `../e2e/scenario_mappings.json`.

Validation from `server/`:

- `./gradlew test`
