# Sprint 27 domain-proof implementation result

Scope: #643, #644, #645, and #646. Historical #665 Forgejo evidence is obsolete; current GitHub delivery evidence remains separate from this Calendar/Files/Identity aggregate proof.

## Implemented

- Added support-safe provider-boundary artifacts under `release/provider-lab/cross-domain-provider-proof/`:
  - Calendar Nextcloud CalDAV -> Radicale proof for #643.
  - Files Nextcloud -> MinIO S3 proof for #644.
  - Identity Keycloak -> Authentik proof for #645.
  - Sprint 27 cross-domain scoreboard and provider-neutrality claim gate for #646.
- Added executable gate `tools/cross_domain_provider_proof_check.py`.
- Wired Gradle task `crossDomainProviderProofCheck` and included it in `releaseEvidenceCheck` and CI evidence metadata.
- Added Gherkin acceptance feature `e2e/features/sprint_27_cross_domain_provider_proof.feature`.
- Added acceptance mappings in `e2e/scenario_mappings.json`.
- Added evidence report `docs/evidence/sprint-27-cross-domain-provider-proof.md`.

## Gates run locally

- `python3 tools/cross_domain_provider_proof_check.py` — passed.
- `./gradlew crossDomainProviderProofCheck acceptanceContract --no-daemon --console=plain` — passed.
- `./gradlew releaseEvidenceCheck --no-daemon --console=plain` — passed.

## Branch / commit / PR

- Branch: `feat/sprint27-mainline-provider-proof`
- Commit: branch HEAD for `feat(provider): add Sprint 27 cross-domain proof gate`; final pushed hash is reported in the handoff.
- PR: #672 `https://github.com/masssi164/weave/pull/672` (updated on the existing Sprint 27 mainline provider-proof branch).
