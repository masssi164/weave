# Admin Console CI/CD setup proof

Status: Sprint 26 / #659 contract and local Forgejo proof seam.

## Scope

This evidence establishes the Admin Console as the canonical setup surface and CI/CD as the execution/validation backend. It is support-safe and contract-first: it validates provider manifests, SecretRef/variable-name display, pipeline run references, status copy, and fail-closed behavior. It does not dispatch a real pipeline while the local Forgejo runner is missing.

## Artifacts

- `specs/admin-ci-cd-orchestration-contract.md` — #659 contract for setup states, `PipelineProviderManifest`, `PipelineRunRef`, SecretRef validation, fail-closed behavior, and the local Forgejo seam.
- `release/provider-lab/admin-cicd/pipeline-provider-manifest.fixture.json` — GitHub Actions, Azure DevOps, local Forgejo Actions, and Woodpecker manifest coverage.
- `release/provider-lab/admin-cicd/local-forgejo-setup-proof.fixture.json` — local Forgejo target proof with connection refs, secret/variable names, runner-missing preflight block, and support-safe run-ref shape.
- `release/provider-lab/admin-cicd/admin-console-copy.fixture.json` — Admin Console missing-name/status/progress/fail-closed copy model.
- `e2e/features/sprint_26_admin_cicd_setup.feature` — Gherkin mapping for the first setup-flow proof.
- `docs/evidence/admin-cicd-ui-test-plan.md` — Admin Console UI path coverage plan for provider selection, fallback, missing-secret display, blocked trigger, run status, dry-run, abort, apply, and post-reconcile evidence.

## Local Forgejo status

Support-safe local diagnostic on 2026-06-03 found the Forgejo service and database service present. No act_runner/Woodpecker runner service was present. The setup proof therefore blocks trigger before dispatch with `runner_missing` and shows only the missing runner-registration name/role in Admin Console copy.

## Gates

- `python3 tools/admin_cicd_orchestration_check.py`
- `./gradlew releaseEvidenceCheck --console=plain`
- `./gradlew acceptanceContract --console=plain` after the Gherkin scenario mapping is updated.
- `./gradlew specContract docsCheck --console=plain` for the new contract and evidence docs.

## Claim boundary

This proof may claim that Weave has a support-safe contract and local Forgejo preflight seam for Admin Console-driven CI/CD setup orchestration. It must not claim production cutover, customer-ready migration automation, live Forgejo pipeline dispatch, runner installation, commercial adapter implementation, raw CI log access, raw provider diagnostics, token visibility, or secret-value validation.
