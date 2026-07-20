# Weave

<p align="center">
  <img src="client/assets/images/weave_logo.png" alt="Weave logo, an interlaced blue and teal knot" width="180">
</p>

Provider-neutral collaboration for organizations that need control, portability, and governed assistance.

**Weave – Collaboration Seamlessly Woven with Agentic AI, Activated on Your Terms.**

Weave is a provider-neutral collaboration suite and governed workspace for organizations that want collaboration to stay portable, reviewable, and under their own rules.

It gives members one place for chat, files, calendars, tasks, decisions, meetings, help, and workspace context, while giving admins and operators a controlled way to connect providers, review readiness, keep evidence support-safe, and change direction without pretending provider changes are risk-free.

Agent Runtime Control (ARC) follows that product order: it is optional, entitlement-bound, auditable, and fail-closed. Weaver/OpenClaw is its first runtime provider, not an identity or collaboration authority.

## Architecture At A Glance

Weave is an open-standards gateway and product surface, not a branded skin over one provider. The northbound side exposes stable Weave-owned protocols and product APIs to clients. The southbound side adapts replaceable providers behind canonical Weave domains.

| Domain | Northbound member data plane | Canonical Weave boundary | Current dogfood/default southbound |
| --- | --- | --- | --- |
| Chat | Matrix Client-Server-compatible facade at the public API origin under `/_matrix/client/**` | Chat conversations, rooms, messages, decisions, and meeting capsules | Matrix/Synapse-class provider adapters or bridges, with federation disabled by default for MVP |
| Files | WebDAV facade under `/dav/files/**` | Files, folders, download/upload, copy/move, lock state, quota/conflict errors, audit | Nextcloud/WebDAV-class storage adapter |
| Calendar | CalDAV/iCalendar facade under `/caldav/**` | Workspace, team, and channel calendars with stable canonical meeting-thread references plus setup/readiness control plane | Nextcloud/CalDAV-class calendar adapter |
| Identity | OIDC/SAML-compatible organization identity | One login, user profile, roles, policy, audit, support-safe diagnostics | Keycloak by default, adapter-friendly for Entra ID/Auth0/Authentik-style sources |
| Boards/tasks | Weave product/control APIs while protocol parity matures | Provider-neutral task, board, readiness, mapping, authorization, and audit contracts | Local workspace today; OpenProject-class adapters remain gated |
| Calls/meetings | Matrix v1.19 plus the revision-pinned MatrixRTC Profile 0 target | Matrix room, slot, membership, authorization, media-key, consent, and artifact contracts | LiveKit is the first replaceable RTC transport/SFU, not the member contract |
| Agent Runtime Control | Signed RuntimeProfile v2 and an administrative lifecycle API | Entitlement, cell identity, desired state, profile issuance, workload reconciliation, encrypted external state, and audit | `weave/server`; Weaver/OpenClaw is the first runtime consumer |
| Agent tools | Guarded OAuth-protected MCP at `/mcp` using Spring AI stateful Streamable HTTP | ARC-bound workload admission and current backend context are active; domain tools require current authorization and evidence | Keycloak Standard Token Exchange V2; tool/resource/prompt catalogs are still empty |

Spring Boot is the server gatekeeper for OIDC, authorization, audit, readiness, and support-safe errors. The Matrix facade shares the public API origin; a `matrix.<tenant>` host is a southbound provider/operator endpoint, never a member-client setting. Matrix protocol shaping targets a shared Rust/Ruma core: server integration through JNI, Flutter integration through `flutter_rust_bridge`. Flutter consumes Weave-owned facades, not raw provider SDKs or provider secrets.

Matrix encryption is device-owned. The Flutter bridge uses the Apache-2.0 Matrix Rust SDK for encrypted-room state, cross-signing, SAS verification, recovery, and an encrypted SQLite crypto store. Spring and southbound adapters may persist public device keys, opaque encrypted events, to-device envelopes, and room-key backup ciphertext, but never user private keys or decrypted message bodies. Plaintext fallback is rejected for encrypted rooms.

