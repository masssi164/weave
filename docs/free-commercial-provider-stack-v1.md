# ADR: Free Commercial Provider Stack v1

Status: proposed architecture slice for [#231](https://github.com/masssi164/weave/issues/231)  
Date: 2026-05-22  
Scope: `weave`, `weave-backend`, `weave-infra`

## Decision

Weave's first provider stack must be self-hostable, commercially usable without paid-license dependencies, and hidden behind Weave product facades. Weave remains the product UX: Flutter may perform OIDC/PKCE login and Matrix client-protocol calls, but normal product operations after sign-in go through `weave-backend`.

`weave-backend` is the provider facade for auth policy, provider credentials, ID mapping, redaction, support-safe errors, pagination/cursors, readiness, and feature capability discovery. Provider APIs, provider tokens, raw upstream URLs with sensitive context, app passwords, and raw provider errors must not reach Flutter.

Terraform remains infrastructure bootstrap. Product-level provider setup, especially Keycloak realm shape, should move toward backend/provisioner `dry-run -> diff -> gated apply` contracts instead of embedding product logic directly in Terraform HCL.

## Non-negotiable boundaries

- No paid-only feature is required for the MVP provider stack.
- Provider-specific UIs are not normal Weave product surfaces.
- Provider-specific IDs can be retained as backend support/export metadata, but not as primary Flutter domain identity.
- Flutter must not call Nextcloud, GitLab, Forgejo, OpenProject, ONLYOFFICE, Collabora, Woodpecker, or provider admin APIs directly for product flows.
- Matrix is the scoped direct-protocol exception because the chat client needs Matrix client semantics and E2EE/device behavior; backend still owns Matrix configuration, provisioning/status, and support-safe diagnostics where needed.
- Collabora is not the default office provider. It remains non-default/licensing-risk until a later legal/product review explicitly changes the decision.

## Provider capability matrix

| Provider | Capability | License | Commercially usable without paid dependency | Paid-feature risk | API maturity | Backend facade port | Default / alternative status | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Keycloak | Identity, SSO, realms, clients, roles, scopes, claims | Apache-2.0 | Yes | Low; commercial support exists but is not required | Mature admin APIs, OIDC/SAML standards | `IdentityRealmProvider` | Default | Keycloak stays auth/identity authority. Product realm desired state should be represented as dry-run/diff/apply, with destructive apply unavailable by default. |
| Matrix Synapse + Matrix Authentication Service | Chat protocol, E2EE-capable homeserver/auth service | Apache-2.0 | Yes | Low for Weave-hosted core chat; hosted/vendor features not required | Mature Matrix client-server APIs; MAS/OIDC path is specialized | Matrix platform config/status/provisioning seam; not a generic chat facade | Default chat provider | Flutter may call Matrix client protocol directly. Backend support diagnostics must not claim server access to encrypted message bodies. |
| Nextcloud Files / WebDAV / OCS | Files, storage, quota, internal sharing | AGPL-3.0-or-later | Yes, subject to AGPL obligations | Low for MVP files; advanced enterprise features are not required | Mature WebDAV/OCS surfaces | `FilesProvider` | Default | Product files use backend facade; raw Nextcloud UI is fallback/admin/protocol surface only. |
| ONLYOFFICE Docs Community | Office document view/edit | AGPL-3.0 | Yes, subject to AGPL/trademark/deployment obligations and community-edition limits | Medium; commercial editions add scale/support/features, but MVP must not require them | Mature document-server integration APIs; exact Weave launch route still to decide | `OfficeProvider` | Default office candidate | Prefer as Office default. First route can be Nextcloud ONLYOFFICE app or backend-mediated launch, but must fail closed until configured. |
| Collabora Online / CODE | Office document view/edit | Mixed/open-source project licensing; product/edition terms need review | Not accepted for default until reviewed | High/unknown for current requirements | Mature WOPI/Nextcloud integration, but product-use posture needs review | `OfficeProvider` | Non-default, licensing-risk alternative | Do not make Collabora a default or required dependency without later legal/product decision. |
| Nextcloud Calendar / CalDAV | Workspace/team/channel calendar backing store | AGPL-3.0-or-later | Yes, subject to AGPL obligations | Low for MVP shared calendars; enterprise groupware extras not required | Mature CalDAV; app-specific behavior must be tested | `CalendarProvider` | Default | Backend facade must expose Weave scopes, not private personal calendar templates. |
| Nextcloud Contacts / CardDAV | Contacts/address books | AGPL-3.0-or-later | Yes, subject to AGPL obligations | Low for basic search/summary | Mature CardDAV; app API details require spike | `ContactsProvider` | Default contacts candidate | Initial scope should be read/search/summary behind backend redaction. |
| Nextcloud Forms | Forms, submissions, exports | AGPL-3.0-or-later | Yes, subject to AGPL obligations | Low/medium; app API stability and export surface must be verified | REST API exists; version/compatibility must be pinned by adapter tests | `FormsProvider` | Default forms candidate | Flutter must not call Forms API directly or receive raw submission/provider errors. |
| OpenProject Community | Boards / project management / work packages | GPL-3.0 | Yes, subject to GPL obligations | Medium; Enterprise add-ons must not be assumed | Mature REST/OpenAPI surface | `BoardsProvider` | Preferred Boards/PM provider | Preferred for stronger Boards/PM and read-sync provider proof. Provider writes remain gated by authz/audit/consent promotion. |
| Nextcloud Deck | Lightweight boards bridge/import | AGPL-3.0-or-later | Yes, subject to AGPL obligations | Low for bridge scope; not enough to define product model | App REST API exists; sync/event behavior weaker than OpenProject | `BoardsProvider` | Optional bridge/fallback | Deck may be bridge/import/fallback only; it must not define Weave board vocabulary. |
| GitLab CE/FOSS self-managed | Source control, issues, merge requests, CI, releases | MIT for application code; docs/assets have separate terms | Yes | Medium; Premium/Ultimate features must be excluded from MVP assumptions | Very mature REST/GraphQL APIs, webhooks, CI APIs | `SourceControlProvider`, `IssueTrackerProvider`, `CiProvider`, `ReleaseProvider` | Primary DevOps provider | Primary because UI is familiar/professional and the free self-managed surface is broad. Do not require paid approvals, epics, advanced security/compliance, or paid package features. |
| Forgejo | Source control, pull requests, issues, releases/packages | GPL-3.0-or-later from current v9+ line; older releases were MIT | Yes, subject to GPL obligations | Low; project is community/open-source, but feature breadth differs from GitLab | Mature Gitea-compatible API with Forgejo-specific behavior to verify | `SourceControlProvider`, `IssueTrackerProvider`, `ReleaseProvider` | First-class DevOps alternative | Use for lighter deployments. Keep provider-neutral DTOs so GitLab remains primary but not mandatory. |
| GitLab CI | CI pipelines, jobs, artifacts | Same GitLab CE/FOSS licensing boundary | Yes | Medium; paid CI minutes/hosted SaaS and premium analytics are out of scope | Mature pipeline/job/artifact APIs | `CiProvider` | Primary CI provider with GitLab | Self-managed runners/artifacts only; no dependency on GitLab.com paid SaaS. |
| Woodpecker CI | CI for Forgejo/Gitea-style deployments | Apache-2.0 | Yes | Low; capability breadth must be tested | Mature enough for simple pipeline/status integration | `CiProvider` | Optional CI alternative for Forgejo | Use when Forgejo needs CI without GitLab. Backend maps statuses/log pointers support-safely. |

## Backend facade ports

All ports expose `capabilities()`, `health()`, support-safe error mapping, redaction policy, provider references for backend diagnostics/export, and explicit unsupported-capability responses. All write-capable methods must be authorized through Weave identity/workspace/context policy before provider calls.

### `IdentityRealmProvider`

Owns product-level identity desired state for realms, clients, redirect origins, scopes, roles, groups, claims/mappers, seed/admin user posture, and provider warnings/blockers.

Required first methods:

- `readCurrentRealm()` returns redacted current state.
- `plan(desiredRealmSpec)` returns dry-run validation, diff, warnings, blockers, and required manual steps.
- `apply(planId)` is unavailable by default and later owner/admin-gated; destructive operations require explicit allow flags.

### `FilesProvider`

Owns file list, metadata, upload/download/open, create-folder, delete/move where safe, quota/status, basic internal sharing, provider refs, and WebDAV/OCS error normalization.

### `OfficeProvider`

Owns document open/view/edit launch readiness, supported MIME types, permission and lock/session mapping, callback/session validation, provider health, and fail-closed user guidance when office is not configured.

### `CalendarProvider`

Owns Weave scheduling scopes (`workspace`, `team`, `channel`), event list/read/create/update/delete, timezone handling, channel/event-thread metadata where available, and CalDAV error normalization. It must fail closed for unresolved private-user calendar templates.

### `ContactsProvider`

Owns address-book capability discovery, contact search/summary/detail where permitted, group/address-book scope mapping, CardDAV normalization, and PII redaction for support output.

### `FormsProvider`

Owns form list/detail, publication/submission readiness, submission summary/export where authorized, provider version/API compatibility, and redaction of submitted personal data by default.

### `BoardsProvider`

Owns provider-neutral projects/boards/columns/tasks/comments/attachments, OpenProject read-sync as preferred provider path, Deck bridge/fallback where enabled, non-drag operation support, provider refs, cursors, conflict/sync metadata, and write refusal until audit/consent gates are promoted.

### `SourceControlProvider`

Owns repositories/projects, branches, commits, merge/pull requests, file/tree metadata, webhooks/readiness, provider refs, and support-safe errors for GitLab/Forgejo.

### `CiProvider`

Owns pipelines/workflows, jobs, statuses, logs/artifact pointers, runner readiness, retry/cancel capability flags, and redaction of tokens, secrets, raw logs where unsafe.

### `IssueTrackerProvider`

Owns issue list/detail/status/labels/assignees/comments links for GitLab/Forgejo/OpenProject-backed contexts where configured, with Weave-owned DTOs and no raw provider issue IDs as primary identity.

### `ReleaseProvider`

Owns tags/releases/changelogs/assets/package/artifact pointers where free/community features support them. Paid release governance/approval features must be advertised as unsupported rather than assumed.

## Follow-up issue map

Existing follow-ups already cover provider-specific slices and should not be duplicated:

- [#232](https://github.com/masssi164/weave/issues/232) — GitLab CE primary and Forgejo alternative provider research/contracts.
- [#233](https://github.com/masssi164/weave/issues/233) — Keycloak realm provider dry-run/apply architecture.
- [#234](https://github.com/masssi164/weave/issues/234) — Nextcloud Forms and Contacts through backend facade.
- [#235](https://github.com/masssi164/weave/issues/235) — ONLYOFFICE Community as default Office provider.

New concrete gaps from this ADR:

- [`weave-backend` #102](https://github.com/masssi164/weave-backend/issues/102) — backend provider capability registry and fail-closed facade contract tests across all ports.
- [`weave-infra` #82](https://github.com/masssi164/weave-infra/issues/82) — optional provider-stack profile/runbook that keeps non-core providers off by default and verifies no paid-only dependency is required.
- [`weave` #237](https://github.com/masssi164/weave/issues/237) — frontend provider-readiness/capability status surface that consumes backend-only capability data and never provider APIs directly.

## Acceptance checks for this ADR slice

- The provider matrix names licenses and paid-feature risk for every selected default/alternative.
- No row requires a paid provider feature for MVP.
- Collabora is explicitly non-default/licensing-risk.
- GitLab CE/FOSS is primary DevOps; Forgejo is a first-class alternative.
- ONLYOFFICE Docs Community is the office default candidate.
- Nextcloud owns Files, Calendar, Contacts, Forms, and optionally Deck bridge scope.
- OpenProject remains preferred for stronger Boards/PM.
- Every non-Matrix product capability has a backend facade port.
- No runtime implementation, provider route, secret, or frontend direct-provider path is introduced by this document.
