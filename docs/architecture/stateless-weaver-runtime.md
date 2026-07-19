# Stateless Weaver runtime architecture

Status: **Proposed target architecture; not an implementation claim.** This is the implementation-repo projection for Agent Runtime Control. Canonical product/domain truth remains the pinned Weave Specification Corpus and must be reconciled before merge.

## Binding decision

A Weaver Cell is ephemeral compute and owns no durable bytes. It can be deleted, moved, upgraded or scaled to zero and reconstructed from external authorities. OpenClaw continues to see a normal local filesystem; persistence is supplied around it, not by replacing its workspace, memory or session mechanisms.

## Storage contract

| Authority | Durable data |
|---|---|
| Member Files/WebDAV | bootstrap Markdown, `MEMORY.md`, `memory/**/*.md`, optional `DREAMS.md`/imports, approved custom `skills/**` |
| Weave Control Store | entitlement/profile refs, lease/fencing, Workspace HEAD/manifest, bootstrap marker, wake dedupe/outbox, conflicts, audit |
| RuntimeStateStore | encrypted versioned `$OPENCLAW_STATE_DIR` checkpoint: sessions/SQLite, approvals, channel/plugin state, stable Matrix device/crypto state |
| Secret Manager/KMS | OAuth/Matrix/provider/DAV credentials, recovery material, encryption keys |
| Cell-local ephemeral | generated config, staged snapshot and write overlay, derived indexes/caches, processes and short-lived tokens |

The private user root is `/dav/files/{personRef}/.weaver/workspace/`. Hiding `.weaver` is only UX; private DAV ACLs, no public shares and subject/organization binding are security requirements. SQLite, tokens, raw transcripts and Matrix crypto never use WebDAV.

## Start and stop protocol

1. Verify Keycloak entitlement and signed RuntimeProfile.
2. Acquire one distributed per-person lease with a monotonically increasing fencing epoch.
3. Restore a verified RuntimeState generation.
4. Fetch the control-plane-signed WorkspaceManifest; materialize a complete immutable WebDAV revision to fresh staging.
5. Validate paths/content/size and approved skill hashes; activate a local immutable base plus copy-on-write overlay.
6. Generate secret-free OpenClaw configuration and start. Every run pins profile, workspace and skill revisions.
7. Journal allowed writes externally immediately. At turn end, before compaction/response completion and on stop, publish a new immutable revision and advance Workspace `HEAD` through conditional compare-and-swap.
8. Drain work, flush OpenClaw/SQLite WAL, checkpoint RuntimeState, revoke credentials, release the lease and erase the cell filesystem.

On crash or lease loss, stale writes fail by fencing token. A new cell restores completed state/workspace generations and the durable journal; it never reads residue from the failed cell. “Remembered” may be confirmed only when journaled durably or committed to WebDAV; an outage produces a visible `DEGRADED` state.

## Workspace files and skills

Supported v1 content: `AGENTS.md`, `SOUL.md`, `USER.md`, `IDENTITY.md`, `TOOLS.md`, optional `HEARTBEAT.md`, `BOOT.md`, one-time `BOOTSTRAP.md`, `MEMORY.md`, dated or slugged Markdown under `memory/**`, optional `DREAMS.md`/imports, and approved skills.

Private `MEMORY.md` is loaded only for the member's main private session. Shared-room execution does not receive it without an explicit safe projection. Raw sessions are not memory automatically.

Because workspace skills have highest OpenClaw precedence, v1 permits only `SKILL.md` plus approved text/JSON/YAML resources. Binaries, archives, symlinks, plugins, reserved names and shadowing managed skills are blocked. Activation requires scan, allowlist/signature, hash pinning and a new WorkspaceRevision; skill text cannot expand tools, sandbox or network policy.

## Atomic workspace model

WebDAV has no atomic multi-file transaction. The Workspace Service therefore owns immutable revisions, a signed manifest and one atomically updated `HEAD`. Direct DAV edits first become drafts. Only successful `HEAD` CAS makes a revision active. Conflicts preserve both versions and emit a ConflictRecord; no blind last-write-wins.

## Lifecycle and deletion

```text
ABSENT → PROVISIONING → STOPPED → ACQUIRING_LEASE → RESTORING → MATERIALIZING
MATERIALIZING → READY ↔ BUSY → COMMITTING → READY
READY/BUSY → STOPPING → COMMITTING → STOPPED
CRASH/LEASE_LOSS → DEGRADED → STOPPED/ACQUIRING_LEASE
ANY → REVOKING → SUSPENDED/STOPPED
STOPPED → RESETTING/DELETING → STOPPED/DELETED
```

Session reset, RuntimeState reset, Matrix device rotation/logout, credential revoke and workspace/memory deletion are separate actions. E2EE-destructive operations require step-up confirmation, explicit loss warning and recovery check. Runtime reset never deletes WebDAV memory.

## Acceptance

- reconstruct on another node after complete old-cell deletion;
- exactly-once cold wake from Matrix and split-brain fencing;
- durable `AGENTS.md`, memory and approved custom skill across scale-to-zero;
- crash tests at restore, materialize, journal, HEAD-CAS and checkpoint boundaries;
- no mid-run profile/workspace/skill drift;
- no SQLite, secret, recovery key, raw transcript or generated config in WebDAV/profiles/logs/support bundles;
- stable Matrix device/recovery behavior and explicit recovery after crypto-state loss;
- OpenClaw upgrade and rollback via versioned external checkpoints.


## Migration boundary

Current pod-local persistence, in-memory maps, stateful MCP transport behavior and provider-specific runtime wiring remain evidence of the existing implementation only. Migration may retain them behind an explicitly deprecated adapter while cross-node restore and fencing are built. No retained adapter may be presented as the target, and each requires an owner, removal issue/date, rollback rule and parity test.

See [the implementation plan](../implementation-plans/matrixrtc-stateless-runtime.md) for phased delivery and negative-test requirements.
