# Admin-Suite readiness and setup contract

Status: WEAVE-SPEC-0001 issue #387 contract.

## Product rule

The Admin-Suite is the owner/admin/operator control plane for organization setup, provider readiness, policy, diagnostics, and repair. Normal members enter an already-provisioned organization and consume only provider-neutral capability states from the organization manifest.

This contract extends the [Admin-provisioned first use boundary](admin-provisioned-first-use.md) and [Weave product line and Weaver integration plan](product-line-and-weaver-plan.md) for WEAVE-SPEC-0001. It does not add member-facing provider setup and it does not claim full provider migration automation; portable switch/export/import details remain in the provider replacement contract.

## Guided setup assistant

The Admin-Suite setup assistant must lead an owner/admin through these steps before member go-live:

1. Choose or confirm WEAVE-SPEC-0001 provider domains: IDM/RBAC, Chat/Channels, Files/Docs, Boards/Tasks, Calendar/Events, Meetings, and Forms/Contacts.
2. Bind the selected adapter through backend admin APIs only; never call provider admin APIs directly from the browser client.
3. Keep secrets as `SecretRef` handles and reject raw secrets, bearer tokens, credential-bearing URLs, raw downstream payloads, or provider diagnostics in form fields and evidence.
4. Run dry-run/preflight validation before apply.
5. Show an effective member preview using only `available`, `disabled_by_policy`, `not_configured`, `degraded`, `unavailable`, or `coming_later`.
6. Explain consequences, recovery path, and rollback/support boundary before any irreversible bind, unbind, switch, detach, or policy change.
7. Write support-safe audit evidence for every setup, readiness, and policy action.

## Readiness dashboard

The Admin-Suite readiness dashboard is per domain, not vendor-first. Every domain row must include:

| Field | Contract |
| --- | --- |
| Domain | Canonical Weave domain/category label. |
| Admin readiness | Admin/operator status and next action from backend readiness/policy. |
| Member preview | Provider-neutral member state only: `available`, `disabled_by_policy`, `not_configured`, `degraded`, `unavailable`, or `coming_later`. |
| Selected adapter | Visible only to owner/admin/operator roles. |
| Evidence | Support-safe references, never raw provider payloads or secrets. |
| Recovery | Repair or rollback guidance before irreversible actions. |

Admin/operator readiness may use implementation states such as ready, configured, degraded, misconfigured, policy-blocked, admin-action-required, disabled, unsupported, or not_configured, but those states must be translated before reaching normal member contracts.

## Role and action boundary

- `owner` and delegated `admin`: may configure provider categories, policy, readiness, setup, preflight, and guarded apply paths.
- `operator`: may inspect readiness, run delegated tests/preflight, collect support-safe evidence, and execute delegated repair actions.
- `member` and `guest`: must not see provider setup controls, diagnostics, selected adapters, `SecretRef` handles, raw provider names in core workflows, or recovery internals.

Bind, unbind, switch, and detach are admin-side actions. They must be unavailable or blocked until preflight evidence, member impact preview, consequence copy, and recovery guidance exist. Switch/export/import/cutover details are governed by [Provider replacement and anti-silo contract](provider-replacement-and-anti-silo-contract.md).

## Evidence and gates

#387 evidence is satisfied when:

- the Admin Console exposes a guided setup assistant and per-domain readiness dashboard for owner/admin/operator roles;
- member preview remains provider-neutral and hides provider/admin controls;
- backend/admin API contracts remain support-safe and redacted;
- `./gradlew specContract acceptanceContract adminCi --console=plain` passes for the slice.

## RC go-live claim control

For #586 and `WEAVE-RC-GATE`, the guided setup surface must include one RC go-live decision summary instead of scattering release readiness across separate checklists. The summary is owner/admin/operator-only and must join:

- identity/provider setup, SecretRef/CredentialRef posture, policy preview, suite readiness, governed Weaver/MCP posture, support-bundle posture, and release blockers;
- pinned spec corpus reference, conformance/acceptance gate evidence, generated release-notes source, support-safe bundle ref, accessibility evidence ref, unresolved Veto/blockers, audit refs, freshness, and next actions;
- explicit release-claim control: any missing, stale, sample-only, or unresolved release-blocking gate blocks RC or production claims until a release owner records evidence or an accepted blocker.

The RC summary must remain support-safe. It may show stable reason codes and evidence references, but never raw provider diagnostics, endpoint URLs, provider IDs, SecretRef values, bearer tokens, downstream bodies, raw Weaver runtime configuration, or member content. It does not authorize production cutover, live infrastructure mutation, RC tagging, or external release publication.

Support-bundle, audit, export/import, release-note, CI, Live Stack, accessibility, migration, and Weaver entries are evidence pointers with freshness and blocker state. A pointer is not a readiness claim by itself: missing, stale, sample-only, non-support-safe, or unresolved blocker evidence must render the RC summary blocked until a release owner records current evidence or an accepted issue-linked waiver.
