# Weave product acceptance flows

Status: reviewable product-flow baseline for Massimo; executable evidence is mapped in the acceptance files listed below.

These flows keep the product order explicit: describe the user journey first, then map only the relevant parts to deterministic Cucumber/Gherkin/E2E gates. They are not a broad test zoo.

## General product flow summary

A Weave user signs in once, lands in the workspace shell, chooses the relevant workspace/team/channel context, and then uses Weave-owned surfaces for chat, files, calendar, and boards. Matrix and Nextcloud stay sovereign provider modules behind the Weave experience. Boards are a Weave-owned release surface: provider engines stay behind the backend facade, user writes require authorization and audit, and unsafe provider paths fail closed.

```mermaid
flowchart TD
  Start([Start Weave]) --> Config{Server configuration present?}
  Config -- no --> Setup[Enter canonical Weave/auth/API endpoints]
  Setup --> SignIn
  Config -- yes --> SignIn[Sign in via Keycloak/OIDC]
  SignIn --> Session[Restore backend session]
  Session --> Me[Load identity/profile through /api/me and profile facade]
  Me --> Shell[Open workspace shell]
  Shell --> Context[Select workspace, team, channel, or Context/Space]
  Context --> Chat[Chat surface]
  Context --> Files[Files surface]
  Context --> Calendar[Calendar surface]
  Context --> Boards[Boards surface]
  Chat --> E2EE[Report Matrix E2EE posture and recovery boundary]
  Files --> FilesFacade[Use Weave backend files facade]
  Calendar --> ChannelEvent[Create/read/update/delete channel event]
  Boards --> BoardGate{Boards provider enabled and authorized?}
  BoardGate -- no --> FailClosed[Support-safe fail-closed state]
  BoardGate -- yes --> BoardWork[Create, move, update, comment, or decision-link tasks]
  BoardWork --> Audit[Authorize and audit before provider mutation]
  Audit --> Result[Return support-safe result]
```

## Flow 1: Sign-in and workspace shell boot

Purpose: prove activation and one login restore the Weave product shell and
that the production OIDC session can refresh.

```mermaid
flowchart TD
  A([App boot]) --> B[Load server configuration]
  B --> C[Derive issuer, API, Matrix, Nextcloud endpoints]
  C --> D[Show sign-in affordance]
  D --> E[OIDC sign-in]
  E --> F[System browser returns Authorization Code + PKCE result]
  F --> G[Restore authenticated backend session]
  G --> H[Workspace shell visible]
  H --> I[Refresh session through production AppAuth]
```

Acceptance evidence:
- `e2e/features/live_stack_app.feature` scenario `@weave-live-auth-shell`.
- `e2e/scenario_mappings.json` maps the scenario to `PHYSICAL_AUTH_SESSION_RESULT`.
- `client/integration_test/system_browser_auth_e2e_test.dart` uses the production `WeaveApp`, AppAuth session repository, system browser, workspace route, and refresh path without credential build arguments.
- `./gradlew testApp` separately proves automatic invitation, browser activation, PKCE, WebDAV, workload OAuth, MCP, revocation, and cleanup.
- `client/test/live_stack_feature_mapping_test.dart` prevents the readable scenario from drifting away from executable evidence.

## Flow 2: Workspace/team/channel and Context/Space selection

Purpose: keep collaboration scope explicit before module actions run. Team/channel language is a product projection; provider-backed boards must still resolve through Context/Space authorization.

```mermaid
flowchart TD
  Shell[Workspace shell] --> Select[Select workspace/team/channel]
  Select --> Scope[Resolve product scope]
  Scope --> Module{Target module}
  Module --> CalendarScope[Calendar workspace/team/channel scope]
  Module --> ChatRoom[Matrix room/context]
  Module --> BoardContext[Boards Context/Space]
  BoardContext --> Authz{Context/Space permits read?}
  Authz -- yes --> ProviderRead[Provider read may proceed]
  Authz -- no --> NoContact[Fail closed before provider contact]
```

