# Integrations Instructions

`lib/integrations/` hosts reusable external-service boundaries that are shared across multiple features.

Rules:
- place cross-feature auth, session, capability, and shared protocol orchestration here instead of inside a feature
- keep the same clean-architecture split inside each integration: `presentation/`, `domain/`, and `data/`
- expose reusable contracts/providers that features can consume without importing another feature's internals
- do not let integrations depend on feature presentation code or feature-owned domain models they are meant to serve

Ownership guidance:
- keep feature-specific mapping and user-facing state inside the owning feature
- keep server/session/account lifecycle rules in the integration when those rules are not feature-specific
- if a new integration becomes large enough to guide contributors, add an `AGENTS.md` inside that integration subtree

## Global Weave agent baseline

- Write agent instructions, PRs, issues, code comments, and documentation in English unless an explicit localization file requires another language.
- Follow `docs/developer-handbook.md`, `docs/gitflow-pr-workflow.md`, `docs/weave-operating-model.md`, and relevant domain docs before coding, opening PRs, merging, or declaring work complete.
- If the user asks to finish a sprint/milestone, derive acceptance from GitHub issues/milestones, repo specs/tasks, docs, CI policy, and evidence; do not require the user to restate issue acceptance criteria.
- Use protected `main`, short-lived branches, exactly one `release-notes-*` label per PR, smallest meaningful local gates, green CI, fallback review evidence, and GitHub closure verification before reporting completion.
