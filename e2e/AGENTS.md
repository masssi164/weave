# E2E / Acceptance Agent Rules

Gherkin in this directory is the Weave product contract.

- Keep scenarios readable for product, engineering, and screen-reader users.
- Describe user-visible Weave behavior, not implementation details, HTTP routes, selectors, JWTs, or provider internals.
- Every scenario in `features/` must have a stable tag and a mapping in `scenario_mappings.json`.
- Every mapping must point to checked-in executable evidence and evidence markers.
- Do not add aspirational scenarios without executable mappings; use roadmap docs or issues for future intent.
- Live-stack evidence must be sparse, sanitized, and deterministic.
- Never write tokens, cookies, private keys, raw provider errors, room IDs, event IDs, filenames, usernames, or display names into evidence artifacts.

## Global Weave agent baseline

- Write agent instructions, PRs, issues, code comments, and documentation in English unless an explicit localization file requires another language.
- Follow `docs/developer-handbook.md`, `docs/gitflow-pr-workflow.md`, `docs/weave-operating-model.md`, and relevant domain docs before coding, opening PRs, merging, or declaring work complete.
- If the user asks to finish a sprint/milestone, derive acceptance from GitHub issues/milestones, repo specs/tasks, docs, CI policy, and evidence; do not require the user to restate issue acceptance criteria.
- Use protected `main`, short-lived branches, exactly one `release-notes-*` label per PR, smallest meaningful local gates, green CI, fallback review evidence, and GitHub closure verification before reporting completion.
