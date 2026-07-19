# Weave product line and Weaver target plan

Positioning line: **Weave – Collaboration Seamlessly Woven with Agentic AI, Activated on Your Terms.**

Status: active implementation direction, 2026-07-19. Canonical product and domain truth lives in
the sibling `weave-specs` repository. This document is the implementation projection for the
`weave` monorepo; it cannot override an accepted corpus contract.

## Decision lock

Weave is product-first, provider-neutral for collaboration providers, and explicit about its
authorities:

- Keycloak is the mandatory organization identity and OAuth backbone. Entra ID, Auth0,
  Authentik, SAML, LDAP, and other enterprise sources connect through Keycloak federation or
  brokering; they do not replace the Weave identity authority at runtime.
- `weave/server` owns product APIs, domain authorization, policy, audit, canonical mappings,
  readiness, and provider anti-corruption layers.
- Flutter owns member UX. The Admin Console owns organization setup, policy, provider readiness,
  and diagnostics.
- Chat uses Matrix Client-Server; Files uses WebDAV; Calendar uses CalDAV/iCalendar; Calls uses
  Matrix v1.19 plus the exact pinned MatrixRTC Profile 0; Agents use MCP; OpenAPI/REST is the
  control plane.
- Calls is Core. Boards/Tasks is Expansion. Both remain evidence-gated.
- Weaver is an optional governed runtime layer after identity, product-domain policy, and
  provider readiness. A runtime profile, model decision, or approval never authorizes a domain
  side effect by itself.
- WCAG 2.2 AA and the applicable EN 301 549 requirements are release baselines.

No backward-compatibility layer is retained for an explicitly retired target contract. Removal
still requires a recovery backup, inventory, reset plan, and post-reset evidence when dogfood
state could otherwise be lost.

## Status quo and target state

| Area | Implemented repository truth | Target/readiness gate |
| --- | --- | --- |
| Identity | Keycloak-backed member login, organization policy, and identity administration exist | Dedicated workload clients, exact audience/scope enforcement, rotation, and two-principal evidence must stay green |
| Files | Weave WebDAV facade and canonical Files services | Live provider, portability, native-client, and recovery evidence |
| Calendar | Weave CalDAV/iCalendar facade and canonical calendar services | Full recurrence, shared-scope, native-client, and recovery evidence |
| Chat | Matrix facade and client-owned Rust/Ruma encryption boundary | Live two-device, recovery, revocation, accessibility, and provider-switch evidence |
| Calls | Proprietary Calls REST/events/models are removed; strict MatrixRTC Profile 0 is the only contract | MAS, RTC Authorizer, third-party interop, media E2EE, TURN/reconnect, physical-device, consent, accessibility, and operations remain `Guarded` |
| Boards/Tasks | Provider-neutral contracts and adapter seams | Expansion runtime enablement requires named product and provider evidence |
| MCP | Spring AI semantic domain tools | Keycloak token exchange is bounded; full MCP OAuth stays `Guarded` until RFC 8707 Resource Indicator consumption is proven |
| Weaver | Thin-fork policy and fail-closed runtime projection are being established | Cell orchestration, four external authorities, signed profile verification, recovery, and integrated E2E are not yet release-ready |

The product remains in active dogfood and does not claim public production readiness.

## Product line

### Provider-neutral suite

The member product surfaces remain Weave-owned:

- Home and activity;
- personal messages, Spaces, channels, and chat;
- Files and documents;
- workspace, team, and channel calendars;
- Calls and meeting context;
- Decisions and evidence;
- Boards/Tasks when the Expansion capability is enabled;
- Help, profile, settings, and support-safe capability state.

Provider choice is risk-aware, not prohibition-based. Matrix/Synapse, Nextcloud, OpenProject,
LiveKit, Teams, Slack, SharePoint, and future adapters are southbound choices. They may change
without changing member vocabulary, public canonical identifiers, or authorization semantics.
Migration is never advertised as lossless without reconciliation evidence.

Workspace/Admin Health and setup language names identity/IDM, chat, files, calendar,
boards/tasks, meetings/calls, documents/collaboration, and Weaver as first-class categories.
The dogfood topology maps Keycloak as the mandatory IDM backbone, Matrix as Chat, Nextcloud as
Files and Calendar backing, OpenProject as the Boards/Tasks candidate, and LiveKit as the first
Calls transport. Those systems do not become northbound product contracts.

