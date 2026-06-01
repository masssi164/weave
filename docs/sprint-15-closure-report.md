# Sprint 15 Closure Report — Organization Embedding, Domain Facades, and Provider-Neutral Proof

## Governing sources

- Organization embedding epic: [weave#558](https://github.com/masssi164/weave/issues/558)
- Provider-neutral domain facade epic: [weave#559](https://github.com/masssi164/weave/issues/559)
- Product boundary/release evidence epic: [weave#568](https://github.com/masssi164/weave/issues/568)
- Sprint milestone: `Sprint 15 — Organization Embedding, Domain Facades & Provider-Neutral Proof`
- Organization contracts: `docs/organization-embedding-contract.md`, `docs/identity-provisioning-strategy.md`, `docs/admin-provisioned-first-use.md`, `docs/admin-operator-handbook.md`
- Provider/domain contracts: `docs/provider-replacement-and-anti-silo-contract.md`, `docs/architecture/provider-portability.md`, `docs/architecture/no-unaccounted-data-loss.md`
- Chat proof boundary: `docs/matrix-chat-migration-proof.md`, `docs/matrix-chat-sprint15-dry-run-policy.md`, `docs/matrix-chat-dry-run-operator-runbook.md`

## Delivered scope in this PR

This PR is reused as a target-vision Sprint 15 draft. It does **not** close the epics yet; it contributes the first executable Chat-domain proof slice under the broader organization/provider-neutral plan:

- Backend admin provider-replacement dry-runs return support-safe Chat consequence evidence: preserved/lossy/unsupported/manual-review/archive-only counts, member-impact copy, rollback limits, and apply blockers.
- The Admin Console provider-switch review surfaces domain-first consequence preview and next-action copy without exposing raw Matrix URLs, `mxc://`, tokens, homeserver internals, provider IDs, or raw diagnostics to members/support-safe artifacts.
- Matrix/Synapse remains only the current real Chat adapter proof. Production apply/cutover, lossless migration, E2EE history migration, and legal/compliance claims stay blocked.
- Operator docs tie the Chat proof to organization setup, effective policy, deny-by-default readiness, support bundles, and role boundaries: owner/admin/operator/member/guest stay distinct.
- Release wording frames Sprint 15 as organization embedding, provider-neutral domain facades, and bounded Matrix Chat dry-run evidence; Matrix is not the sprint identity.

## Target-vision issue traceability

| Issue | Current status | Evidence in this PR | Remaining blocker before issue closure |
| --- | --- | --- | --- |
| [weave#558](https://github.com/masssi164/weave/issues/558) | Open / partially evidenced | Admin/operator handbook keeps organization setup, immutable identity keys, verified domains, provider category selection, deny-by-default policy, SecretRefs, support bundles, and member capability-state boundaries out of normal member UX. | Full organization embedding/effective-policy preview evidence across identity/group/role mappings, guests, service principals, break-glass, deprovisioning, and last-admin protection. |
| [weave#559](https://github.com/masssi164/weave/issues/559) | Open / first proof slice | `ProviderReplacementDryRunResponse`, `AdminControlPlaneService`, `MigrationDryRunService`, Admin Console consequence preview, Matrix fixtures, portability gates, and runbook prove a reusable Chat dry-run workflow with lossy/unsupported/manual-review/archive-only consequences and apply blockers. | Generalized category manifests/facades beyond Chat and promotion evidence for any future apply path. |
| [weave#568](https://github.com/masssi164/weave/issues/568) | Open / partially evidenced | Member-impact copy stays provider-neutral; Admin Console consequence copy is role-appropriate; README/release notes/claim matrix bound claims to dry-run evidence; accessibility AT template records pending manual evidence. | Broader member/admin embedded help and accessibility execution evidence across Home/channel/settings/Workspace Health, plus final release evidence after all Sprint 15 slices land. |

## Local gates

```text
python3 /Users/flotterotter/sprints/scripts/lint_sprint_issues.py sprint_15
# PASS (3 active outcome epics)

./gradlew productTrustClaimMatrixCheck portabilityContractCheck docsCheck releaseEvidenceCheck --no-daemon
# BUILD SUCCESSFUL

./gradlew serverCi adminCi --no-daemon
# BUILD SUCCESSFUL

./gradlew acceptanceContract --no-daemon
# BUILD SUCCESSFUL

make docs-check
# BUILD SUCCESSFUL
```

PR/CI gate evidence must be current before merge. Keep #558/#559/#568 open unless the final Sprint 15 closure PR supplies the remaining acceptance evidence.

## Boundaries carried forward

- No production provider apply/cutover is enabled in Sprint 15.
- Matrix apply remains blocked until a future spec introduces explicit apply gates, rollback validation, operator approval, and CI/live evidence.
- E2EE encrypted-room history is not migrated server-side and remains `unsupported`/`coming_later` pending a client-side export/key strategy.
- Matrix power-level parity and media retention require manual review; unsupported/lossy/archive-only counts must remain visible in support-safe evidence.
- Normal member UX must not expose raw provider URLs, `mxc://`, tokens, homeserver details, provider internals, raw diagnostics, support bundles, or setup paths.

## Closure gate

- [x] This PR is reframed as a target-vision draft with Matrix Chat only as the first provider-neutral Chat proof slice.
- [x] Backend and Admin Console proof code remains dry-run-only and support-safe.
- [x] Claim/release wording documents organization embedding, domain facades, product boundaries, and dry-run-only boundaries.
- [x] Current local gates pass after the target-vision rescope.
- [ ] #558 organization/effective-policy acceptance evidence is complete.
- [ ] #559 reusable domain-facade/category acceptance evidence is complete.
- [ ] #568 product boundary/help/accessibility/release acceptance evidence is complete.
- [ ] Sprint PR merged with green CI and explicit issue carry-over/closure decision.
