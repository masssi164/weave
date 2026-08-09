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

| Domain | Permanent northbound member data plane | Canonical Weave boundary | Selected default provider |
| --- | --- | --- | --- |
| Chat | Matrix Client-Server-compatible facade at the public API origin under `/_matrix/client/**` | Provider-neutral conversations, rooms, events, membership, sync and encryption policy | `weave-native` Chat with PostgreSQL/JPA; Synapse/Matrix-backed adapters remain optional southbound providers |
| Files | WebDAV facade under `/dav/files/**` | Provider-neutral files, folders, versions, rights, locks, lifecycle and audit | `weave-native` Files with JPA metadata and Apache OpenDAL blob storage; Nextcloud/WebDAV/S3-class adapters remain optional providers/backends |
| Calendar | CalDAV/iCalendar facade under `/caldav/**` | Provider-neutral calendars/events, time semantics, recurrence, sync and meeting-thread references | `weave-native` Calendar with JPA/PostgreSQL and iCal4j; Nextcloud/CalDAV/Radicale adapters remain optional providers |
| Platform identity/security | OIDC/OAuth2 with Keycloak as authority | One login, user profile, roles, policy, audit, workload identities, support-safe diagnostics | Keycloak; Entra ID/Auth0/Authentik/LDAP/AD may federate or broker upstream through Keycloak |
| Boards/tasks | Weave product/control APIs while protocol parity matures | Provider-neutral task, board, readiness, mapping, authorization, and audit contracts | Local workspace today; OpenProject-class adapters remain gated |
| Calls/meetings | Matrix v1.19 plus the revision-pinned MatrixRTC Profile 0 target | Matrix room, slot, membership, authorization, media-key, consent, and artifact contracts | LiveKit is the first replaceable RTC transport/SFU, not the member contract |
| Agent Runtime Control | Signed RuntimeProfile v2 and an administrative lifecycle API | Entitlement, cell identity, desired state, profile issuance, workload reconciliation, encrypted external state, and audit | `weave/server`; Weaver/OpenClaw is the first runtime consumer |
| Agent tools | Guarded OAuth-protected MCP at `/mcp` using Spring AI stateful Streamable HTTP | ARC-bound workload admission plus `files.search` and `weave://files/{canonicalFileId}` over the canonical Files boundary | Keycloak Standard Token Exchange V2 and the existing Weave WebDAV projection; Calendar, Chat and write catalogs remain gated |

The WebDAV, CalDAV and Matrix Client-Server surfaces are server contracts, not provider feature flags. Provider selection happens only behind `FilesProviderPort`, `CalendarProviderPort`, and `ChatProviderPort`; changing a provider must not change the northbound URL, canonical IDs, authorization semantics, or application contracts.

Spring Boot is the server gatekeeper for OIDC, authorization, audit, readiness, and support-safe errors. The Matrix facade shares the public API origin; a `matrix.<tenant>` host is a southbound provider/operator endpoint, never a member-client setting. Server-side Matrix protocol shaping lives in the isolated `weave-matrix-protocol` Rust crate using Ruma and jni-rs. Client-side Matrix SDK/E2EE and Flutter bindings live separately in `weave-matrix-client`; client crypto is not linked into the server runtime.

Matrix encryption is device-owned. The Flutter bridge uses the Apache-2.0 Matrix Rust SDK for encrypted-room state, cross-signing, SAS verification, recovery, and an encrypted SQLite crypto store. Spring and southbound adapters may persist public device keys, opaque encrypted events, to-device envelopes, and room-key backup ciphertext, but never user private keys or decrypted message bodies. Plaintext fallback is rejected for encrypted rooms.

```mermaid
flowchart LR
  clients["Flutter, native DAV clients, and Weaver cells"] --> oidc["Keycloak identity"]
  oidc --> northbound["WebDAV | CalDAV | Matrix Client-Server/MatrixRTC | OAuth-protected MCP"]
  northbound --> domains["Files | Calendar | Chat | Calls | Agent Runtime Control domains"]
  domains --> ports["Provider ports, mappings, conformance, and audit"]
  ports --> native["weave-native defaults"]
  ports --> optional["Replaceable Nextcloud/WebDAV, CalDAV, Synapse/Matrix, Slack, Teams and future providers"]
```

Textual equivalent: clients authenticate through the Weave identity boundary and always reach Weave-owned northbound standards interfaces. Those interfaces call canonical domain/application services, which select a provider only behind the corresponding provider port. `weave-native` is the selected default implementation for Files, Calendar and Chat; optional external providers stay southbound and replaceable.

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
- Normal member data planes terminate at Weave-owned northbound standards: Chat through Matrix Client-Server, Files through WebDAV, and Calendar through CalDAV/iCalendar. Those protocol surfaces remain stable independently of the selected southbound provider.
- The native-provider completion track is moving Files, Calendar and Chat authority to the first-party `weave-native` providers. Completion/readiness claims remain gated by the acceptance and evidence criteria tracked in the active native-provider PR and issues; this README does not substitute architecture intent for green evidence.
- Chat uses the client-owned Rust crypto engine for encrypted sync/send, durable device identity, SAS verification, recovery, and lost-device revocation. The client crypto store remains device-owned; the server stores/routs ciphertext and public or opaque Matrix routing metadata only.
- Agent Runtime Control now owns per-cell Keycloak workload identity, signed RuntimeProfile v2 issuance, lifecycle reconciliation, revocation/deletion, encrypted external RuntimeState generations, and separate least-privilege identity-administration credentials. The MCP edge admits only a current bound cell, validates RFC 9068 `at+jwt`, negotiates the MCP Client Credentials extension, exchanges rather than relays the workload token, and revalidates current context in the backend. Human and generic service-account access remains closed.
- The first MCP domain slice is active: Spring AI publishes `files.search` and the canonical file resource, while the separate MCP process consumes the same OAuth-protected WebDAV `SEARCH`/`GET` projection as standards clients. It has no private MCP-only business API, persistence, provider adapter, or duplicate use case.
- Weave treats admin/operator readiness as part of the product: provider categories, policy boundaries, evidence, and support-safe diagnostics belong in the control plane, not in member setup.

