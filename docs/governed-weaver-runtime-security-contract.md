# Agent Runtime Control security contract

Status: **Guarded implementation contract**. This document describes the active Weaver/OpenClaw
runtime-control boundary. It does not claim a generally available personal agent, active domain
tool catalog, production-grade KMS integration, or completed disposable-cell orchestrator.

The canonical contract is the pinned `weave-specs` Agent Runtime Control bounded context and its
Keycloak workload-identity ADR. Weaver is the optional product capability, OpenClaw is the first
runtime provider, and Agent Runtime Control (ARC) is the narrow Weave control-plane context. ARC
is not a collaboration content domain and does not own Matrix messages, WebDAV files, domain
permissions, provider side effects, OpenClaw sessions, or OpenClaw approval state.

## Authority and identity boundary

- Keycloak is the organization identity backbone. An administrator uses the public
  `weave-admin-console` Authorization Code + PKCE client and must have both a current owner/admin
  role and `agent-runtime.admin`.
- ARC resolves the target `acct_` person reference through the configured Keycloak organization.
  Request data cannot choose an organization, email identity, raw subject, or arbitrary owner.
- Human Weaver entitlement is exact membership in the native Keycloak Organization group
  `/capabilities/weaver`. ARC reads the enabled organization member and that member's groups
  through the Organizations Admin API. Realm user groups, token claims, cached observations,
  human roles, and the workload-only `weaver-runtime` role never grant this entitlement.
- Every cell receives one dedicated confidential Keycloak client named `weaver-cell-{cellId}`.
  ARC creates, reconciles, rotates, disables, and deletes that client through the distinct,
  least-privileged `weave-agent-runtime-admin` service account. It never reuses the
  organization/member `weave-identity-admin` credential for client lifecycle.
- RuntimeProfile retrieval is workload-only. The token must be an RFC 9068 `at+jwt` whose
  `client_id` and `azp` name the same bound cell client, whose subject matches the immutable
  Keycloak service-account binding, whose audience is the exact ARC resource, whose only scope
  is `agent-runtime.profile.read`, and whose only realm workload role is `weaver-runtime`.
- Human tokens, public clients, generic service accounts, member roles, additional audiences,
  additional scopes, stale profiles, revoked entitlements, and caller-supplied binding hints fail
  closed.

## RuntimeProfile v2

ARC signs a short-lived `RuntimeProfile v2` desired-state projection. The semantic profile hash
is the lowercase `sha256:` digest of the RFC 8785 JCS payload bytes; a matching hash never
substitutes for signature, expiry, entitlement, and current cell-binding validation.

The profile contains references and maximum permitted capabilities only. It does not contain a
member bearer token, provider credential, OpenClaw configuration file, service endpoint secret,
runtime database, approval decision, or domain authorization. Runtime configuration such as
`openclaw.json` is ephemeral provider output reconstructed from the current signed profile.
RuntimeProfile v1, `WeaverRuntimeProfile`, and compatibility readers are removed target contracts.

The signing trust root is an operator-mounted Ed25519 SecretRef. Normal application startup never
generates a replacement key. Prepare, activate, and retire are explicit idempotent operations;
the public overlap window is at least the maximum profile lifetime. Private signing material is
excluded from application databases, source, logs, metrics, audit events, support bundles, and
backup manifests.

## Disposable cell and external state

The target runtime cell owns **zero durable cell-local bytes**:

- WebDAV is canonical only for allowlisted portable workspace content. A signed
  `WorkspaceManifest` constrains paths, types, ownership, sizes, ETags/hashes, signers, and write
  policy. Materialization uses ephemeral staging and rejects traversal, links, devices,
  oversize content, and unsigned artifacts.
- Runtime databases, sessions, credentials, Matrix crypto/device state, plugin state, generated
  configuration, and provider-specific state never live on WebDAV or a durable cell volume.
- The provider-neutral `RuntimeStateStore` holds encrypted external generations. The target
  adapter stores immutable AES-256-GCM ciphertext in S3/MinIO and authority metadata in
  PostgreSQL, with organization/person/cell/generation/profile authenticated context and
  independently wrapped data keys. The adapter remains `Guarded` and fails closed in
  dogfood/main until the cross-store outbox/reconciler and OpenBao/KMS boundary have their own
  migration, reconstruction, corruption, rollback, retention, and deletion evidence.
- Generation heads use compare-and-swap. State publication, event redelivery, reconcile, and
  deletion are idempotent. A stale generation or fencing epoch cannot win.
