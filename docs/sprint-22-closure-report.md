# Sprint 22 closure report — Free Provider Lab

Status: implementation evidence prepared for Sprint 22 closure.

## Governing sources inspected

- GitHub issues #623, #624, #625, and #626 under milestone `Sprint 22 — Free Provider Lab`.
- `docs/sprint-21-closure-report.md` and PR #655 merge context.
- `docs/product-reality-foundation.md` and `release/product-reality-gates.json`.
- `tools/product_reality_claim_gate_check.py` and root Gradle verification tasks.
- `docs/product-line-and-weaver-plan.md` for provider-neutral product ordering and Weaver deferral.

## Issue DAG final state

1. #623 establishes the reproducible local provider-lab compose topology, health command, reset command, and runbook.
2. #624 depends on #623 provider scope and publishes one manifest per targeted provider with Sprint 21 reality levels.
3. #625 depends on #623/#624 chat scope and publishes the deterministic chat fixture needed by Sprint 23.
4. #626 depends on #623-#625 and adds the Sprint 23 entry scoreboard plus CI-safe gate.

All work is intentionally safe for local and CI validation. Live Docker provider startup remains an operator-run path and is not required by CI.

## Files and artifacts changed

- `infra/provider-lab/docker-compose.yml` — local Docker Compose topology for Keycloak, Authentik, Matrix/Synapse, Zulip, Nextcloud, MinIO, Radicale, OpenProject, and Docker Runtime boundary.
- `infra/provider-lab/.env.example` — local-only environment variable template.
- `infra/provider-lab/scripts/lab.sh` — start, stop, reset, inspect, health, and verify wrapper.
- `release/provider-lab/manifests/*.json` — provider manifests with one reality level, rollback honesty, history honesty, secret boundary, and redacted support evidence.
- `fixtures/provider-lab/chat-fixture.json` — deterministic Sprint 22 chat fixture with exact counts and history status expectations.
- `release/provider-lab/health-report.sample.json` — support-safe health report snapshot naming provider, domain, reality level, and timestamp.
- `release/provider-lab/support-redaction-report.json` — support-safe artifact redaction declaration and scan targets.
- `release/provider-lab/sprint-23-entry-scoreboard.json` — Sprint 23 entry gate scoreboard.
- `tools/provider_lab_check.py` — CI-safe validator for manifests, fixture, redaction artifacts, health, and scoreboard agreement.
- `build.gradle` — `providerLabCheck` task and inclusion in `releaseEvidenceCheck`.
- `docs/free-provider-lab.md` — operator/developer runbook.
- `docs/sprint-22-closure-report.md` — this closure report.

## Evidence commands

Required local evidence for closure:

```bash
./gradlew providerLabCheck --console=plain
./gradlew productRealityClaimGateCheck releaseEvidenceCheck docsCheck --console=plain
```

Expected Sprint 22 provider-lab gate summary:

```text
provider-lab-check: ok providers=10 fixtureMessages=50 scoreboard=sprint23EntryGate:green
```

## Review evidence

Review evidence must be attached to the Sprint 22 PR before merge. If GitHub Copilot review is unavailable, use a scoped specialist review of `infra/provider-lab`, `release/provider-lab`, `fixtures/provider-lab`, `tools/provider_lab_check.py`, and `docs/free-provider-lab.md`.

## Claim boundary

Sprint 22 does not claim provider interchangeability, migration apply readiness, production rollback, release readiness, Weaver availability, per-user PA availability, or full history preservation. Matrix/Synapse history specifically keeps E2EE history archive-only or blocked and rollback partial until named Sprint 23 evidence exists.

## Sprint 23 handoff

Sprint 23 Chat Provider Switch may start only from a green `providerLabCheck` and matching scoreboard. The next safe work is to use the Matrix/Synapse and Zulip manifests plus `fixtures/provider-lab/chat-fixture.json` to build dry-run chat migration evidence without applying migration or claiming production rollback.
