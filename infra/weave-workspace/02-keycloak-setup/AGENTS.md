# Keycloak Setup Stage Guide

This stage owns tenant-level identity configuration after Keycloak is already running.

## Files

- `main.tf`: provider configuration, derived URLs, child module call, and `moved` blocks.
- `variables.tf`: public input contract for the Keycloak setup stage, including the optional local integration test user flag.
- `outputs.tf`: realm, client, scope, audience, and optional test user outputs consumed by operators and `install.sh`.
- `.terraform.lock.hcl`: pinned provider selections for reproducible init behavior.
- `modules/AGENTS.md`: map of the child module used by this stage.

## Responsibility Boundary

- This stage configures Keycloak only.
- It does not mutate Docker resources or rerender infrastructure assets.
- Nextcloud app bootstrap remains in `install.sh`, using outputs from this stage.

## Global Weave agent baseline

- Write agent instructions, PRs, issues, code comments, and documentation in English unless an explicit localization file requires another language.
- Follow `docs/developer-handbook.md`, `docs/gitflow-pr-workflow.md`, `docs/weave-operating-model.md`, and relevant domain docs before coding, opening PRs, merging, or declaring work complete.
- If the user asks to finish a sprint/milestone, derive acceptance from GitHub issues/milestones, repo specs/tasks, docs, CI policy, and evidence; do not require the user to restate issue acceptance criteria.
- Use protected `main`, short-lived branches, exactly one `release-notes-*` label per PR, smallest meaningful local gates, green CI, fallback review evidence, and GitHub closure verification before reporting completion.
