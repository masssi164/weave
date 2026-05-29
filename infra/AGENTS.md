# Repository Guide

This repository contains one runnable workspace under `weave-workspace/`.

## Files

- `.gitignore`: ignores OpenTofu state, generated runtime assets, and local work directories.
- `Makefile`: local operator helpers such as printing default host entries.
- `README.md`: operator-focused overview, local bootstrap instructions, and single-host operator path deployment summary.
- `KEYCLOAK_CONTRACT.md`: local realm, client, scope, claim, and audience contract.
- `docs/single-host-operator-guide.md`: non-local single-host operator path target, required inputs, and operator runbook notes.
- `docs/matrix-default-workspace.md`: Matrix default workspace aliases, access policy, and verification notes.
- `.github/AGENTS.md`: GitHub automation and workflow navigation notes.
- `weave-workspace/.env.example`: local hostname, port, and Caddy mount defaults.
- `weave-workspace/release.env.example`: single-host env template with explicit production-facing placeholders.
- `weave-workspace/release-verify.sh`: public endpoint verification script for release operators.
- `weave-workspace/backup.sh`: manually runnable backup helper for Postgres dumps, required data volumes, and generated config/secrets.
- `weave-workspace/restore-smoke.sh`: non-destructive post-restore/reprovisioning smoke wrapper around the recovery readiness checks.
- `weave-workspace/provision-matrix-default-workspace.sh`: idempotent default Matrix space/room provisioner invoked by install.
- `weave-workspace/docker-compose.yml`: Caddy service definition for proxy-only iteration against the OpenTofu-created network.
- `weave-workspace/AGENTS.md`: workspace-level navigation guide.

## Working Model

- Treat `01-infrastructure` and `02-keycloak-setup` as separate OpenTofu states.
- Keep generated runtime artifacts inside each stage’s `.generated/` directory.
- Prefer extending existing child modules before adding more logic to a root `main.tf`.
- Keep PostgreSQL changes at the shared-instance level in `01-infrastructure`; service isolation is handled with one database per service, not with cross-service schema juggling.

## Monorepo / v0.1 release rules

- This directory is now `infra/` inside the Weave monorepo; do not assume a separate `weave-infra` checkout.
- OpenTofu is preferred. Operator scripts should use `${WEAVE_IAC_BIN:-tofu}` so emergency Terraform-compatible fallback remains explicit.
- Any destructive operation must require typed confirmation and must preserve backup/rollback guidance.
- Keep support bundles redacted and deterministic; never leak secrets, tokens, cookies, raw provider errors, generated private keys, room IDs, event IDs, filenames, usernames, or display names.
- Map release-critical infra behavior to `../e2e/scenario_mappings.json` when it affects product acceptance.

## Global Weave agent baseline

- Write agent instructions, PRs, issues, code comments, and documentation in English unless an explicit localization file requires another language.
- Follow `docs/developer-handbook.md`, `docs/gitflow-pr-workflow.md`, `docs/weave-operating-model.md`, and relevant domain docs before coding, opening PRs, merging, or declaring work complete.
- If the user asks to finish a sprint/milestone, derive acceptance from GitHub issues/milestones, repo specs/tasks, docs, CI policy, and evidence; do not require the user to restate issue acceptance criteria.
- Use protected `main`, short-lived branches, exactly one `release-notes-*` label per PR, smallest meaningful local gates, green CI, fallback review evidence, and GitHub closure verification before reporting completion.
