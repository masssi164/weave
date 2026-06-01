# v0.1.0-rc.3 release evidence

Status: published prerelease evidence, 2026-06-01.

## Release facts

- Release: [`v0.1.0-rc.3`](https://github.com/masssi164/weave/releases/tag/v0.1.0-rc.3)
- Published at: `2026-06-01T16:13:33Z`
- Candidate commit: `2f0794c46cf8ecc91697b930d27b443c12fdeec2` on protected `main`
- Tag target: `v0.1.0-rc.3` -> `2f0794c46cf8ecc91697b930d27b443c12fdeec2`
- Draft: `false`
- Prerelease: `true`

## Scope audit

`v0.1.0-rc.3` is a post-`v0.1.0-rc.2` rollup for the dogfood-production line. It includes provider-neutral Admin/Suite foundation work from Sprints 8-17 and the first RC-shaped governed Workspace/Weaver slice. Guarded or disabled surfaces remain named as such; this release does not claim production provider cutover, broad runtime execution, public GA readiness, or a production release.

The release closed the stale RC3 draft blocker #557 after refreshing the candidate, evidence, and release notes. Publication did not mutate live infrastructure, perform provider migration apply/cutover, or publish a final production release.

## Evidence reviewed

- Main CI on candidate commit: [`26761284816`](https://github.com/masssi164/weave/actions/runs/26761284816), success, for `2f0794c46cf8ecc91697b930d27b443c12fdeec2`.
- Local `./gradlew ci` on candidate commit: passed, with sanitized `build/evidence/ci-summary.json`.
- Release draft workflow: [`26762480045`](https://github.com/masssi164/weave/actions/runs/26762480045), success, artifact `release-draft-review-v0.1.0-rc.3` id `7334742373`.
- Credentialed Live Stack E2E on candidate commit: [`26762479975`](https://github.com/masssi164/weave/actions/runs/26762479975), success, artifact `weave-live-stack-acceptance-evidence`.
- Fresh Live Stack E2E rerun on the same candidate commit: [`26764652300`](https://github.com/masssi164/weave/actions/runs/26764652300), success, artifact `weave-live-stack-acceptance-evidence` id `7335979752`.
- Release blocker refresh/signoff trail: issue #557.
- RC readiness command shape: `./gradlew releaseReadinessCheck -PcandidateVersion=0.1.0-rc.3 -PcandidateTag=v0.1.0-rc.3 -PcandidateCommit=2f0794c46cf8ecc91697b930d27b443c12fdeec2 ...`.

## Release note summary

### Added

- Organization-embedded provider-neutral suite foundation: Spaces, Chat, Files/Documents, Calendar, Boards/Tasks, Admin Health/Ops, and provider reality levels behind Weave-owned domain facades.
- Guided Admin setup and go-live readiness surfaces with support-safe provider evidence, policy boundaries, release claim control, and explicit blockers.
- Governed Weaver RuntimeProfile and MCP direction through Weave-generated policy, grants, audit, and Streamable HTTP MCP bindings instead of raw provider or OpenClaw config exposure.
- Sprint 17 RC workspace slice with governed MCP/runtime invocation evidence, Space-centered Chat/Calendar/Boards/Files flow, and Admin RC go-live claim control.

### Changed

- Release evidence now separates offline contract/spec gates from credentialed Live Stack E2E evidence and keeps release promotion blocked without explicit green evidence or release-owner waiver.
- Provider portability wording uses conservative states (`contract_only`, `configured_readiness`, `live_adapter_read`, `live_adapter_write`, `migration_apply_ready`, `release_ready`) to prevent overclaiming.
- Matrix/Synapse remains the current real Chat provider path; other chat providers remain contract-only until separately promoted.

### Fixed

- Support-safe redaction and error handling for Matrix chat readiness, Nextcloud Files/Calendar adapters, Office/WOPI unavailable posture, OpenProject boards writes, and release evidence mapping.
- Android release identity and admin dependency policy checks are hardened.

## Current post-publication blockers

After RC3 publication, Sprint 18 added stronger workspace/migration/trust evidence and closed the live-stack evidence gap in PR #596. The remaining release-blocking carryover is #591: actual manual assistive-technology signoff is still required before Sprint 18 milestone closure or public/production release signoff. Automated tests, support-safe artifacts, release notes, and green Live Stack E2E cannot substitute for real AT reviewer evidence.
