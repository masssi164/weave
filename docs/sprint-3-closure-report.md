# Sprint 3 closure report: provider-neutral domain adapter control plane

Status: closure PR evidence, 2026-05-25.

## Closure scope

Sprint 3 closes the provider-neutral domain adapter control plane. The closing state is Weave product-first: stable member domains, backend/admin-owned provider adapters, support-safe readiness, and explicit migration/audit boundaries. Default dogfood providers remain implementation choices for operators and admins, not the product identity presented to members.

## Issue and PR graph

| Area | Closing evidence | State |
| --- | --- | --- |
| Live-stack dispatch guard | PR #300 closed issue #246 by making unconfirmed Live Stack E2E dispatch actionable instead of silently green. | Merged before closure. |
| Workspace Health cockpit and support-safe readiness | PR #319 closed issues #250 and #315 by exposing adapter readiness evidence through Admin/Operator surfaces and support bundles without leaking raw provider data. | Merged before closure. |
| Manuals, canonical models, diagrams, release workflow | PR #320 closed issues #289, #296, and #293 by publishing canonical feature models, embedded manual contracts, and generated/reviewable release-note workflow. | Merged before closure. |
| Sprint 3 epic | Issue #311 is the final closure issue for the domain adapter control plane. | To close only after this PR is merged, current-main CI is green, and final-main Live Stack E2E is green. |

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
- Weaver remains a future governed per-user PA runtime, disabled by default and later than admin portal, IDM/RBAC, readiness, and whitelisting.

## README and release evidence review

README.md was read as a whole public product document for this closure, not treated as a phrase-blacklist target. The closure pass checked end-to-end positioning, shipped/guarded/future separation, default dogfood stack naming, evidence-scoped screenshots, release-note vs release-evidence separation, and overclaiming risks around provider swaps, OpenProject, LiveKit/media, release publication, generic marketplace language, and Weaver.

The README release-note tooling was changed accordingly: the previous special-phrase blacklist was removed. The remaining check is deterministic structure only: expected managed markers, marker ordering, required top-level sections, and release-note/release-evidence marker placement plus the existing round-trip comparison against generated managed blocks.

## Evidence snapshot before closure PR

| Evidence | Run / SHA | Result |
| --- | --- | --- |
| Current `main` | `5cdd7b57bd2e60ab52afbb9f0c97f72f18ff7574` | Clean baseline before closure PR branch. |
| CI on current `main` | GitHub Actions run `26417215189` on `5cdd7b57bd2e60ab52afbb9f0c97f72f18ff7574` | `success`. |
| Live Stack E2E on current `main` | GitHub Actions run `26419132246` on `5cdd7b57bd2e60ab52afbb9f0c97f72f18ff7574` | `failure`: operator-check still expected a member token to read `/providers/status`. This PR updates live/operator/smoke checks to the Sprint 3 boundary: provider registry diagnostics are Admin/Operator-only and member tokens receive `403`. Final closure evidence must use the post-merge run below. |

## Local PR-C gates

Completed locally before opening the closure PR:

```bash
make docs-check                         # pass
make acceptance-contract                # pass
./gradlew releaseEvidenceCheck          # pass
python3 tools/readme_release_notes.py --check  # pass
make infra-static                       # pass
bash infra/weave-workspace/tests/infra-product-contract-test.sh  # pass
bash infra/weave-workspace/tests/acceptance-feature-mapping-test.sh  # pass
```

The PR must remain documentation/operator-tooling only and must not publish GitHub releases automatically.

## Residual risks and non-goals

- Final closure still requires CI on the merged closure PR and a final-main Live Stack E2E run with `power_storage_confirmation=I_HAVE_SOLAR_STORAGE_BUDGET`.
- No GitHub release is published by this closure work.
- Broad provider marketplaces, Teams/Slack migration tooling, full generic provider swaps, media recording/transcription/caption claims, and Weaver runtime operation remain outside v0.1 unless promoted by later contracts and evidence.