- Keycloak data, ARC bindings, RuntimeProfile signing trust, workload credential SecretRefs, and
  external runtime state form one restore consistency set. Readiness remains blocked until those
  authorities reconcile and cross-cell isolation is proven.

The lifecycle is `ABSENT -> PROVISIONING -> STOPPED -> STARTING -> MATERIALIZING -> READY`, with
`BUSY`, `SYNCING`, `DEGRADED`, `SUSPENDED`, `REVOKING`, `DELETING`, and `DELETED` as explicit
states. Only `READY` and `BUSY` may process events.

Normal cell deletion does not delete canonical Files content or externally retained runtime
state. The separately confirmed `DELETE_RUNTIME_STATE_ONLY` operation is the narrow exception:
after revocation it deletes the selected cell's encrypted generations, dedicated Keycloak
client, and workload credential SecretRef, records deletion evidence, and never touches WebDAV,
provider data, or another retention domain.

## MCP and domain authorization

MCP is an AI-workload surface, never a human member API. A cell obtains a short-lived exact-MCP-
audience token with the MCP Client Credentials extension. `weave-mcp-server` validates RFC 9068
token type, issuer, time, exact audience, bound workload role, exact current scopes, and active
cell binding before Spring AI dispatch.

The MCP edge uses Keycloak Standard Token Exchange V2 to create a new exact-backend-audience,
downscoped token and asks `weave-backend` to resolve current
`client -> cell -> organization -> immutable person owner -> RuntimeProfile v2` context. The
inbound bearer is never relayed. Public member tokens, the fixed MCP server service account, and
unbound service accounts cannot discover or invoke tools.

The domain tool, resource, and prompt catalogs are currently empty. A future catalog is a
capability ceiling only. Discovery and invocation must intersect the fixed contract with the
current RuntimeProfile, current entitlement, current domain permission, and runtime readiness.
Every receiving domain independently reauthorizes the effective person and authenticated cell at
execution time; a RuntimeProfile or approval artifact can never grant a domain permission.

## OpenClaw-native approval boundary

OpenClaw owns approval presentation, Matrix routing, `allow-once`/bounded remembered decisions,
timeout, cancellation, and its open approval lifecycle. MCP form elicitation is protocol UI, not
authorization. ARC may register a short-lived action/argument challenge and, after validating the
authenticated Matrix resolver, produce signed, argument-bound, single-use
`ApprovalDecisionEvidence v2`.

Before a side effect, the owning domain reauthorizes, atomically claims the evidence `jti`, writes
an operation intent/outbox record, invokes the provider with one canonical idempotency key, and
records immutable `ActionEvidence v2` for the final observed result. The removed
`WeaverApprovalReceipt`, central member approval inbox, caller-created approval receipt, and v1
evidence readers have no compatibility path.

## Upstream and supply-chain boundary

The `weaver` repository is an upstream-first thin fork of OpenClaw. Release evidence pins an
upstream stable tag/commit, records the local patch inventory, verifies the upstream signature,
and blocks unclassified drift. A local core patch requires an isolated upstream gap, tests, an
owner, an upstream reference, and a deletion criterion. Runtime images must be separate from the
Weave backend/MCP images, digest-pinned for release, scanned, accompanied by an SBOM, and contain
no baked member or provider secrets.

## Current evidence and guarded work

Implemented and live-proven in the local topology:

- exact administrative ARC authorization and organization/person binding;
- idempotent per-cell Keycloak client lifecycle with separate least-privileged service accounts;
- signed RuntimeProfile retrieval and current workload binding;
- PostgreSQL runtime authority metadata, generation CAS and deletion-ledger persistence, mounted
  SecretRef validation, backup/restore wiring, and support-bundle redaction;
- workload-only Spring AI Streamable HTTP admission, protected-resource metadata, MCP Client
  Credentials extension negotiation, Standard Token Exchange V2, and backend current-context
  validation;
- positive cell-token-to-MCP-to-backend proof plus negative human, generic-service, upscope,
  stale-binding, and admin-route proofs.

Still guarded: S3/MinIO runtime-state activation pending its durable cross-store outbox/reconciler,
a production external KMS/secret-manager adapter, disposable-cell orchestration and kill/recreate
on a second node, WebDAV manifest materialization, the official Matrix/OpenClaw approval live
proof, signed skills, non-empty domain tools, and production SLO/restore evidence.

Primary executable gates are `./gradlew serverCi`, `./gradlew infraStatic`,
`python3 tools/spring_ai_mcp_facade_acceptance_check.py`, the ARC controller/security tests, and
the infrastructure workload/lifecycle contract tests.
