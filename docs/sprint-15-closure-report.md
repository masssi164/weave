# Sprint 15 Closure Report — Matrix Chat Dry-Run and Admin Provider Switch

## Governing sources

- Sprint epic: [weave#558](https://github.com/masssi164/weave/issues/558)
- Closure issue: [weave#570](https://github.com/masssi164/weave/issues/570)
- Sprint milestone: `Sprint 15 — Matrix Chat Migration Dry-Run & Admin Provider Switch`
- Matrix proof boundary: `docs/matrix-chat-migration-proof.md`
- Sprint 15 policy/runbook: `docs/matrix-chat-sprint15-dry-run-policy.md` and `docs/matrix-chat-dry-run-operator-runbook.md`
- Provider portability contracts: `docs/architecture/provider-portability.md` and `docs/architecture/no-unaccounted-data-loss.md`
- Admin/operator guidance: `docs/admin-operator-handbook.md`

## Delivered scope

Sprint 15 implements the minimum coherent Matrix Chat dry-run/provider-switch slice without enabling production apply or cutover:

- Backend admin provider-replacement dry-runs return Matrix-aware, support-safe consequence preview evidence: preserved/lossy/unsupported/manual-review/archive-only counts, member-impact copy, rollback limits, and apply blockers.
- Matrix Chat provider replacement is deliberately reported as `dry-run-blocked-for-apply`; apply/cutover remains blocked by Sprint 15 default policy.
- The migration dry-run service emits Matrix-specific review-only cutover gates and stores support-safe artifact refs without admin approval.
- Admin Console provider-switch review surfaces backend consequence preview, member-impact copy, rollback limits, and apply blockers without exposing raw Matrix URLs, `mxc://`, tokens, homeserver internals, or raw provider diagnostics.
- Power-level, media-retention/rollback, and E2EE client-export policies are documented as blockers to future apply.
- Release/customer wording stays inside a dry-run-only claim boundary: no production Matrix migration, no automated switch, no lossless migration, no legal-compliance guarantee, and no E2EE history migration claim.

## Issue traceability

| Issue | Sprint 15 outcome | Evidence |
| --- | --- | --- |
| [weave#558](https://github.com/masssi164/weave/issues/558) | Sprint orchestration closes after this implementation PR is merged, issue comments are linked, and the milestone is closed. | This closure report, GitHub PR/CI, sprint issue lint. |
| [weave#559](https://github.com/masssi164/weave/issues/559) | Matrix Chat dry-run runner skeleton emits backend-owned support-safe evidence and review-only gates. | `MigrationDryRunService`, `MigrationDryRunServiceTest`. |
| [weave#560](https://github.com/masssi164/weave/issues/560) | Matrix export inventory/canonical mapping fixtures remain executable and are referenced by the dry-run evidence boundary. | `specs/0006-portability-contract/*matrix*`, `./gradlew portabilityContractCheck`. |
| [weave#561](https://github.com/masssi164/weave/issues/561) | Backend provider-switch preflight/consequence API returns Matrix blockers, consequence counts, rollback limits, and support-safe audit refs. | `ProviderReplacementDryRunResponse`, `AdminControlPlaneService`, `PlatformProductContractControllerTest`. |
| [weave#562](https://github.com/masssi164/weave/issues/562) | Admin Console displays Matrix dry-run consequence evidence and blockers in provider-switch review. | `admin-console/src/App.tsx`, `admin-console/src/App.test.tsx`, `admin-console/src/api.ts`. |
| [weave#563](https://github.com/masssi164/weave/issues/563) | Power-level parity is documented as lossy/manual-review/unsupported and apply-blocking. | `docs/matrix-chat-sprint15-dry-run-policy.md`. |
| [weave#564](https://github.com/masssi164/weave/issues/564) | Media retention is documented as copy/archive/reference policy with rollback caveats and raw-media redaction. | `docs/matrix-chat-sprint15-dry-run-policy.md`, `docs/matrix-chat-dry-run-operator-runbook.md`. |
| [weave#565](https://github.com/masssi164/weave/issues/565) | E2EE server migration stays unsupported/coming_later until client-side key/export strategy exists. | `docs/matrix-chat-sprint15-dry-run-policy.md`, Matrix fixture gates. |
| [weave#566](https://github.com/masssi164/weave/issues/566) | Member disruption copy uses provider-neutral capability states and text-first consequence copy. | Admin Console tests, API normalization, `docs/evidence/accessibility/sprint-15-provider-switch-at-template.md`. |
| [weave#567](https://github.com/masssi164/weave/issues/567) | Operator runbook documents dry-run-only review, rollback, and redaction requirements. | `docs/matrix-chat-dry-run-operator-runbook.md`, `docs/admin-operator-handbook.md`. |
| [weave#568](https://github.com/masssi164/weave/issues/568) | Release evidence gates include server/admin tests plus portability, product-trust, docs, and release evidence checks. | Gate list below. |
| [weave#569](https://github.com/masssi164/weave/issues/569) | Release/customer wording updated to dry-run-only evidence. | `README.md`, `docs/release-notes/unreleased.md`, claim matrix. |
| [weave#570](https://github.com/masssi164/weave/issues/570) | Closure report created; final issue/milestone closure happens after merge and green checks. | This file. |

## Local gates

```text
python3 scripts/lint_sprint_issues.py sprint_15
# PASS (13 issues)

./gradlew serverCi adminCi portabilityContractCheck productTrustClaimMatrixCheck docsCheck releaseEvidenceCheck --no-daemon
# BUILD SUCCESSFUL
```

PR/CI gate evidence is recorded on the Sprint 15 PR before merge.

## Boundaries carried forward

- No production Matrix apply/cutover is enabled in Sprint 15.
- Matrix apply remains blocked until a future spec introduces explicit apply gates, rollback validation, operator approval, and CI/live evidence.
- E2EE encrypted-room history is not migrated server-side and remains `unsupported`/`coming_later` pending a client-side export/key strategy.
- Matrix power-level parity and media retention require manual review; unsupported/lossy/archive-only counts must remain visible in support-safe evidence.
- Normal member UX must not expose raw Matrix URLs, `mxc://`, tokens, homeserver details, provider internals, or raw diagnostics.

## Closure gate

- [x] Minimum implementation slice is support-safe and dry-run-only.
- [x] Backend and Admin Console tests pass locally.
- [x] Portability, product-trust, docs, and release evidence gates pass locally.
- [x] Claim/release wording documents dry-run-only boundaries.
- [ ] Sprint PR merged with green CI.
- [ ] Issues #558-#570 closed or explicitly carried over.
- [ ] Sprint 15 milestone closed after zero open issues remain.
