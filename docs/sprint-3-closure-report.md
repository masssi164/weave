# Sprint 3 closure report: provider-neutral domain adapter control plane

Status: final closure evidence, 2026-05-26.

## Closure scope

Sprint 3 closes the provider-neutral domain adapter control plane. The final state is Weave product-first: stable member domains, backend/admin-owned provider adapters, support-safe readiness, and explicit migration/audit boundaries. Default dogfood providers remain implementation choices for operators and admins, not the product identity presented to members.

## Final closure status

- Sprint 3 milestone: `Sprint 3 — Provider-neutral product maturity`, closed with `open_issues: 0` and `closed_issues: 12`.
- Sprint 3 umbrella: #311, closed after final PR merge and post-merge CI.
- Final closure PR: #322, `fix: align live E2E with member provider boundary`.
- Final merge commit on `main`: `382676529c86d0da95156687e74cd862261e54ca`.
- Final PR head before merge: `95b2bad7844e`.
- Copilot review was requested and re-run. Its actionable Matrix-connect diagnostic was addressed before merge.

## Issue and PR graph

| Area | Closing evidence | State |
| --- | --- | --- |
| Live-stack dispatch guard | PR #300 closed issue #246 by making unconfirmed Live Stack E2E dispatch actionable instead of silently green. | Merged before closure. |
| Workspace Health cockpit and support-safe readiness | PR #319 closed issues #250 and #315 by exposing adapter readiness evidence through Admin/Operator surfaces and support bundles without leaking raw provider data. | Merged before closure. |
| Manuals, canonical models, diagrams, release workflow | PR #320 closed issues #289, #296, and #293 by publishing canonical feature models, embedded manual contracts, and generated/reviewable release-note workflow. | Merged before closure. |
| Live E2E member/provider boundary | PR #322 aligned live E2E, contract tests, feature mappings, and Matrix-connect diagnostics with the Sprint 3 member/admin boundary. | Merged as `382676529c86d0da95156687e74cd862261e54ca`. |
| Sprint 3 epic | Issue #311 tracked the umbrella acceptance graph and final closure evidence. | Closed after #322 merge, Live Stack E2E, and post-merge `main` CI. |

## Frozen vocabulary and boundaries

Sprint 3 freezes member-facing vocabulary as Weave domains and capability states, not provider brands:

- Domains: Chat, Files, Calendar, Boards/Tasks, Meetings, Decisions, Identity/Admin/Policy, and Health.
- Member states: ready/usable, disabled, degraded, policy-blocked, or admin setup required.
- Backend/admin primitives: ProviderConfig, ProviderMapping, CapabilityPolicy, SecretRef, Readiness, AuditEvent, adapter registry, migration plan, lossy mapping notes, rollback evidence.
- Provider names such as Keycloak, Matrix, Nextcloud, OpenProject, and LiveKit are dogfood adapter/admin/operator context. They are not the member-facing product model.

## Domain → Adapter → Readiness → Migration Contract

The closing contract is:

1. **Domain:** the member client consumes Weave-owned feature models and routes.
2. **Adapter:** the backend owns provider mapping, credentials, lossy conversion, authorization, and audit.
3. **Readiness:** Admin/Operator surfaces inspect support-safe category readiness and fail-closed states; support bundles include only redacted booleans, stable adapter keys, counts, and codes.
4. **Migration:** provider swaps are not a generic marketplace claim. They are supported only where a specific migration contract proves authorization, readiness, lossy-field handling, audit publication, rollback/restore path, and member-state stability.

## Member/admin diagnostic split

- Raw diagnostics, provider URLs, tokens, downstream bodies, provider errors, endpoint rotation, SecretRefs, and backup/restore evidence are Admin/Operator-only and must be redacted or represented by stable codes in support artifacts.
- Member UX renders stable Weave product capabilities and safe impact states. Members do not configure raw providers or receive provider-specific remediation details.
- OpenProject is the first real Boards/Tasks provider validation path; it is not the visible product UX and is not proof of a broad provider marketplace.
- Meetings use a backend token/readiness facade. LiveKit is the dogfood media provider path, but media encryption, recording, transcription, captions, and retention claims remain guarded until independently evidenced.
- Weaver remains a governed per-user PA/runtime direction. Sprint 4 may introduce read-only/proposal-first context assistance, but silent team-room writes remain out of scope until policy, consent, receipts, and audit evidence are mature.

## README and release evidence review

README.md was read as a whole public product document for this closure, not treated as a phrase-blacklist target. The closure pass checked end-to-end positioning, shipped/guarded/future separation, default dogfood stack naming, evidence-scoped screenshots, release-note vs release-evidence separation, and overclaiming risks around provider swaps, OpenProject, LiveKit/media, release publication, generic marketplace language, and Weaver.

The README release-note tooling was changed accordingly: the previous special-phrase blacklist was removed. The remaining check is deterministic structure only: expected managed markers, marker ordering, required top-level sections, and release-note/release-evidence marker placement plus the existing round-trip comparison against generated managed blocks.

## Final evidence snapshot

| Evidence | Run / SHA | Result |
| --- | --- | --- |
| Final PR head | `95b2bad7844e` | PR #322 review-ready head after addressing Copilot's Matrix-connect diagnostic. |
| PR CI on final head | GitHub Actions CI on `95b2bad7844e` | `success`; Gradle CI passed and Release Notes Label Check was skipped as expected for labeled PR context. |
| Live Stack E2E on final head | GitHub Actions run `26433113444` on `95b2bad7844e` | `success`; `Bootstrap Stack And Run App E2E` completed in `11m14s`. |
| Final merge commit | `382676529c86d0da95156687e74cd862261e54ca` | Squash merge of #322 into `main`. |
| Post-merge `main` CI | GitHub Actions run `26433489753` on `382676529c86` | `success`; Gradle CI completed in `6m0s`. |
| Sprint 3 umbrella | #311 | Closed with all checklist items completed. |
| Sprint 3 milestone | Milestone #3 | Closed with `open_issues: 0`, `closed_issues: 12`. |

## Local PR gates used during closure

Local gates used across the closure PR series included:

```bash
make docs-check
make acceptance-contract
./gradlew releaseEvidenceCheck
python3 tools/readme_release_notes.py --check
make infra-static
bash infra/weave-workspace/tests/infra-product-contract-test.sh
bash infra/weave-workspace/tests/acceptance-feature-mapping-test.sh
flutter test test/live_stack_contract_test.dart test/live_stack_feature_mapping_test.dart test/release_1/v0_1_release_spine_contract_test.dart
```

The final PR #322 remained a live-E2E/member-boundary fix and did not publish GitHub releases automatically.

## Residual risks and non-goals

- No GitHub release was published by Sprint 3 closure work; release publication remains an explicit later action.
- Broad provider marketplaces, Teams/Slack migration tooling, full generic provider swaps, media recording/transcription/caption claims, and Weaver runtime write operation remain outside the closed Sprint 3 scope unless promoted by later contracts and evidence.
- Sprint 4 should start from this boundary: user-facing accessible work rooms and governed read-only/proposal-first Weaver context assistance, not another provider-specific expansion sprint.