### Weave Client and Organization/Admin Console split

The Weave Client owns member work surfaces and the primary profile-editing experience. A member
opens an organization URL, invite, or deep link, completes sign-in, consumes a support-safe
organization manifest, and sees only effective capability states.

The Organization/Admin Console owns organization bootstrap, Keycloak lifecycle administration,
provider choice, policy, readiness, diagnostics, whitelisting, credential references,
backup/restore status, and migration evidence. Normal members never configure provider URLs,
client secrets, service accounts, raw diagnostics, or operator topology.

The handoff is:

```text
organization URL/invite -> Keycloak -> support-safe organization manifest -> member surfaces
```

### Provider and domain boundaries

Every domain follows clean architecture:

```text
northbound protocol or product UI
  -> application use case
    -> canonical domain values and policy
      -> southbound provider port
        -> provider adapter / anti-corruption layer
```

Delivery code cannot become domain authority. Provider SDK objects, IDs, URLs, credentials,
errors, and payloads stop at adapter boundaries. Capability profiles are deny-by-default.

## Keycloak identity and workload model

Keycloak issues and validates the identities used across the product. The accepted workload
matrix separates public member clients, resource servers, lifecycle workloads, identity
administration, MAS, and the MCP workload.

For a member-domain MCP action:

1. the runtime presents a short-lived member token for the exact MCP resource;
2. `weave-mcp-server` validates issuer, subject, organization, audience, scope, expiry, and
   RuntimeProfile binding;
3. the confidential `weave-mcp-server` client performs Standard Token Exchange V2 for the exact
   Weave Server audience and reduced tool scope;
4. Weave Server requires the effective member `sub` and the allowed workload `azp`, then repeats
   current domain authorization;
5. ActionEvidence records the human principal and workload principal separately.

The incoming token is never relayed. A service-account/client-credentials token cannot act as a
member. The `weaver-runtime` workload may call only Agent Runtime Control scopes. The
`weave-identity-admin` workload may call only the required Keycloak lifecycle operations.
`matrix-mas` receives no Weave domain, MCP, runtime-control, or identity-administration rights.

Confidential clients prefer `private_key_jwt` or mTLS. A local Docker deployment may use a
rotated SecretRef-backed client secret. Credential values never enter source, RuntimeProfile,
logs, audit, support bundles, or MCP results.

## Calls: strict MatrixRTC cutover

The only member signaling shape is Matrix v1.19 plus `weave.matrixrtc/profile-0`. MAS exposes
Matrix Native OAuth with Keycloak upstream. Matrix OAuth tokens, OIDC ID tokens, Matrix OpenID
credentials, and RTC transport tokens are not interchangeable.

The RTC Authorizer treats Matrix OpenID as identity input only. It independently validates the
current Matrix user, room, slot/member, device, role, organization policy, nonce, audience, and
expiry before issuing short-lived transport access. LiveKit is the first replaceable SFU; it is
not a Weave member API.

The removed `/api/calls`, `/api/weave/calls`, `com.weave.call.*`, proprietary join models, old
MSC shapes, unstable aliases, and compatibility readers do not remain as fallbacks. Private
Calls require MatrixRTC media E2EE; DTLS-SRTP alone is not claimed as E2EE. Recording and
transcription stay off without explicit consent, retention, and a declared decrypting boundary.

## IDM/RBAC capability profiles and whitelisting

Capability profiles are deny-by-default. Keycloak roles/groups and current Weave policy map to
category-level capabilities before provider access or runtime wake-up. Member states remain
support-safe: `available`, `disabled_by_policy`, `not_configured`, `degraded`, `unavailable`,
`coming_later`, or `unsupported` where the owning contract permits it.

Weaver remains disabled by default until the organization enables its runtime policy for an
entitled user or group. Entitlement creates no product-domain permission; it only allows Agent
Runtime Control to evaluate whether a runtime may exist.

## Governed Weaver runtime integration

