# Backend Module Guide

This module owns the local Weave backend runtime consumed by the Caddy product gateway.

## Files

- `main.tf`: Weave backend image, container, healthcheck, OIDC environment, and Docker network aliases.
- `variables.tf`: image, port, hostname, and OIDC contract inputs.
- `outputs.tf`: exported backend container identifier.

## Global Weave agent baseline

- Write agent instructions, PRs, issues, code comments, and documentation in English unless an explicit localization file requires another language.
- Follow `docs/developer-handbook.md`, `docs/gitflow-pr-workflow.md`, `docs/weave-operating-model.md`, and relevant domain docs before coding, opening PRs, merging, or declaring work complete.
- If the user asks to finish a sprint/milestone, derive acceptance from GitHub issues/milestones, repo specs/tasks, docs, CI policy, and evidence; do not require the user to restate issue acceptance criteria.
- Use protected `main`, short-lived branches, exactly one `release-notes-*` label per PR, smallest meaningful local gates, green CI, fallback review evidence, and GitHub closure verification before reporting completion.
