# Sprint 15 Closure Report — Organization Embedding, Domain Facades, and Provider-Neutral Proof

## Governing sources

- Organization embedding epic: [weave#558](https://github.com/masssi164/weave/issues/558)
- Provider-neutral domain facade epic: [weave#559](https://github.com/masssi164/weave/issues/559)
- Product boundary/release evidence epic: [weave#568](https://github.com/masssi164/weave/issues/568)
- Sprint milestone: `Sprint 15 — Organization Embedding, Domain Facades & Provider-Neutral Proof`
- Organization contracts: `docs/organization-embedding-contract.md`, `docs/identity-provisioning-strategy.md`, `docs/admin-provisioned-first-use.md`, `docs/admin-operator-handbook.md`
- Provider/domain contracts: `docs/provider-replacement-and-anti-silo-contract.md`, `docs/architecture/provider-portability.md`, `docs/architecture/no-unaccounted-data-loss.md`, `docs/architecture/canonical-domains.md`
- Product/release contracts: `docs/v0.1-golden-path.md`, `docs/iso-9241-110-dogfood-ux-gate.md`, `docs/user-handbook.md`, `docs/product-trust-provider-choice-claim-matrix.md`, `docs/release-notes/unreleased.md`
- Chat proof boundary: `docs/matrix-chat-migration-proof.md`, `docs/matrix-chat-sprint15-dry-run-policy.md`, `docs/matrix-chat-dry-run-operator-runbook.md`

## Delivered scope in PR #571

PR #571 closes Sprint 15 as target-vision evidence, not a Matrix-only slice:

- Backend/admin organization embedding evidence covers existing-org and new-org bootstrap, immutable `issuer+subject` admin recovery keys, deny-by-default effective policy, identity readiness, provider category selection, SecretRef-only configuration, support-safe audit events, and owner/admin/operator/member separation.
- Effective policy preview explains roles, groups, context roles, provider mappings, grants, denies, readiness/member-impact states, unknown-input fail-closed behavior, Weaver runtime disabled-by-default posture, and audit refs before member go-live or identity realm apply.
- Provider-neutral domain evidence models category manifests/facades for identity, Chat, Files, Calendar, Boards/Tasks, Meetings/Calls, Documents, Models, and Weaver. Matrix/Synapse is only the first Chat-domain dry-run proof: consequence counts, unsupported/E2EE blockers, lossy/manual-review/archive-only fields, rollback/retention limits, member-impact copy, audit refs, and apply blockers are backend-owned and reusable.
- Admin Console evidence shows role-appropriate Workspace Health, guided setup, provider category readiness, effective policy explanation, member capability preview, provider-replacement dry-run results, SecretRef inventory, audit trail, and embedded help/copy. Member-facing vocabulary remains Weave domain/capability states only.
- Accessibility/release evidence is scoped and honest: automated Admin Console copy/ARIA assertions and `docs/evidence/accessibility/sprint-15-provider-switch-at-template.md` cover the Sprint 15 provider-switch journey, while release wording forbids production apply/cutover, lossless migration, E2EE history migration, legal compliance, or raw-provider setup claims.

## Epic acceptance traceability

| Issue | Closure evidence | Result |
| --- | --- | --- |
| [weave#558](https://github.com/masssi164/weave/issues/558) | `AdminControlPlaneControllerTest` covers admin-only control plane, identity readiness, existing/new organization bootstrap, effective-policy simulation, operator read/no-write boundary, member denial, SecretRef-only provider configuration, last-admin guards, and redacted audit. `AdminControlPlaneServiceTest`, `KeycloakRealmDryRunProviderTest`, `KeycloakRealmLiveApplyAdapterTest`, `docs/organization-embedding-contract.md`, `docs/identity-provisioning-strategy.md`, and `docs/admin-operator-handbook.md` cover verified domains/source selection, OIDC/SAML/SCIM/LDAP/AD strategy, guests, service principals, deprovisioning, break-glass, immutable keys, deny-by-default policy, and support-safe readiness. | Satisfied. |
| [weave#559](https://github.com/masssi164/weave/issues/559) | `ProviderRegistryTest`, `DomainAdapterRegistryMapperTest`, `CanonicalDomainRegistryContractTest`, `CanonicalDomainFacadeServicesTest`, `PlatformProductContractControllerTest`, `MigrationDryRunServiceTest`, `ProviderReplacementDryRunResponse`, `AdminControlPlaneService`, Admin Console tests, portability fixtures, and the Matrix Sprint 15 runbook prove category-first provider manifests, backend-owned Chat facade/dry-run evidence, portable export/import/replacement shape, fail-closed apply blockers, support-safe consequence reports, and Matrix limitations as explicit blockers. | Satisfied. |
| [weave#568](https://github.com/masssi164/weave/issues/568) | Admin Console tests assert organization overview, guided setup, readiness dashboard, embedded admin/member copy, effective-policy explanation, member capability preview, provider-switch consequence preview, provider-internal leakage guards, ARIA labels, and support-safe audit/evidence sections. `README.md`, `docs/release-notes/unreleased.md`, claim matrix, user/admin handbooks, accessibility template, and this closure report bind claims to evidence and keep member/admin/operator UX boundaries. | Satisfied. |

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

CI evidence must be green on PR #571 before merge.

## Boundaries carried forward

- No production provider apply/cutover is enabled in Sprint 15.
- Matrix apply remains blocked until a future spec introduces explicit apply gates, rollback validation, operator approval, and CI/live evidence.
- E2EE encrypted-room history is not migrated server-side and remains `unsupported`/`coming_later` pending a client-side export/key strategy.
- Matrix power-level parity and media retention require manual review; unsupported/lossy/archive-only counts must remain visible in support-safe evidence.
- Normal member UX must not expose raw provider URLs, `mxc://`, tokens, homeserver details, provider internals, raw diagnostics, support bundles, or setup paths.
- Manual assistive-technology execution remains a release-promotion activity; Sprint 15 supplies the scoped template and automated contract evidence, not a fabricated manual pass.

## Closure gate

- [x] #558 organization/effective-policy acceptance evidence is complete for Sprint 15 scope.
- [x] #559 reusable domain-facade/category acceptance evidence is complete for Sprint 15 scope with Chat as first proof.
- [x] #568 product boundary/help/accessibility/release acceptance evidence is complete for Sprint 15 scope.
- [x] Backend and Admin Console proof code remains dry-run-only and support-safe.
- [x] Claim/release wording documents organization embedding, domain facades, product boundaries, and dry-run-only boundaries.
- [x] Local gates pass after the target-vision rescope.
- [x] PR #571 merged with green GitHub CI as `dcb2f2db8725aa32a5f8ef10d6c771b028d14255`; push CI run `26746805501` passed.
- [x] GitHub issues #558, #559, and #568 closed with this report and PR #571 links.
- [x] Sprint 15 milestone closed after issue closure (`open_issues=0`, `closed_issues=13`, closed 2026-06-01T09:40:07Z).
