# Weaver cell lifecycle and storage contract

Status: the Agent Runtime Control plane and self-hosted encrypted state adapter are `Guarded`. The baseline infrastructure does not claim a production-ready Weaver cell runtime or production KMS custody. The canonical contract is the pinned `weave-specs` Agent Runtime Control domain; this document describes its executable local/dogfood projection.

## One governed cell, one workload identity

An entitled person may have one active Weaver cell in an organization. The server owns the immutable Keycloak subject-to-`personRef` binding, `cellRef`, signed `RuntimeProfile`, revocation generation, and lifecycle revision. Names, email addresses, raw subjects, provider identifiers, and operator-selected aliases are not runtime identity.

Each cell gets a dedicated confidential Keycloak client named `weaver-cell-{cellId}`. Its
`client_id` and `azp` must match that client, while `sub` must match the immutable Keycloak
service-account subject recorded by ARC. Tokens carry the sole realm role `weaver-runtime`, no
client roles, and an exact resource-specific scope set:

- only `agent-runtime.profile.read` for `https://api.weave.test/api/v1/agent-runtime`;
- `mcp.tools` plus the current RuntimeProfile's allowed domain scopes for
  `https://api.weave.test/mcp`.

Human clients and generic service accounts have no MCP path. A cell never receives a member token or the `weave-mcp-server` credential. The MCP edge exchanges rather than relays the incoming workload token and every receiving domain revalidates current cell binding, entitlement, member authorization, policy, arguments, expiry, and revocation.

## Signed RuntimeProfile input

The cell accepts only the currently bound flattened JWS `RuntimeProfile` issued by Agent Runtime Control. It verifies Ed25519 trust through the canonical JWKS route, RFC 8785 canonical payload identity, issuer, organization, person, cell, workload client, expiry, profile hash, and revocation generation. The profile contains references and maximum capabilities, never provider credentials, member tokens, refresh tokens, or raw provider payloads.

Runtime configuration rendered from a valid profile is disposable. A changed trust boundary or invalid, expired, stale, or revoked profile stops processing instead of preserving an older local configuration as authority.

## Zero durable cell-local bytes

The target cell has a read-only root filesystem and no durable `stateDir`, `workspaceDir`, `agentDir`, host home, Docker socket, SSH agent, keychain, or implicit bind mount. Writable runtime, materialization, and cache paths are bounded tmpfs or equivalent ephemeral storage with CPU, memory, process, and ephemeral-disk limits. Stopping or rebuilding a cell destroys all of those bytes.

WebDAV is canonical only for allowlisted portable workspace content such as `AGENTS.md`, bootstrap instructions, portable memory, and signed skills. A signed `WorkspaceManifest` constrains path, type, owner, size, hash/ETag, signer, and write policy. Materialization stages into ephemeral storage, rejects traversal, links, devices, oversize or unsigned content, overlays immutable organization policy, and atomically activates a snapshot. Conditional sync preserves both versions on conflict.

Databases, sessions, credentials, Matrix crypto/device state, plugin state, generated configuration, and other runtime state never enter WebDAV or a durable cell volume. They are checkpointed as generations through the provider-neutral `RuntimeStateStore`.

## Encrypted external RuntimeStateStore

Dogfood stores ordered ciphertext objects as immutable generations in an isolated S3-compatible MinIO store; JPA persists only coordination metadata. Each generation uses a random AES-256-GCM data key. Authenticated context binds the organization, person, cell, store, generation reference, generation number, and `runtimeProfileHash`. A separately mounted key wraps data keys with AES-KWP; the raw wrapping key never enters PostgreSQL, object storage, source, environment variables, logs, WebDAV, the cell image, backup manifests, or support bundles.

Generation commits use compare-and-swap. A wake is acknowledged only after its post-event generation wins that comparison, and redelivery reuses the same source-event and idempotency references. The mounted file-key adapter supports overlap rotation but remains `Guarded`; a production claim requires separately evidenced external KMS or secret-manager custody.

Revocation disables the workload and preserves retained authorities according to policy. The
separately confirmed `DELETE_RUNTIME_STATE_ONLY` operation is narrower and destructive: after
revocation it deletes the selected cell's encrypted RuntimeState generations, dedicated Keycloak
client, and credential SecretRef, then records `DELETED`. It never deletes WebDAV/Files content,
provider data, or another retention domain. Conversation reset, Matrix device/crypto reset,
credential revocation, workspace-memory deletion, and complete personal-agent-data deletion remain
separate commands rather than aliases for runtime-state deletion.

## Network and lifecycle

Default egress is deny. A cell may reach only the internal Weave API, the exact MCP gateway, and explicitly mediated channel endpoints selected by the signed profile. Direct provider APIs, public internet, metadata services, the host network, and cross-cell networks are forbidden.

The canonical lifecycle is:

`ABSENT -> PROVISIONING -> STOPPED -> STARTING -> MATERIALIZING -> READY <-> BUSY -> SYNCING -> STOPPED`

`DEGRADED`, `SUSPENDED`, `REVOKING`, `DELETING`, and `DELETED` are explicit side paths. Only `READY` and `BUSY` may process member events. Revocation wins over queued wake-up, remembered approval, and pending tool work.

## Restore and support evidence

Keycloak data, the Agent Runtime Control database, the RuntimeProfile signing SecretRef root, the RuntimeStateStore wrapping-key SecretRef root, and per-cell workload-credential SecretRefs are one private restore consistency set. Normal server startup never generates missing roots. Restore stays unready until current profiles, cell bindings, revocations, Keycloak clients, published signing trust, and encrypted state all reconcile.

Support output may include opaque cell/profile correlations, lifecycle state, counts, policy/audit references, and reconciliation status. It omits prompts, payloads, tokens, cookies, private keys, key and credential references, member identity, filenames, room/event IDs, and direct provider identifiers.
