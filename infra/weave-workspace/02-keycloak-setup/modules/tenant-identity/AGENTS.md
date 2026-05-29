# Tenant Identity Module Guide

This module owns tenant-specific Keycloak configuration after the server is already running.

## Files

- `main.tf`: tenant realm, optional integration test user, OIDC client definitions, Weave workspace scope and audience mapper, and the Nextcloud group membership mapper.
- `variables.tf`: tenant slug, public URLs, shared secret inputs, and optional test user flag.
- `outputs.tf`: realm, client, and optional test user outputs returned to the stage root.

## Global Weave agent baseline

- Write agent instructions, PRs, issues, code comments, and documentation in English unless an explicit localization file requires another language.
- Follow `docs/developer-handbook.md`, `docs/gitflow-pr-workflow.md`, `docs/weave-operating-model.md`, and relevant domain docs before coding, opening PRs, merging, or declaring work complete.
- If the user asks to finish a sprint/milestone, derive acceptance from GitHub issues/milestones, repo specs/tasks, docs, CI policy, and evidence; do not require the user to restate issue acceptance criteria.
- Use protected `main`, short-lived branches, exactly one `release-notes-*` label per PR, smallest meaningful local gates, green CI, fallback review evidence, and GitHub closure verification before reporting completion.
