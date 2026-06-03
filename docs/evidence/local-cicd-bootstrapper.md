# Local CI/CD bootstrapper proof

Status: Sprint 27 / #666 implementation slice.

## Scope

The local bootstrapper is a Go-built executable under `tools/weave-setup`. It runs before live Admin Console orchestration and supports two modes from the same Go core package:

- `weave-setup app` — accessible guided terminal app mode for local first-run setup. It is text-only, keyboard-first, and screenreader-friendly. A later GUI shell may wrap the same Go core, but this slice does not pretend that a web/Admin Console screen is the local app.
- deterministic CLI commands: `detect`, `validate`, `plan`/`init`, `commit`, and `push` for headless, recovery, and evidence flows.

## Product boundary

The bootstrapper lets an admin/operator choose an existing local repo or clone URL, choose worktree/storage locations, detect existing CI/CD files, choose exactly one target (`forgejo`, `github-actions`, `gitlab-ci`, or `azure-devops`), enter only non-secret target values and required secret-name hints, validate the repo/target state, generate a support-safe plan/workflow artifact, and optionally commit/push only to the selected target.

GitHub repository secrets are backend-specific and optional. They are not required for the Forgejo path. Forgejo credentials and runner registration belong in Forgejo/act_runner or a customer-owned SecretRef/external secret mechanism. GitHub secrets are only relevant when GitHub Actions is selected or intentionally bridges to the homelab.

## Evidence artifacts

- `tools/weave-setup/internal/bootstrap` — shared Go core for app and CLI modes.
- `tools/weave-setup/cmd/weave-setup` — executable entry point.
- `release/provider-lab/local-cicd-bootstrapper/support-safe-plan.fixture.json` — support-safe evidence fixture for #666.
- `e2e/features/sprint_27_local_cicd_bootstrapper.feature` — product-language Gherkin scenario.
- `tools/local_cicd_bootstrapper_check.py` — repo evidence guard.

## Gates

- `cd tools/weave-setup && go test ./...`
- `python3 tools/local_cicd_bootstrapper_check.py`
- `./gradlew localCicdBootstrapperCheck acceptanceContract docsCheck releaseEvidenceCheck --console=plain`

## Claim boundary

This slice may claim that Weave has a local Go executable bootstrapper contract and support-safe plan generation for setup/deploy/E2E orchestration. It must not claim runner registration, live Forgejo dispatch, `~/server` mutation, secret creation/rotation/deletion, production cutover, or release-ready deployment.