```mermaid
flowchart LR
  clients["Flutter, native DAV clients, and Weaver cells"] --> oidc["Keycloak identity; MAS for Matrix Native OAuth"]
  oidc --> northbound["WebDAV | CalDAV | Matrix Client-Server/MatrixRTC | OAuth-protected MCP"]
  northbound --> domains["Files | Calendar | Chat | Calls | Agent Runtime Control domains"]
  domains --> ports["Provider ports, mappings, conformance, and audit"]
  ports --> providers["Replaceable WebDAV, CalDAV, Matrix, Slack, Teams, S3, and future adapters"]
```

Provider switching happens below the canonical domain boundary. Adapters translate provider identifiers, errors, and capabilities into Weave values; durable mappings and conformance reports preserve continuity. A provider URL, token, SDK type, Matrix homeserver, or Nextcloud endpoint is therefore an implementation detail, never the member contract.

## Enterprise Workflow

1. **Buyer and transformation lead** align the collaboration domains that matter: identity, chat, files, calendar, boards/tasks, meetings, decisions, and governed assistance.
2. **Admin and operator** prepare the organization through one control path: connect provider categories, review readiness, preview policy impact, and keep diagnostics and evidence support-safe before member go-live.
3. **Member** enters through an organization URL, invite link, or deep link, lands in Weave without raw provider setup, and works through stable Weave surfaces for daily collaboration.
4. **Governance and change** stay explicit: provider changes are reviewed through dry-run evidence, approvals, audit, and member-impact boundaries before they become rollout decisions.

## Product Screenshots

These checked-in visuals are support-safe proof assets for the current dogfood path. They show what Weave can demonstrate today for setup, member work, and workspace governance. The evidence manifest is [docs/assets/screenshot-evidence.json](docs/assets/screenshot-evidence.json).

- [Admin setup start](docs/assets/marketing/01-setup-start.svg): guided workspace setup for admins preparing a Weave organization.
- [Service review](docs/assets/marketing/02-review-service-endpoints.svg): support-safe provider endpoint review before setup completion.
- [Chat room](docs/assets/marketing/03-chat-room.svg): member chat with message history, workspace context, and a send action.
- [Files and documents](docs/assets/marketing/04-files-documents.svg): document and folder surface with accessible file actions.
- [Settings](docs/assets/marketing/05-settings.svg): account session controls and configured workspace services.

<p align="center">
  <a href="docs/assets/marketing/01-setup-start.svg"><img src="docs/assets/marketing/01-setup-start.svg" alt="Guided Weave workspace setup screen for admins preparing a workspace." width="420"></a>
  <a href="docs/assets/marketing/03-chat-room.svg"><img src="docs/assets/marketing/03-chat-room.svg" alt="Weave chat room with message history, workspace context, and a send action." width="420"></a>
</p>

## What Works Today

- The current frontdoor proves a provider-neutral member path with guided setup, service review, chat, files, and settings visuals backed by checked-in evidence.
- The current implementation moves normal member data planes to northbound standards: Chat through the OIDC-gated Matrix facade, Files through WebDAV, and Calendar through CalDAV/iCalendar. Legacy REST chat messages and calendar event data-plane routes are obsolete rather than compatibility targets.
- Chat uses the client-owned Rust crypto engine for encrypted sync/send, durable device identity, SAS verification, recovery, and lost-device revocation. A support-safe hash binds each OIDC login session to its first Matrix device so a lost refresh session cannot rename itself after revocation. The OIDC refresh session, Matrix device ID, Keychain-held store passphrase, and encrypted app-support store survive normal force-quit, relaunch, and in-place app updates; only explicit account removal deletes that device state.
- Agent Runtime Control now owns per-cell Keycloak workload identity, signed RuntimeProfile v2 issuance, lifecycle reconciliation, revocation/deletion, encrypted external RuntimeState generations, and separate least-privilege identity-administration credentials. The MCP edge admits only a current bound cell, validates RFC 9068 `at+jwt`, negotiates the MCP Client Credentials extension, exchanges rather than relays the workload token, and revalidates current context in the backend. Human and generic service-account access remains closed.
- The obsolete member-oriented MCP v1 catalog, forwarded-member-token exchange, caller-supplied profile header, fake Scout UI, duplicated approval path, Python/FastMCP runtime, and handwritten JSON-RPC controller are removed without compatibility readers.
- Weave treats admin/operator readiness as part of the product: provider categories, policy boundaries, evidence, and support-safe diagnostics belong in the control plane, not in member setup.
- The release track already carries product-level proof for dogfood collaboration, governed assistance boundaries, portability dry-runs, operator recovery guardrails, and release-claim control.