Acceptance evidence:
- Backend Cucumber `@backend-openproject-context-space-gate` proves missing Context/Space authorization exposes no provider data and does not contact OpenProject.
- Infra scenario `@infra-openproject-context-gate` maps the same expectation to the live Weave API gate.
- Calendar scope coverage is in `@weave-live-calendar-threadrefs`.

## Flow 3: Matrix chat and E2EE posture/recovery boundary

Purpose: prove Weave chat can send/read Matrix messages while being honest about E2EE readiness, encrypted wire evidence, and recovery state.

```mermaid
flowchart TD
  Context[Selected chat context] --> MatrixClient[Create Matrix client from platform config]
  MatrixClient --> PlainRoom[Create/send/read normal room message]
  MatrixClient --> CryptoState[Load crypto/bootstrap state]
  CryptoState --> NeedsBootstrap{Recovery/bootstrap required?}
  NeedsBootstrap -- yes --> Bootstrap[Bootstrap test crypto identity]
  NeedsBootstrap -- no --> EncryptedRoom
  Bootstrap --> EncryptedRoom[Create encrypted room]
  EncryptedRoom --> SendEncrypted[Send encrypted message]
  SendEncrypted --> WireProof[Observe authoritative m.room.encrypted wire event]
  WireProof --> Timeline[Confirm decrypted Weave timeline]
  Timeline --> Report[Report E2EE_RESULT without claiming more than proven]
```

Out of scope for this acceptance layer: claiming complete human device-verification UX, long-term recovery UX, or federation behavior.

## Flow 4: Files product boundary vs raw provider/protocol fallback

Purpose: users use Weave Files, while raw Nextcloud remains admin/protocol fallback only.

```mermaid
flowchart TD
  Files[Open Weave Files] --> BackendFacade[Connect to backend files facade]
  BackendFacade --> List[List directory]
  List --> Upload[Upload file through Weave facade]
  Upload --> Refresh[Refresh Weave listing]
  Refresh --> Download[Download through Weave facade]
  Download --> Cleanup[Delete test file]
  BackendFacade -. admin fallback only .-> Raw[Raw Nextcloud admin/protocol origin]
```

Acceptance evidence:
- `@weave-live-files-boundary` maps to browse/upload/download/delete through the backend facade.
- Raw Nextcloud is not promoted as normal product UX.

## Flow 5: Calendar workspace/team/channel event and meeting-thread reference

Purpose: channel events round-trip through the CalDAV/iCalendar facade and keep a stable canonical meeting-thread reference for later concrete chat/meeting linkage.

```mermaid
flowchart TD
  Calendar[Open Calendar] --> Scopes[Load workspace/team/channel scopes]
  Scopes --> ChannelScope[Choose channel scope]
  ChannelScope --> Create[Create channel event]
  Create --> Read[Read event]
  Read --> ThreadRef[Verify meeting thread reference]
  ThreadRef --> Update[Update event]
  Update --> ThreadStable[Verify thread reference is stable]
  ThreadStable --> Delete[Delete event]
```

Acceptance evidence:
- `@weave-live-calendar-threadrefs` maps to the live Flutter E2E and checks create/read/update/delete plus stable thread reference.

## Flow 6: Boards user writes, authorization, and audit gates

Purpose: keep board work as a Weave product surface while provider engines stay behind backend facades, authorization, audit, support-safe errors, and non-drag accessibility paths.

```mermaid
flowchart TD
  Boards[Open Boards] --> Facade[Call Weave Boards facade]
  Facade --> ProviderGate{Boards runtime configured?}
  ProviderGate -- no --> Disabled[503/support-safe provider unavailable]
  ProviderGate -- yes --> ContextGate{Context/Space permits board work?}
  ContextGate -- no --> Forbidden[Support-safe forbidden; no provider contact]
  ContextGate -- yes --> TaskAction[Create, move, update, comment, or decision-link task]
  TaskAction --> AuditGate{Audit event can be written?}
  AuditGate -- no --> FailClosed[Fail closed before provider mutation]
  AuditGate -- yes --> ProviderMutation[Mutate provider through backend-held credentials]
  ProviderMutation --> Result[Return provider-neutral task result]
```

