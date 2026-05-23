# Weave product-flow activity diagrams

These diagrams are presentation-ready product flows for the North-Star MVP evidence layer. They intentionally describe Weave-owned user journeys rather than raw Matrix, Nextcloud, OpenProject, or operator-internal workflows.

Executable scenario anchors:

- Frontend Live Stack: `e2e/features/live_stack_app.feature`
- Backend Cucumber: `server/src/test/resources/features/openproject-boards-workspace.feature`
- Infra Live Gates: `infra/weave-workspace/openproject-boards-live-e2e.sh`, operator checks, support-bundle redaction tests, and restore-smoke checks

Source/quality-check anchors:

- Identity/profile source of truth: `specs/03-identity-and-unified-user-profile.md:73` and `/api/me`.
- CI/smoke/E2E source of truth: `specs/10-ci-smoke-and-e2e-contract.md:59`.
- Spec-map guard source: `/tool/spec_map.dart` in the repository that owns the checked mapping.
- `/admin/protocol` is not a current Weave product route here; treat it only as shorthand for raw admin/protocol fallback surfaces unless a future spec defines the endpoint.
- `/storage/power` is not a current Weave product route here; treat it only as shorthand for the manual storage/power budget gate unless a future spec defines the endpoint.

## 1. Sign-in and workspace shell

Scenario anchor: `@weave-live-auth-shell`

```mermaid
flowchart TD
  A[Open Weave app] --> B[Load platform config from canonical API]
  B --> C{Existing valid session?}
  C -- yes --> F[Restore workspace shell]
  C -- no --> D[Start Keycloak OIDC sign-in]
  D --> E{OIDC callback valid?}
  E -- no --> X[Show accessible sign-in error and retry]
  E -- yes --> F
  F --> G[Load /api/me profile facade]
  G --> H[Show workspace navigation, status, profile, modules]
  H --> I[User can enter Chat, Files, Calendar, and Boards surfaces]
```

## 2. Workspace, team, and channel context

Scenario anchors: `@weave-live-calendar-threadrefs`, `@backend-openproject-context-space-gate`, `@infra-openproject-context-gate`

```mermaid
flowchart TD
  A[Authenticated principal] --> B[Resolve workspace membership]
  B --> C[Project Weave Context/Space graph]
  C --> D[Workspace context]
  D --> E[Team context]
  E --> F[Channel context]
  F --> G{Requested module action allowed?}
  G -- yes --> H[Return context-scoped product data]
  G -- no --> I[Fail closed with support-safe denial]
  H --> J[Calendar event scope, chat room, file area, or board snapshot]
  I --> K[No provider IDs, raw URLs, or secrets exposed]
```

## 3. Matrix chat and E2EE posture/recovery boundary

Scenario anchor: `@weave-live-matrix-e2ee`

```mermaid
flowchart TD
  A[User opens Weave Chat] --> B[Read Matrix homeserver from platform config]
  B --> C[Use Matrix/MAS client auth boundary]
  C --> D[Load room list and selected room timeline]
  D --> E[Send and read message through Weave chat UI]
  E --> F{Room encrypted and validated?}
  F -- no --> G[Show honest E2EE unavailable/gated posture]
  F -- yes --> H[Assert encrypted wire event and no plaintext leakage]
  H --> I{Recovery and verification validated?}
  I -- no --> J[Keep recovery claim gated; expose diagnostics only]
  I -- yes --> K[Allow E2EE-ready user guidance]
  G --> L[Backend may use support-safe metadata only]
  J --> L
  K --> L
```

## 4. Files product boundary

Scenario anchor: `@weave-live-files-boundary`

```mermaid
flowchart TD
  A[User opens Weave Files] --> B[Frontend calls backend files facade]
  B --> C[Backend authorizes workspace/user context]
  C --> D[Backend talks to Nextcloud WebDAV/OCS]
  D --> E[Normalize file entries and errors]
  E --> F[Show Weave file list]
  F --> G[Upload unique test file]
  G --> H[Download/open through Weave facade]
  H --> I[Cleanup via facade]
  D -. forbidden .-> X[Do not scrape raw Nextcloud UI or expose credentials]
```

## 5. Calendar and meeting threads

Scenario anchor: `@weave-live-calendar-threadrefs`

```mermaid
flowchart TD
  A[User opens Weave Calendar] --> B[Load workspace/team/channel scopes]
  B --> C[User selects team or channel scope]
  C --> D[Create scoped event through backend facade]
  D --> E[Backend maps to shared CalDAV backing store]
  E --> F[Return scope metadata and meetingThreadId]
  F --> G[Read event after write]
  G --> H[Update event]
  H --> I{Meeting thread ref stable?}
  I -- yes --> J[Show event connected to channel context]
  I -- no --> X[Fail E2E evidence]
  J --> K[Delete test event and verify cleanup]
```

## 6. Boards user writes, fail-closed provider boundary, and context gate

Scenario anchors: `@weave-v01-board-write-audit`, `@weave-live-boards-workspace-nondrag`, `@backend-openproject-disabled-fail-closed`, `@backend-openproject-context-space-gate`, `@infra-openproject-enabled-workspace`

```mermaid
flowchart TD
  A[User opens Boards] --> B[Frontend calls Weave Boards facade]
  B --> C{Boards runtime enabled?}
  C -- no --> D[Fail closed: boards-provider_unavailable]
  C -- yes --> E{Context/Space authorization allows board work?}
  E -- no --> H[Fail closed: boards-forbidden; do not contact or expose provider data]
  E -- yes --> F[Create, move, update, comment, or decision-link task]
  F --> G{Audit record can be written?}
  G -- no --> X[Fail closed before provider mutation]
  G -- yes --> I[Backend-held credentials mutate provider]
  I --> J[Map provider response to Weave boards/columns/tasks]
  J --> K[Return support-safe task result and sync metadata]
  K --> L[Non-drag actions remain first-class]
  K --> M[Team/agent actions stay refused for v0.1]
```

## 7. Support, redaction, and refusal gates

Scenario anchors: `@backend-openproject-support-safe-metadata`, `@backend-openproject-write-refusals`, `@infra-support-bundle-redaction`, `@infra-reset-guardrails`

```mermaid
flowchart TD
  A[Failure, diagnostics, or operator request] --> B{Action type}
  B -- support bundle --> C[Collect public config and logs]
  C --> D[Redact tokens, passwords, signing secrets, provider credentials]
  D --> E{Secret pattern remains?}
  E -- yes --> X[Fail support-safety gate]
  E -- no --> F[Emit support-safe bundle]
  B -- provider action --> G{Audit/consent promotion exists?}
  G -- no --> H[Refuse unsafe provider or agent automation]
  G -- yes --> I[Authorized user write path]
  B -- destructive reset --> J{Typed confirmation and backup expectations satisfied?}
  J -- no --> K[Preserve identity, Matrix, Nextcloud, and generated secrets]
  J -- yes --> L[Run explicit destructive path]
```