## What Is Guarded

Weave is in active dogfood and does not claim public production readiness. The portability promise is no unaccounted data loss; perfect lossless migration is not claimed. In particular, universal provider interchangeability is not claimed. Encrypted history remains blocked from provider-switch apply when target fidelity or client-side key export cannot be proven. Matrix E2EE is an implemented release candidate, but the product claim remains gated on green live two-device, ciphertext-only, recovery, revocation, accessibility, and physical-iPhone relaunch evidence. Weaver remains optional and policy-bound, and unrestricted autonomous agents are not part of the current public claim.

The workload identity, MCP context chain, and first read-only Files slice are implemented and remain Guarded pending exact deployed authorization evidence and RFC 8707 support. Calendar/Chat tools and every write tool remain absent. Writes additionally need signed single-use ApprovalDecisionEvidence v2 and immutable ActionEvidence v2. The file-key RuntimeState adapter is dogfood-only until external KMS custody and cross-node crash/restore evidence exist. Matrix v1.19 plus pinned MatrixRTC Profile 0 is the Calls target; RTC Authorizer, TURN, media E2EE, consent/artifact, interoperability, and physical-device gates remain open.

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
- `weave-application-core/`, `weave-files-core/`, `weave-persistence-jpa/`, and the runtime adapter modules keep domain, persistence, security and provider dependencies directed inward;
- `weave-mcp-server/` owns the workload-only MCP protocol edge, not product authority;
- `weave-product-e2e/` owns the framework-free invitation/OIDC/WebDAV/MCP acceptance driver and no runtime beans;
- `infra/` owns OpenTofu, Caddy, Keycloak/provider topology, backup/restore, smoke, and support evidence;
- `rust/matrix-protocol/` owns server-side Matrix protocol shaping and JNI;
- `rust/matrix-client/` owns client Matrix SDK/E2EE and Flutter Rust bindings;
- `e2e/`, `release/`, and `docs/` own behavioral evidence and claim boundaries.

For a local stack, build the two local images, then use the idempotent installer. Human principals are created only through invitation and activation:

```bash
./gradlew :server:bootJar :weave-mcp-server:bootJar
docker build -f server/Dockerfile -t weave-backend:local .
docker build -f weave-mcp-server/Dockerfile -t weave-mcp-server:local .
./infra/weave-workspace/install.sh
./infra/weave-workspace/smoke-test.sh
./infra/weave-workspace/operator-check.sh
```

Use the discovered Gradle and infra gates before opening a PR: `./gradlew check`, `./gradlew infraStatic`, `tofu fmt -check -recursive`, and the repository-specific live checks appropriate to the changed contract. Generated secrets, plans, logs, and support bundles do not belong in commits.

The complete Fresh product proof is one command:

```bash
./gradlew testApp
```

It builds the current Server and MCP images (or accepts only paired digest-pinned candidate images), creates a disposable labelled stack, completes real invitation/activation and PKCE, invokes `files.search` through MCP and WebDAV, proves revocation, writes support-safe evidence, and tears down only the exact run-owned resources. Human passwords and activation links remain inside the test JVM and are never written to files or Gradle properties.

Repository delivery is GitHub-only: feature work flows into `dev`, validated candidates move to `dogfood`, and stable release-capable truth moves to `main` through protected GitHub pull requests and Actions. Physical-iPhone iterations use the stable Weave app identity, with TestFlight as the preferred human dogfood channel and development-signed in-place installs as an engineering fallback. Normal updates preserve the saved organization profile, OIDC refresh session, Matrix device ID, and encrypted crypto store; dogfood never relies on repeatedly trusting a developer certificate.

## Release Notes

The frontdoor keeps the current release track visible here; the full chronology stays in the versioned release notes and evidence docs.

- **Published prerelease, 2026-06-01:** [`v0.1.0-rc.3`](docs/release-v0.1-rc3-evidence.md) added the provider-neutral suite foundation, Admin/Workspace Health readiness boundary, first governed Weaver slice, and green CI plus Live Stack evidence for that candidate.
- **Guarded Beta slice, refreshed 2026-06-18:** the [Sprint 32 closure report](docs/sprint-32-closure-report.md) captures Admin readiness preview, adapter-continuity dry-run, approval-required Weaver actions, member Client + Weaver flow, and Admin + User + Weaver E2E/accessibility smoke. It is ready for review, not an overall completion claim.
- **Active dogfood stream:** [Unreleased](docs/release-notes/unreleased.md) tracks current merged highlights, including provider-switch contract gates, human validation gates, operator recovery guardrails, and refreshed Beta evidence.

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
