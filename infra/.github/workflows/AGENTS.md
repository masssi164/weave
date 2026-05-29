# Workflows Guide

This directory contains GitHub Actions workflows for repository validation.

## Files

- `ci.yml`: runs Terraform formatting and validation checks plus shell linting on pushes and pull requests. Full-stack smoke is manual-only through `workflow_dispatch` with an explicit power/storage confirmation gate.

## Maintenance Notes

- Keep the validation job focused on deterministic repository checks that can run without local Docker state.
- Prefer validating both Terraform stages with `init -backend=false` before heavier integration steps.
- The smoke job is allowed to use Docker when manually dispatched to validate release-critical stack contracts end to end. Do not make it a normal PR/push requirement unless the cross-repo CI/E2E spec changes.

## Global Weave agent baseline

- Write agent instructions, PRs, issues, code comments, and documentation in English unless an explicit localization file requires another language.
- Follow `docs/developer-handbook.md`, `docs/gitflow-pr-workflow.md`, `docs/weave-operating-model.md`, and relevant domain docs before coding, opening PRs, merging, or declaring work complete.
- If the user asks to finish a sprint/milestone, derive acceptance from GitHub issues/milestones, repo specs/tasks, docs, CI policy, and evidence; do not require the user to restate issue acceptance criteria.
- Use protected `main`, short-lived branches, exactly one `release-notes-*` label per PR, smallest meaningful local gates, green CI, fallback review evidence, and GitHub closure verification before reporting completion.