## What Is Guarded

Weave is in active dogfood and does not claim public production readiness. The portability promise is no unaccounted data loss; perfect lossless migration is not claimed. In particular, universal provider interchangeability is not claimed. Encrypted history remains blocked from provider-switch apply when target fidelity or client-side key export cannot be proven. Matrix E2EE is an implemented release candidate, but the product claim remains gated on green live two-device, ciphertext-only, recovery, revocation, accessibility, and physical-iPhone relaunch evidence. Weaver remains optional and policy-bound, and unrestricted autonomous agents are not part of the current public claim.

The workload identity and MCP context chain is implemented, but no domain tool is advertised yet. Read tools still need catalog/RuntimeProfile/domain-authorization intersection; writes additionally need signed single-use ApprovalDecisionEvidence v2 and immutable ActionEvidence v2. The file-key RuntimeState adapter is dogfood-only until external KMS custody and cross-node crash/restore evidence exist. Matrix v1.19 plus pinned MatrixRTC Profile 0 is the Calls target; the obsolete member Calls API and proprietary LiveKit join-grant slice have been removed. RTC Authorizer, TURN, media E2EE, consent/artifact, interoperability, and physical-device gates remain open.

The detailed boundary lives in the [product trust claim matrix](docs/product-trust-provider-choice-claim-matrix.md), the [provider portability docs](docs/architecture/provider-portability.md), and the [roadmap and guarded surfaces](docs/roadmap-and-guarded-surfaces.md).

## For Members

Members should experience Weave as one workspace instead of a tour through provider setup. The current product path starts with an organization entry point, then moves into Weave-owned collaboration surfaces and clear capability states such as `available`, `disabled_by_policy`, `not_configured`, `degraded`, `unavailable`, `coming_later`, or `unsupported`.

Start with the [user handbook](docs/user-handbook.md) and the [v0.1 golden path](docs/v0.1-golden-path.md).

## For Admins And Operators

Admins and operators use Weave as the governance layer for provider choice, readiness, policy, evidence, and change control. The [Bootstrap foundation](docs/bootstrap-foundation-contract.md) explains how the control plane is staged, while the admin/operator docs explain readiness, evidence handling, and support-safe operations.

Start with the [admin/operator handbook](docs/admin-operator-handbook.md), [Bootstrap foundation](docs/bootstrap-foundation-contract.md), [quality and evidence guide](docs/quality-and-evidence.md), and [provider portability](docs/architecture/provider-portability.md).

## For Developers

Developers should treat this repository as implementation and evidence truth, with product/domain truth pinned through the spec corpus. The shortest path in is the [developer handbook](docs/developer-handbook.md), followed by the [PR workflow](docs/gitflow-pr-workflow.md), the [operating model](docs/weave-operating-model.md), and [spec-driven development](docs/spec-driven-development.md).

Before changing cross-domain contracts, read `AGENTS.md`, `specs/README.md`, and the pinned spec corpus in `../weave-specs`. Public routes, auth, topology, generated OpenAPI, protocol facades, E2E evidence, and docs must move together.

