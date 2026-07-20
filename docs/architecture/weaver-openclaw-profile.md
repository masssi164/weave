# Weaver/OpenClaw runtime projection

Status: **Guarded**. Agent Runtime Control, signed RuntimeProfile v2, per-cell Keycloak workload
identity, encrypted external state generations, and the MCP identity/context chain are implemented.
A production cell runtime, immutable workspace materializer, external KMS custody, native approval
evidence, and cross-node crash/reconstruction proof remain gated.

## Ownership

Weave is the provider-neutral organization product. Weaver is an optional OpenClaw-based runtime,
not a collaboration domain and not a second source of identity, policy, provider configuration, or
authorization. `weave/server` owns the narrow Agent Runtime Control context. The upstream-first
`weaver` distribution owns the agent loop and consumes the signed desired-state projection.

The active projection is `RuntimeProfile v2`. Older `WeaverRuntimeProfile` product models and v1
readers are historical only; they do not authorize a cell or MCP call. Generated `openclaw.json`,
channel/plugin entries, MCP configuration, tool filters, model defaults, and sandbox settings are
ephemeral implementation outputs and never member-editable authority.

## RuntimeProfile v2

ARC issues a flattened Ed25519 JWS after revalidating current Keycloak organization membership,
entitlement, immutable person/cell/workload binding, policy, workspace revision, and revocation
state. The cell verifies the signature and exact canonical hash before projection. The profile
contains identifiers, maximum capabilities, expiry, references, and SecretRefs only—never bearer
tokens, refresh tokens, raw provider credentials, provider payloads, or mutable authorization
decisions.

The effective capability rule is **user-rights, organization-whitelisted capabilities**. A profile
can narrow what a runtime may attempt; it cannot grant a product-domain permission. Weave freshly
authenticates, authorizes, policy-checks, idempotently applies, and audits every MCP side effect.

## Upstream-first OpenClaw boundary

The Weaver fork uses upstream OpenClaw for the agent loop, sessions, official Matrix plugin,
workspace/Markdown memory and skill loaders, MCP client, and native approval lifecycle. It does not
create `weave-chat`, a second memory engine, a second approval inbox, or a parallel agent core.
OpenClaw owns approval presentation and decision state. Weave accepts only canonical,
argument-bound ApprovalDecisionEvidence v2 at the final authorization boundary and emits immutable
ActionEvidence v2 for a completed side effect.

The stock `channels.matrix` projection points to the OIDC-gated Weave Matrix Client-Server facade.
Matrix, Teams, iMessage, Slack, Telegram, or another supported southbound provider are backend
providerRefs, not per-member runtime channel choices. Admin Chat provider changes are provider
migrations, not member adapter switches: Weave reviews readiness/fidelity, changes the Chat-domain
route, and issues RuntimeProfile vNext without exposing provider secrets or URLs to the cell.

## Disposable cell and external authorities

One active user/trust boundary maps to one active runtime context/container. The binding consists
of organization, immutable Keycloak service-account subject and person owner, `cellRef`, current
RuntimeProfile hash, entitlement revision, and revocation/fencing state.

The target cell has zero durable cell-local bytes. It uses a read-only image and bounded ephemeral
runtime/materialization/cache storage. Durable `stateDir`, `workspaceDir`, `agentDir`, host home,
Docker socket, SSH agent, keychain, and implicit bind mounts are forbidden. Durable authorities are
separated:

- WebDAV stores only signed-manifest-constrained portable workspace and Markdown memory;
- the Weave Control Store owns entitlement/profile refs, lifecycle, manifests/HEAD, dedupe,
  checkpoint refs, fencing, conflicts, and audit metadata;
- RuntimeStateStore owns encrypted OpenClaw sessions/databases, native approval state,
  channel/plugin state, Matrix device/crypto state, and recovery journal generations;
- Secret Manager/KMS owns credentials, recovery material, and encryption keys.

The dogfood RuntimeStateStore encrypts each generation with AES-256-GCM, wraps the random data key
with a separately mounted AES-KWP key, chunks ciphertext in PostgreSQL, and advances state through
compare-and-swap. The file-key adapter remains Guarded; production needs an external KMS/secret
manager plus cross-node reconstruction, corruption, rollback, and migration evidence.

## Workload and MCP path

Each cell receives only its dedicated `weaver-cell-{cellId}` private-key credential reference. A
profile-read token has the exact ARC audience and sole `agent-runtime.profile.read` scope. An MCP
token has the exact MCP/requester audience set, `mcp:tools`, and only the profile's current domain
scopes. Both are RFC 9068 access tokens; human and generic service-account tokens are rejected.

The MCP edge negotiates the MCP Client Credentials extension, validates the bound workload, uses
Keycloak Standard Token Exchange V2 to mint a reduced exact-audience backend token, and resolves
current ARC context before Spring AI protocol dispatch. It exchanges rather than relays the bearer.
The domain catalogs remain empty until discovery and approval/action-evidence gates are complete.

## Lifecycle, deletion, and recovery

Lifecycle changes are server-governed and idempotent. Revocation wins over queued wake work,
remembered approval, profile expiry, and pending tools. It fences new work, revokes the profile and
credential, and stops compute. The confirmed `DELETE_RUNTIME_STATE_ONLY` command additionally
deletes the selected cell's encrypted RuntimeState generations, Keycloak client, and credential
SecretRef; it never deletes WebDAV/Files or provider data.

Session reset, RuntimeState reset, Matrix device/crypto rotation, credential revocation, workspace
memory deletion, and complete personal-agent-data deletion are separate operations. E2EE-destructive
actions require step-up, an irreversibility warning, and recovery/backup evidence.

Restore treats Keycloak data, ARC database state, signing trust, wrapping keys, and per-cell
credential SecretRefs as one consistency set. Missing trust roots are never generated by normal
startup. Readiness stays closed until profiles, bindings, clients, revocations, and encrypted state
reconcile.

## Promotion gates

No overall Weaver readiness claim is allowed until a busy cell is killed and reconstructed on
another node without the old filesystem or acknowledged-memory loss; stale-fencing writers fail;
official Matrix wake/dedupe and native approvals survive replacement; workspace materialization and
skill quarantine withstand path/content attacks; external KMS and key-loss operations are proven;
and support/evidence scans remain free of member content, tokens, keys, SecretRef values, and raw
provider data.