Acceptance evidence:
- `e2e/features/v0_1_dogfood_release.feature` maps the release requirement to `V01_BOARD_WRITE_AUDIT`.
- Existing OpenProject backend and infra checks remain lower-level provider-boundary evidence; they must not be presented as the v0.1 user-write release surface until the write/audit facade is implemented.
- App-facing non-drag board behavior remains mapped to Flutter evidence so the release path does not depend on drag-and-drop.

Out of scope for v0.1: team/agent board actions, broad background automation, and raw OpenProject UI as Weave product UX.

## Flow 7: Support/operator diagnostics and redaction

Purpose: diagnostics should help operators without leaking secrets or encouraging unsafe live-state mutation.

```mermaid
flowchart TD
  Operator[Operator requests diagnostics] --> Check[Run readiness/operator checks]
  Operator --> Bundle[Create support bundle]
  Check --> Routes[Check product API/auth/Matrix/files origins]
  Check --> ProviderConfig[Check optional provider config support-safely]
  Bundle --> Redact[Redact tokens, passwords, signing keys, provider credentials]
  Redact --> Share[Share support-safe bundle/log summary]
  Operator --> Reset{Destructive reset requested?}
  Reset -- no typed confirmation --> Preserve[Preserve persistent volumes/data]
  Reset -- confirmed + backed up --> Maintenance[Explicit operator maintenance path]
```

Acceptance evidence:
- `infra/weave-workspace/tests/*-test.sh` maps support-bundle redaction, operator checks, restore smoke, and teardown guard behavior to deterministic CI evidence.
- Manual full-stack smoke remains manually dispatched because it has storage/power cost and live-state implications.

## Explicit non-product labels

- `/api/me` is the canonical backend identity/profile contract and is part of the sign-in/shell acceptance flow.
- `/admin/protocol` is not a current Weave product route in this repository; use it only as shorthand for raw admin/protocol fallback surfaces unless a future spec defines an endpoint.
- `/storage/power` is not a current Weave product route; use it only as shorthand for the manual storage/power budget gate unless a future spec defines an endpoint.

## Scenario-to-gate map

| Product area | Readable scenario file | Executable or deterministic gate | Why it is relevant |
| --- | --- | --- | --- |
| Sign-in / shell / profile | `e2e/features/live_stack_app.feature` | Flutter live E2E + `test/live_stack_feature_mapping_test.dart` | One login must restore Weave and `/api/me`/profile facade identity. |
| Matrix chat / E2EE | `e2e/features/live_stack_app.feature` + `e2e/scenario_mappings.json` | Flutter live E2E encrypted wire/timeline proof + acceptance artifact | E2EE posture must be truthful, not decorative. |
| Files boundary | `e2e/features/live_stack_app.feature` + `e2e/scenario_mappings.json` | Flutter live E2E product files proof + acceptance artifact | Files must be Weave product UX, not raw provider UI. |
| Calendar event/thread | `e2e/features/live_stack_app.feature` | Flutter live E2E calendar CRUD + thread reference proof | Channel scheduling needs stable context and future meeting/chat linkage. |
| Boards non-drag UX | `e2e/features/live_stack_app.feature` | Flutter live E2E non-drag operation proof | Boards need accessible non-drag product behavior. |
| Boards provider boundary | `server/src/test/resources/features/openproject-boards-workspace.feature` | Backend Cucumber/JUnit acceptance suite | Provider runtime evidence must stay context-scoped, support-safe, and fail-closed unless user-write/audit promotion is proved. |
| OpenProject live infra | `infra/weave-workspace/openproject-boards-live-e2e.sh` | live script + mapping guard | Live API path is runnable without promoting raw OpenProject UX. |
| v0.1 release spine | `e2e/features/v0_1_dogfood_release.feature` | `client/test/release_1/v0_1_release_spine_contract_test.dart` + release plan fragments | Dogfood-production scope must stay mapped before implementation slices land. |
| Operator/support safety | `infra/weave-workspace/tests/*-test.sh` | support-bundle, operator-check, restore-smoke, teardown guard tests | Diagnostics and reset paths must stay safe by default. |
