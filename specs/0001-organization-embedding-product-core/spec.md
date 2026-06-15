---
id: WEAVE-SPEC-0001
title: Admin-Suite and provider-neutral product core
version: 0.1.0
status: accepted
domain: product-core
owner: weave-co-leader
github_issue: 381
supersedes: []
depends_on:
  - WEAVE-SPEC-0000
acceptance_features:
  - e2e/features/weave_spec_0001_acceptance.feature
evidence_gates:
  - ./gradlew specContract
  - ./gradlew specContractTest
  - ./gradlew acceptanceContract
---

# Feature specification: Admin-Suite and provider-neutral product core

## Decision record

Massimo decided the `WEAVE-SPEC-0001` product core through the Nextcloud Forms response submitted on 2026-05-28.

Weave is a provider-neutral collaboration platform. Its first product-core slice is the first-class Admin-Suite plus provider-neutral domain/capability contracts. Admins bind, unbind, validate, switch, and detach provider adapters per domain. Normal members do not manage providers, status, reachability, diagnostics, OIDC setup, secrets, or repair flows. Members join an already configured organization through invite/SSO/passkey and then see stable Weave capabilities.

The product supports three first-class organization starting scenarios: `deploy_new` for greenfield organizations, `attach_existing` for brownfield organizations that attach their current provider landscape, and `hybrid` for per-domain coexistence during discovery, migration planning, and cutover. Weave recommends self-hosted or otherwise sovereign providers where practical, while allowing existing commercial/hyperscaler providers as active bindings during attach, discovery, coexistence preflight, and migration. Product and operator copy may explain portability, auditability, jurisdiction, data-sovereignty, and compliance benefits, but must include a non-legal-advice boundary and must not promise legal outcomes.

Weaver/AI runtime is explicitly out of scope for this spec. It may appear later as a governed domain, but it must not be introduced accidentally or before admin/provider governance is mature.

## Intent

Define the product contract that lets Weave stay provider-neutral while still being operationally usable:

- admins get a comfortable control plane for provider setup, readiness, switching, and recovery;
- the backend owns provider facades/adapters and support-safe evidence;
- member clients consume provider-agnostic capability manifests;
- provider switching is planned and auditable, with v0.1 guaranteeing portable export/import contracts before full automation.

## Product boundaries

### In scope

- First-class Admin-Suite as provider/adapter control plane.
- Provider-neutral domain and capability model for v0.1.
- Backend-owned provider facade/adapter boundaries.
- Member-safe capability manifest with no provider internals.
- Guided admin setup assistant and readiness dashboard.
- Provider switch contract: plan, preflight, portable export/import, cutover, rollback/recovery, and support-safe audit evidence.
- Invite/SSO/passkey member join flow after admin setup.
- Admin/operator support-safe evidence and repair guidance.
- Accessibility, supportability, auditability, and deployability as release blockers.

### Out of scope

- Weaver/AI runtime behavior.
- Public third-party adapter marketplace or uncontrolled runtime plugin installation.
- Full automated cross-provider migration for every domain in v0.1.
- Normal-member provider configuration, provider diagnostics, endpoint management, or status cockpit.
- Live production provider migration or release/tag/publish action without explicit release evidence and signoff.
- Selecting one provider stack as Weave's product model.

### Non-negotiable constraints

- Weave remains product-first and provider-neutral.
- No provider vendor lock-in.
- Domain models must fit their domain; do not force all domains into one unsuitable model.
- Normal members must not be confronted with admin/provider burden.
- No raw provider secrets, endpoints, diagnostic payloads, or provider-specific repair instructions leak to normal members.
- Irreversible admin actions require preview, preflight, clear consequences, rollback/recovery, and understandable errors.
- Self-hosted infrastructure remains the recommended sovereignty path; managed/external providers are choices behind Weave contracts, not the product model.

## v0.1 domains and capability states

### Required v0.1 domains

- IDM/RBAC
- Chat/Channels
- Files/Docs
- Boards/Tasks
- Calendar/Events
- Meetings
- Forms/Contacts

### Cross-cutting admin capability

- Health/Readiness: admin/operator-only setup, readiness, repair, evidence, and migration state across all domains.

### Capability vocabulary

Weave uses this stable vocabulary for member-safe capability manifests and admin/operator readiness correlation:

- `available`: capability can be used now.
- `disabled_by_policy`: organization policy or RBAC disables it for this user/scope.
- `not_configured`: no provider/adapter is configured for the domain.
- `degraded`: partially working; admin/operator action may be required.
- `unavailable`: configured capability cannot currently be used.
- `coming_later`: intentionally outside current release scope.

