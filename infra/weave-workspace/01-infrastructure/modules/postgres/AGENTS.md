# Postgres Module Guide

This module owns the shared PostgreSQL runtime used by Keycloak, MAS, Synapse, and Nextcloud.

## Files

- `main.tf`: PostgreSQL image, persistent volume, and container lifecycle.
- `variables.tf`: image, network, bootstrap database, and administrator credential inputs.
- `outputs.tf`: exported container and volume identifiers for sibling modules.

## Maintenance Notes

- Per-service roles and databases are created by the root stage bootstrap in `01-infrastructure/main.tf`, not inside this child module.

## Global Weave agent baseline

- Write agent instructions, PRs, issues, code comments, and documentation in English unless an explicit localization file requires another language.
- Follow `docs/developer-handbook.md`, `docs/gitflow-pr-workflow.md`, `docs/weave-operating-model.md`, and relevant domain docs before coding, opening PRs, merging, or declaring work complete.
- If the user asks to finish a sprint/milestone, derive acceptance from GitHub issues/milestones, repo specs/tasks, docs, CI policy, and evidence; do not require the user to restate issue acceptance criteria.
- Use protected `main`, short-lived branches, exactly one `release-notes-*` label per PR, smallest meaningful local gates, green CI, fallback review evidence, and GitHub closure verification before reporting completion.