Weaver is a thin, policy-constrained fork of a signed OpenClaw release. OpenClaw owns the model
tool-call loop, Matrix channel, approval interaction, sessions, and runtime behavior. Weave owns
entitlement, RuntimeProfile issuance, cell lifecycle, credential references, domain tools,
authorization, and ActionEvidence. Generated Weaver/OpenClaw config is implementation output
from Weave policy, never a second source of authority.

Cells are disposable and keep zero durable local bytes. Runtime adapters are portable:

- Docker is the local/dev adapter;
- Kubernetes with a sandboxed RuntimeClass such as gVisor is the production target;
- the cell filesystem is ephemeral and can be destroyed after every stop.

Exactly four external authorities remain separate:

| Authority | Owns | Must not own |
| --- | --- | --- |
| WebDAV workspace | User-editable workspace files and revisions | Runtime sessions, secrets, lifecycle leases |
| Agent Runtime Control Store | entitlement, desired/observed state, leases/fencing, profiles, wake dedupe, audit correlations | OpenClaw session content or provider secrets |
| Encrypted RuntimeStateStore | runtime checkpoints/session state with a 30-day default retention | policy authority or plaintext secrets |
| Secret Manager/KMS | credential material and encryption keys | member workspace content or runtime policy |

A signed RuntimeProfile is verified before projection. Missing verifier, bad signature, wrong
issuer/audience, expiry, replay, cross-user reference, unsafe path, unknown key, or policy drift
fails closed. Projected OpenClaw configuration is ephemeral and is removed with the cell.

OpenClaw approvals remain user-interaction state. They do not replace fresh Keycloak identity,
the authenticated MCP workload, organization policy, object scope, tool/argument validation,
expiry, revocation, or provider-side checks at execution time.

## DevOps delivery plan

### P0 — contract and identity spine

- merge and pin the canonical workload, ActionEvidence, RuntimeProfile, capability-state, Calls,
  accessibility, and strict-retirement contracts;
- provision the dedicated Keycloak clients, audiences, scopes, exchange permissions, SecretRefs,
  rotation, and negative tests;
- remove static boundary tokens and bearer relay;
- publish protected-resource metadata while keeping RFC 8707 readiness guarded.

### P0 — Calls foundation

- deploy/configure MAS with Keycloak upstream;
- implement the exact MatrixRTC Profile 0 wire module in the shared Rust/Ruma boundary;
- deploy the RTC Authorizer, LiveKit transport, TURN, replay/rate/abuse protections, and redacted
  diagnostics;
- prove media E2EE, reconnect, consent, accessibility, physical-device behavior, and recovery.

### P0 — Weaver lifecycle

- keep the fork on a verified signed OpenClaw tag and enforce a small fork budget;
- implement RuntimeProfile signing/key discovery and the fail-closed projector;
- implement Control Store leases/fencing, wake dedupe, workspace revision materialization,
  RuntimeStateStore checkpoint/restore, Secret Manager/KMS, and cross-node recovery;
- package the launcher/policy into the downstream image and select sandboxed RuntimeClass in
  production.

### P1 — integrated evidence

- add cross-repository contract conformance and a live Keycloak -> MCP -> Server negative and
  positive lane;
- add MAS/MatrixRTC/RTC-authorizer interoperability and physical-device E2E;
- add disposable-cell restart, cross-node recovery, backup/restore, support bundle, and zero-byte
  residue evidence;
- repair private-repository provenance attestation without weakening other release gates.

## Issue and PR hygiene

Continue only work aligned to this target. Fold duplicate Python/OpenAPI MCP experiments into
the Spring AI/OIDC removal issue. Close stale custom Calls, static-token, duplicated approval,
custom channel, persistent-cell, and broad fork PRs after replacement evidence exists. Empty
historical milestones should be closed; new issues should be bounded by one owner and one
executable gate.

Delivery order:

1. canonical specs;
2. Keycloak/workload and topology contracts;
3. backend authorization and readiness;
4. client protocol consumption and accessibility;
5. Weaver lifecycle adapters;
6. integrated E2E, recovery, and release evidence.

## Claim boundary

The portability promise is no unaccounted data loss; perfect lossless migration is not claimed.
No green smoke test proves E2E behavior. No checked-in contract proves a live provider. No signed
profile proves runtime isolation. No approval proves domain authorization. Each release claim
must end in named, support-safe executable evidence.
