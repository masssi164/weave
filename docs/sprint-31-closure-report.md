# Sprint 31 closure report — Physical iPhone LAN Dogfood

Sprint 31 implements the executable local LAN dogfood path for Massimo's physical iPhone. Remote GitHub issue and CI state remain the closure source of truth.

## Governing scope

- Milestone: `Sprint 31 — Physical iPhone LAN Dogfood`.
- Issues: #697, #698, #699, #700, #701.
- Prior contract: `release/sprint-30-hot-phase/profile-driven-setup.fixture.json`.
- Runbook: `docs/sprint-31-iphone-lan-dogfood-runbook.md`.
- Physical-device checklist: `docs/evidence/sprint-31-physical-iphone-lan-dogfood/physical-iphone-checklist.md`.

## Issue DAG final state

1. #697 and #698: one profile-driven command prepares readiness, validates LAN endpoint shape/TCP reachability, emits support-safe handoff/evidence under one run id.
2. #699: client `/join` handoff path consumes the generated LAN handoff and routes to existing SSO without normal-member provider setup fields.
3. #700: Sprint 31 guard and checklist prove loopback/Mac-only rejection, redaction, and physical iPhone evidence capture.
4. #701: runbook and closure report publish exact command, expected tester result, and remaining physical-device action.

## Exact command for Massimo

```sh
WEAVE_LAN_HOST=<Mac LAN IP> tools/weavectl profile apply \
  --profile local-lan-dogfood \
  --lan-host "$WEAVE_LAN_HOST" \
  --emit-handoff \
  --emit-evidence \
  --preflight-mode tcp
```

Expected output: `run_id=...`, `readiness=.../readiness.json`, `handoff=.../handoff.json`, `evidence=.../evidence.json`, the `weave:/join?...` deep link, the LAN URL/QR payload, and the screen-reader-friendly tester prompt.

## What Massimo should see

Massimo opens the QR/link from `handoff.json` on the physical iPhone, completes SSO, and lands in Weave workspace/home. The app must not ask for OIDC issuer, OIDC client ID, Matrix URL, Nextcloud URL, provider hostname, TLS certificate, provider diagnostics, SecretRef, or credential URL.

## Remaining physical-device step

The repository can prepare and validate the LAN handoff, but final success requires Massimo's real iPhone on the same LAN. Smallest remaining action: run the command with the actual Mac LAN IP while the stack is listening, open the emitted handoff on the iPhone, complete SSO, and fill the checklist result.

## Evidence and gates

- `python3 tools/sprint31_lan_dogfood_check.py`
- `./gradlew sprint31LanDogfoodCheck`
- `./gradlew acceptanceContract`
- `./gradlew clientCi`
- `./gradlew docsCheck`
- `./gradlew releaseEvidenceCheck`

Local evidence before PR:

- `python3 tools/sprint31_lan_dogfood_check.py`: pass.
- `./gradlew sprint31LanDogfoodCheck acceptanceContract docsCheck releaseEvidenceCheck`: pass.
- `cd client && flutter analyze --fatal-infos`: pass.
- `cd client && flutter test test/features/onboarding/member_handoff_test.dart test/features/auth/sign_in_screen_test.dart test/architecture/member_client_provider_boundary_contract_test.dart`: pass.

CI/main evidence is to be updated after the PR merges.