## User/admin/operator stories

### US1 - Admin embeds an organization through provider-neutral domains (Priority: P1)

**Actor**: Organization admin  
**Story**: As an admin, I can choose, validate, bind, unbind, and detach providers/adapters per Weave domain from one Admin-Suite.  
**Why now**: Provider-neutrality is not credible unless admins can operate domains without changing the member product model.  
**Independent test**: Admin readiness output lists configured domains, missing domains, degraded domains, and next actions without leaking secrets or raw provider payloads.

**Acceptance scenarios**:

1. Given an admin configures IDM/RBAC, Chat/Channels, Files/Docs, Boards/Tasks, Calendar/Events, Meetings, and Forms/Contacts, when readiness is reviewed, then Weave reports domain readiness using provider-neutral language.
2. Given a provider is missing or degraded, when the admin opens readiness, then Weave shows action-oriented repair state and support-safe evidence.
3. Given a normal member opens Weave, when a domain is not configured or degraded, then the member sees only stable capability state and humane product copy, not provider setup or diagnostics.

### US2 - Member joins a configured organization without provider burden (Priority: P1)

**Actor**: Normal member  
**Story**: As a member, I can follow an invite or organization URL, complete SSO/passkey authentication, and land in my assigned organization/workspaces with capabilities already resolved.  
**Why now**: Weave's provider-neutral promise fails if members must understand OIDC, endpoints, provider health, or admin setup.  
**Independent test**: A member onboarding scenario proves invite/SSO/passkey entry and stable capability display with no provider setup affordance.

**Acceptance scenarios**:

1. Given an admin has configured organization providers and policy, when a member follows an invite, then the member completes authentication and sees assigned teams/workspaces/capabilities.
2. Given a capability is disabled by policy, when the member reaches that surface, then Weave explains the product state without exposing provider internals.
3. Given a provider switch is in progress, when the member uses unaffected surfaces, then Weave preserves stable member-facing capability language.

### US3 - Admin switches providers safely (Priority: P1)

**Actor**: Organization admin/operator  
**Story**: As an admin/operator, I can plan a provider switch, run preflight, understand data portability, execute cutover, and recover or rollback if needed.  
**Why now**: Provider neutrality requires a credible path away from a provider, not just a different logo in settings.  
**Independent test**: A provider switch contract proves planned steps, export/import manifest, cutover state, rollback/recovery guidance, and support-safe audit evidence.

**Acceptance scenarios**:

1. Given an admin starts a provider switch, when preflight runs, then Weave shows what will move, what will not, risks, required permissions, and rollback path before any irreversible action.
2. Given v0.1 supports portable export/import rather than full automation, when a domain switch is planned, then Weave produces or references a domain export/import contract and evidence manifest.
3. Given cutover fails, when recovery is needed, then Weave provides support-safe status, audit trail, and rollback/recovery guidance without leaking secrets.

### US4 - Backend and Admin-Suite own provider facades and evidence (Priority: P2)

**Actor**: Backend/admin/operator  
**Story**: As the product platform, Weave keeps provider-specific details behind backend facades and Admin-Suite workflows while exposing stable capability manifests to clients.  
**Why now**: This is the architectural seam that prevents provider lock-in and member-client leakage.  
**Independent test**: Server/admin contracts prove raw provider errors, endpoints, secrets, and diagnostics are not returned to member clients.

**Acceptance scenarios**:

1. Given member client requests organization capabilities, when the backend responds, then the manifest contains only provider-agnostic domains and capability states.
2. Given admin/operator requests readiness evidence, when the backend responds, then evidence is actionable and redacted.
3. Given a provider adapter package is enabled or replaced, when validation fails, then Admin-Suite blocks activation and explains recovery.

## Functional requirements