The active monorepo is intentionally split by clean-architecture ownership:

- `client/` owns the Flutter member/admin experience and consumes Weave contracts;
- `server/` owns product APIs, authorization, audit, persistence, and provider ports/adapters;
- `weave-mcp-server/` owns the workload-only MCP protocol edge, not product authority;
- `infra/` owns OpenTofu, Caddy, Keycloak/provider topology, backup/restore, smoke, and support evidence;
- `rust/` owns shared Matrix protocol/crypto shaping used through explicit JNI/Flutter bridges;
- `e2e/`, `release/`, and `docs/` own behavioral evidence and claim boundaries.

For a local stack, build the two local images, then use the idempotent installer. The test principal
is opt-in and is removed again when the flag returns to `false`:

```bash
./gradlew :server:bootJar :weave-mcp-server:bootJar
docker build -f server/Dockerfile -t weave-backend:local .
docker build -f weave-mcp-server/Dockerfile -t weave-mcp-server:local .
TF_VAR_create_test_user=false ./infra/weave-workspace/install.sh
./infra/weave-workspace/smoke-test.sh
./infra/weave-workspace/operator-check.sh
```

Use the discovered Gradle and infra gates before opening a PR: `./gradlew check`,
`./gradlew infraStatic`, `tofu fmt -check -recursive`, and the repository-specific live checks
appropriate to the changed contract. Generated secrets, plans, logs, and support bundles do not
belong in commits.

Repository delivery is GitHub-only: feature work flows into `dev`, validated candidates move to `dogfood`, and stable release-capable truth moves to `main` through protected GitHub pull requests and Actions. Physical-iPhone iterations use the stable Weave app identity, with TestFlight as the preferred human dogfood channel and development-signed in-place installs as an engineering fallback. Normal updates preserve the saved organization profile, OIDC refresh session, Matrix device ID, and encrypted crypto store; dogfood never relies on repeatedly trusting a developer certificate.

## Release Notes

The frontdoor keeps the current release track visible here; the full chronology stays in the versioned release notes and evidence docs.

- **Published prerelease, 2026-06-01:** [`v0.1.0-rc.3`](docs/release-v0.1-rc3-evidence.md) added the provider-neutral suite foundation, Admin/Workspace Health readiness boundary, first governed Weaver slice, and green CI plus Live Stack evidence for that candidate.
- **Guarded Beta slice, refreshed 2026-06-18:** the [Sprint 32 closure report](docs/sprint-32-closure-report.md) captures Admin readiness preview, adapter-continuity dry-run, approval-required Weaver actions, member Client + Weaver flow, and Admin + User + Weaver E2E/accessibility smoke. It is ready for #836 review, not an overall Sprint 32 completion claim.
- **Active dogfood stream:** [Unreleased](docs/release-notes/unreleased.md) tracks current merged highlights, including free-provider lab coverage, provider-switch contract gates, human validation gates, commercial-adapter readiness guards, operator recovery guardrails, and refreshed Beta evidence.

<!-- WEAVE_RELEASE_NOTES_START -->
- Current checked-in draft: [Unreleased](docs/release-notes/unreleased.md)
- Latest release index: [Release notes](docs/release-notes/index.md)
<!-- WEAVE_RELEASE_NOTES_END -->

## Release Evidence

Every public claim in this README is supposed to terminate in a support-safe artifact, release note, or claim-boundary document. Release evidence stays separate from marketing copy so the frontdoor can stay readable while reviewers still have a precise path to the underlying proof.

<!-- WEAVE_RELEASE_NOTES:START -->
- Current checked-in draft: [Unreleased](docs/release-notes/unreleased.md)
- Offline release-note fixture review artifact: `build/release-notes/unreleased.md`
- Release evidence check: deterministic CI/local gate for README markers, release-note structure, label policy, and release evidence fixtures.
<!-- WEAVE_RELEASE_NOTES:END -->