- **FR-001**: Weave MUST model organization product capabilities by provider-neutral domains, not vendor-specific surfaces.
- **FR-002**: Weave MUST provide a first-class Admin-Suite for provider/adapter bind, unbind, readiness, switch, and detach workflows.
- **FR-003**: Weave MUST keep provider reachability, diagnostics, repair, endpoint configuration, secrets, and raw provider errors admin/operator-only.
- **FR-004**: Weave MUST expose member-safe capability manifests using `available`, `disabled_by_policy`, `not_configured`, `degraded`, `unavailable`, and `coming_later`.
- **FR-005**: Weave MUST let members join configured organizations through invite/SSO/passkey without raw provider setup.
- **FR-006**: Weave MUST define v0.1 portable export/import contracts for provider switching before claiming full automated provider migration.
- **FR-007**: Weave MUST require preflight, preview, clear consequences, rollback/recovery, and understandable errors for irreversible admin actions.
- **FR-008**: Weave MUST keep Weaver/AI runtime out of WEAVE-SPEC-0001 acceptance and implementation.
- **FR-009**: Weave MUST support validated adapter packages with safe rollback rather than uncontrolled arbitrary runtime plugins.
- **FR-010**: Weave MUST produce support-safe audit/evidence for provider readiness and switching without secrets/raw provider payloads.
- **FR-011**: Each Weave product domain MUST have exactly one active adapter/provider binding at a time; additional providers may only be candidates, discovery/read-only sources, migration sources or targets, coexistence/preflight evidence, deprecated, or superseded.
- **FR-012**: Every provider adapter MUST declare an `AdapterMapper` that maps provider objects, capabilities, permissions, events, and errors to canonical Weave contracts with provenance, loss, permission-impact, conflict, portability-manifest, and support-safe audit references.
- **FR-013**: Member clients and app-store surfaces MUST remain provider-agnostic; organization setup, provider choice, endpoint/secret handling, diagnostics, policy, whitelisting, and migration planning belong to the Control/Admin/Operator plane.

## Domain model and contracts

Canonical entities:

- Organization
- Workspace
- Member
- Admin
- Operator
- ProviderDomain
- ProviderAdapter
- AdapterPackage
- CapabilityManifest
- CapabilityState
- ReadinessState
- PolicyProfile
- SwitchPlan
- ExportImportManifest
- CutoverRecord
- RollbackPlan
- SupportEvidence
- AuditEvent

Provider domains:

- `idm-rbac`
- `chat-channels`
- `files-docs`
- `boards-tasks`
- `calendar-events`
- `meetings`
- `forms-contacts`

Admin/readiness domains:

- `health-readiness`
- `provider-switch`
- `support-evidence`

Implementation issue DAG:

- #386 — provider-neutral domain and capability model (sequential root)
- #387 — Admin-Suite readiness and setup UX contract (depends on #386)
- #388 — provider switch and portable export/import contract (depends on #386 and #387)
- #389 — acceptance and evidence mapping (parallel after #386 contract shape)

## Acceptance and evidence mapping

Initial acceptance work is tracked in #389. Implementation PRs must map product-language scenarios to stable executable evidence before claiming release readiness.

Required scenario families:

- Member joins configured organization through invite/SSO/passkey and sees stable capabilities.
- Admin configures domains through the guided setup assistant and reviews per-domain readiness in support-safe language.
- Admin plans provider switch with preflight/export-import/cutover/rollback evidence.
- Backend/member capability manifest proves provider-agnostic capability states.
- Weaver/AI runtime remains out of scope and cannot be accidentally implied as shipped.

Required gates for this spec baseline:

- `./gradlew specContract`
- `./gradlew specContractTest`
- `./gradlew acceptanceContract`

Implementation PRs add the smallest relevant area gate, for example `serverCi`, `clientCi`, `adminCi`, `infraStatic`, or `ci` depending on changed files.

## Release and migration impact

- Member impact: simpler join flow and stable product capability language; no provider/admin burden.
- Admin/operator impact: Admin-Suite becomes the product surface for provider setup, readiness, switching, and recovery.
- Developer/API impact: backend/admin contracts must preserve provider neutrality and member-safe capability manifests.
- Data migration/backfill: v0.1 defines portable export/import contracts and switch evidence; full automation is later work.
- Rollback/reversibility: spec baseline can be reverted; implementation PRs need per-area rollback plans.
- Release-notes label expected: `release-notes-skip` for spec-only PRs; `release-notes-feature` for product behavior changes.

## Closed questions

- First product-core slice: Admin-Suite plus provider-neutral domain/capability model.
- First-spec surface: provider-neutral suite contract across Member + Admin + Operator, with Admin-Suite first-class and member surface provider-agnostic.
- Weaver scope: out of scope for WEAVE-SPEC-0001; no runtime profile generation acceptance here.
- Mandatory v0.1 domains: IDM/RBAC, Chat/Channels, Files/Docs, Boards/Tasks, Calendar/Events, Meetings, Forms/Contacts; Health/Readiness is cross-cutting admin capability.
- Minimal capability vocabulary: `available`, `disabled_by_policy`, `not_configured`, `degraded`, `unavailable`, `coming_later`.
