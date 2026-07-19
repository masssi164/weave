# Implementation plan and complete agent prompt: Matrix-native Calls and stateless Weaver

Status: **Approved target plan, implementation pending.** Use this as the complete handoff for reviewable vertical slices; documentation alone cannot satisfy any Ready gate.

Use this prompt with an implementation agent that has write access to `masssi164/weave`, `masssi164/weaver`, and `masssi164/weave-specs`.

## Mission

Implement the accepted target below as reviewable, tested pull requests. First remove contradictory contracts; then implement vertical slices. Do not preserve a legacy path merely because code exists. Do preserve useful implementation evidence and migrate it behind the standards contract.

Base branches:

- `masssi164/weave`: `dev`
- `masssi164/weaver`: `main`
- `masssi164/weave-specs`: `main`

Read repository instructions and current files before editing. Keep unrelated user changes. Use small commits, link every implementation PR to a normative requirement, and never claim `Ready` without reproducible evidence.

## Binding product model

1. Weave is the provider-neutral collaboration control/contract layer. Its member-facing data planes use open standards; southbound providers are replaceable.
2. Keycloak is the organizational identity backbone and federation/brokering point. Matrix Authentication Service (MAS) is the Matrix-facing Native OAuth authorization server and uses Keycloak as upstream OIDC IdP.
3. Calls are Core, but there is no member `/api/weave/calls` and no `com.weave.call.*` event where Matrix/MatrixRTC semantics exist. Matrix is the facade; LiveKit is the first replaceable RTC transport/SFU.
4. Weaver is not a collaboration domain. `Agent Runtime Control` is a narrow Weave control-plane context. Members interact with OpenClaw through Matrix and governed Weave MCP tools.
5. Use upstream OpenClaw for agent loop, session semantics, Matrix channel, workspace/memory/skill loaders, MCP client, and native approvals. Do not create a second approval inbox, custom `weave-chat`, or competing memory engine.
6. A Weaver cell owns **zero durable bytes**. A killed cell must be reconstructible on another node without reading the old cell.
7. RuntimeProfile is a signed desired-state projection, never domain authorization. Every MCP side effect is freshly authenticated, authorized, policy-checked, idempotent and audited by Weave.

## Normative contracts to install first

Copy/adapt the proposal contracts into `weave-specs` and make them the only active target:

- Product Constitution
- ADR: Agent Runtime Control
- ADR: OpenClaw-native approvals
- ADR: immutable WebDAV workspace materialization
- ADR: stateless Weaver cell
- ADR: Matrix-native Calls Core
- `RuntimeProfile v1` JSON Schema
- `WorkspaceManifest v1` JSON Schema
- `RuntimeStateCheckpoint v1` JSON Schema
- `MatrixRTC Profile 0` YAML

Mark the old `Weaver Governed PA` and proprietary Calls contracts as superseded. Historical files may remain only with an unmistakable replacement notice and link.

## Work package A — truly stateless Weaver

### A1. External authorities

Implement explicit interfaces and adapters for exactly these storage classes:

| Authority | Durable content |
|---|---|
| User Files/WebDAV | `AGENTS.md`, `SOUL.md`, `USER.md`, `IDENTITY.md`, `TOOLS.md`, optional `HEARTBEAT.md`/`BOOT.md`, one-time `BOOTSTRAP.md`, `MEMORY.md`, `memory/**`, approved `skills/**`, user resources |
| Weave Control Store | entitlement/profile refs, desired state, exclusive lease and fencing epoch, workspace manifest/authoritative `HEAD`, active checkpoint ref, bootstrap marker, wake dedupe/outbox, conflicts and audit metadata |
| Encrypted per-user RuntimeStateStore | complete pinned `$OPENCLAW_STATE_DIR` checkpoint: sessions/SQLite, native approval state, channel/plugin state, stable Matrix device/crypto state, recovery journal |
| Secret Manager/KMS | OAuth, Matrix, DAV, provider credentials, recovery secrets, data-encryption keys |
| Cell-local ephemeral storage | generated config, staged workspace, copy-on-write overlay, caches/indexes, processes and short-lived tokens only |

The first RuntimeStateStore adapter may use an encrypted per-user block volume, but it must be externally addressed, exclusively leased, control-plane-owned and replaceable. It is not a pod-owned persistent disk.

### A2. Lease, fencing and state machine

Implement one exclusive per-person lease with a monotonically increasing fencing epoch. Every durable control write, WebDAV activation, journal append and checkpoint commit must carry the epoch. Reject stale writers even when an old process remains alive.

Required lifecycle:

`ABSENT → PROVISIONING → STOPPED → ACQUIRING_LEASE → RESTORING → MATERIALIZING → READY ↔ BUSY → COMMITTING → STOPPING → STOPPED`, with fail-closed `DEGRADED`, `SUSPENDED`, and `REVOKING` paths.

Revocation wins every race: block new runs and side effects, revoke/wipe mounted credentials, fence the old lease, commit only policy-permitted recovery state, and stop compute.

### A3. Immutable workspace revisions

Treat direct WebDAV edits as drafts. Build and validate an immutable `WorkspaceManifest` with ETags, SHA-256 hashes, data class, owner, write policy, size/content type, skill metadata and signature. Activate it only by compare-and-swap of authoritative Control Store `HEAD`. A run pins one profile, workspace revision, skill-set hash and checkpoint generation; it observes no mid-run drift.

Materialization rules:

- stage into a fresh directory;
- reject absolute paths, traversal, symlinks/hardlinks, devices/FIFOs/sockets, case-fold collisions, reserved-name shadowing, oversized files and forbidden MIME types;
- verify manifest signature, ETags and hashes;
- atomically publish the read snapshot;
- use a per-run COW overlay;
- sync permitted changes with ETag preconditions into a new draft revision, then validate/sign/activate it;
- preserve conflict copies and expose `DEGRADED`; never silently overwrite.

### A4. Durable memory and checkpoint barriers

Do not reimplement OpenClaw memory. Persist its Markdown memory through the WebDAV workspace and keep local memory indexes rebuildable. Maintain an encrypted external write-ahead/recovery journal for run output and runtime state.

Commit barriers are mandatory at:

- end of each agent turn before acknowledging durable memory;
- before compaction;
- before final response completion when the response claims something was remembered;
- graceful stop, upgrade and scale-to-zero.

Never say “remembered” unless the relevant journal append or WebDAV revision is durable. If an authority is unavailable, fail visibly as `DEGRADED`.

### A5. Memory privacy and custom skills

Inject private `MEMORY.md` and daily memory only into the member's private main context, never group rooms or another user. Enforce organization retention/export/delete policy per data class.

Pilot custom-skill ingestion permits only reviewed `SKILL.md` plus allowlisted Markdown/text/JSON/YAML resources. Reject binaries, archives, symlinks, plugins and executable payloads. Scan, hash, sign, version and quarantine before activation. Block user skills from shadowing organization-managed/reserved skills. Pin the approved skill-set hash for the run. Runtime sandbox/network policy still applies; a workspace is not a security sandbox.

### A6. Separate destructive operations

Implement distinct commands and audit records for:

- reset conversation/session;
- reset RuntimeState checkpoint;
- rotate/log out Matrix device and crypto state;
- revoke provider/Matrix/DAV credentials;
- delete workspace memory;
- delete all personal agent data.

E2EE-destructive actions require step-up authentication, explicit irreversibility warning and a recovery/backup check. Runtime reset must never implicitly delete WebDAV memory.

### A7. Required Weaver tests

- kill a busy cell at every commit boundary and restore on a different node;
- prove no acknowledged memory loss and no dependence on old local files;
- split-brain/stale-fencing negative tests;
- exactly-once Matrix wake/dedupe and outbox replay;
- cross-user/tenant isolation and private-memory non-injection;
- WebDAV ETag conflict, partial upload, path/symlink/case collision and quota tests;
- checkpoint encryption, wrong-key, corruption, rollback and version-migration tests;
- custom-skill quarantine, shadowing and malicious-content tests;
- entitlement removal during wake/run/commit;
- scoped reset and Matrix recovery tests.

## Work package B — Matrix-native Calls

### B1. Frozen protocol profile

Implement Matrix v1.19 Native OAuth and `weave.matrixrtc/profile-0`. MatrixRTC is experimental; pin these exact PR heads:

| MSC | Commit |
|---|---|
| 4143 | `0c5f1877ef163c02521f22c710a18b49bb527f21` |
| 4195 | `a8464ae82ca85eae2ca1882cd8a38305a14b8bd2` |
| 4196 | `5add2f0c96974c4996a6e5e0907018117cbb5934` |
| 4075 | `39cc54743a4f2187fdee1a69909b4a84eb7af014` |
| 4310 | `67687dc381f56c626edf6e00a2bd2c5e2d04e56b` |
| 4140 | `cd878d211fc855951e79cbb940bd68c304eecc87` |
| 4354 | `74fc75e1dc1301230cc3fcb7435205bf4f567ef8` |

MSC4143 is authoritative for writes: `m.rtc.slot`, sticky `m.rtc.member`, `transports.published/can_subscribe`, and singular to-device `m.rtc.encryption_key`. The frozen MSC4195/4196 heads contain older `rtc_transports`, relation/disconnect fields and one plural key reference. Implement **dual-read, single-write**; preserve unknown fields; reject ambiguous data. Do not describe the union as a stable standard.

### B2. Native OAuth and discovery

From only `weave.example`, a foreign client must be able to:

1. discover the homeserver through `/.well-known/matrix/client`;
2. read `/_matrix/client/v1/auth_metadata`;
3. use Dynamic Client Registration where required;
4. authenticate with Authorization Code + PKCE S256 in the system browser through MAS → Keycloak;
5. verify with `/whoami`;
6. inspect `/versions` and discover authenticated RTC transports at the profile-selected stable or unstable Matrix endpoint;
7. join the MatrixRTC call without any Weave-specific member endpoint.

Never expose a LiveKit API secret or long-lived SFU token. Keep Matrix OAuth access token, OIDC ID token and Matrix OpenID credential as separate types in code.

### B3. Ruma facade and gap module

Pin/review Ruma at commit `ea3455221fd99985256b196866abb85e22ff4bdd`. Use upstream notification/decline and other available event types. Put missing Profile-0 wire types in one module/crate named `matrixrtc_wire`, using exact Matrix/MSC wire names and Serde/Ruma custom-event patterns. Dispatch custom types through Raw/generic event handling; do not fork `Any*Event` enums.

Every local type must have:

- golden JSON fixture at stable and unstable wire name;
- round-trip/unknown-field test;
- upstream tracking issue/reference;
- deletion criterion once an equivalent Ruma type is adopted.

The Ruma facade exposes standard Matrix endpoints/events, never `/api/weave/calls`.

### B4. RTC Authorizer

Accept a Matrix OpenID credential as identity input. Verify it through Matrix federation OpenID userinfo, then independently verify current room membership, open slot, sticky member/device binding, Weave organization policy and requested transport permissions. Identity proof alone is not room authorization.

Issue a short-lived LiveKit JWT bound to:

- Matrix user ID and room ID;
- slot ID and unique member ID;
- pseudonymous LiveKit alias/participant identity;
- publish/subscribe/room-create permissions;
- policy revision, issued-at, expiry and nonce.

Make replay impossible, separate publish/create from subscribe, hide existence details in failures, rate-limit, audit without tokens, and keep federated authorization `Guarded` until a standard/proven attestation path exists.

### B5. Flutter and native OS integration

Keep protocol ownership in the shared Rust/Ruma core: discovery, OAuth, sync/E2EE, MatrixRTC state and keys. Keep media ownership in the LiveKit client. Add a thin idempotent `NativeCallCoordinator` for CallKit and Android Core-Telecom only:

- answer → join slot, authorize, start media;
- decline before join → `m.rtc.decline`;
- end after join → leave `m.rtc.member`, stop media;
- remote end → report ended to OS;
- hold/mute → local OS/media state unless a standard event exists.

OS UUIDs are local correlation IDs, never Matrix protocol IDs. The coordinator must not implement Matrix discovery/signaling or administer LiveKit rooms.

### B6. Encryption, recording and portability

Private calls require an encrypted Matrix room and MatrixRTC media E2EE. DTLS-SRTP alone is not an E2EE claim; document SFU-visible metadata. Recording/transcription is default-off and requires explicit participant-visible consent plus a declared trusted decrypting participant/boundary, or a separately named non-E2EE profile. Store governed artifacts through Files/WebDAV with retention/export/delete evidence.

Calendar contains a stable Matrix/slot reference, never a durable LiveKit credential. Provider change may move future calls; active calls use visible end/rejoin, not “lossless live migration”.

### B7. Required Calls tests

- foreign-client bootstrap from organization URL, DCR and PKCE negatives;
- golden fixtures for every pinned MSC, stable/unstable aliases and dual-read/single-write;
- scan proving no `com.weave.call.*` or member `/api/weave/calls` dependency;
- Element/reference-client interoperability;
- wrong room/member/device, stale sticky member, closed slot, role escalation, replay and expiry;
- local and federated authorization matrix;
- media-key distribution/rotation, cross-signed device and recovery;
- SFU receives ciphertext only for the private profile;
- STUN/TURN, reconnect, network handover and multi-device;
- physical iOS CallKit and Android Core-Telecom background/incoming/audio-route flows;
- consent, late join, revoke, indicators, artifact deletion and accessibility.

## Work package C — retire contradictions and legacy paths

Update README, domain context map, specs, issues and code so repository search has one answer:

- `Weaver Governed PA` → superseded by `Agent Runtime Control`;
- custom `weave-chat` → official OpenClaw Matrix channel;
- duplicated approval request/inbox → OpenClaw native lifecycle; keep only argument-bound Weave authorization evidence;
- pod/local persistent state → external RuntimeStateStore with lease/fencing;
- WebDAV-only “stateless” claim → four external authorities plus ephemeral cell;
- proprietary Calls API/events → Matrix v1.19 + MatrixRTC Profile 0;
- Keycloak as direct Matrix OAuth issuer → MAS protocol role with Keycloak upstream;
- “transport encrypted” → precise signaling/media/SFU/artifact truth table.

Legacy code may remain temporarily only behind an internal adapter with deprecation metadata, no member exposure, migration tests and a removal issue/date.

## Pull-request sequence

1. `weave-specs`: constitution, context map, ADRs, contracts, supersession notices.
2. `weaver`: issue #32 architecture, storage interfaces, lease/fencing, lifecycle and schemas.
3. `weave`: Matrix Native OAuth/MAS and Profile-0 projection contracts; retire public Calls API.
4. `weaver`: immutable workspace/materializer, checkpoint/journal, custom skills and cross-node proof.
5. `weave` + client: Ruma gap module, RTC Authorizer, LiveKit media and native coordinator.
6. Evidence PRs: interoperability, security, device/accessibility, DR and claim matrix.

Do not mix unrelated formatting or product expansion into these PRs.

## Definition of done

For every slice:

- normative requirement IDs and owning context are linked;
- schemas/contracts validate and are versioned;
- positive, negative, abuse, race, crash and degraded tests pass;
- secrets/token/content logging is reviewed;
- metrics, tracing, support-safe errors and runbook exist;
- migration/rollback and compatibility window are explicit;
- German and English front-door wording stays semantically aligned;
- evidence records commit, environment, test-data class, date, claim and known limitations;
- no `Ready` claim is made while MatrixRTC remains guarded or any release gate is red.

At the end, report: changed files by repository, ADR/requirement coverage, test commands/results, migrations, deliberately retained legacy paths with deletion criteria, risks still open, and exact follow-up issues. Do not report completion based only on documentation or mocks.



## Repository-specific handoff for this draft

This draft PR changes architecture and planning truth only. Before implementation starts:

1. merge or pin the matching canonical weave-specs decision and contract changes;
2. split work into the PR sequence above rather than landing a broad rewrite;
3. preserve current behavior behind guarded adapters only when a rollback or migration need is documented;
4. update issue #968 and subsequent child issues with requirement IDs, exact gates, owners and removal criteria;
5. keep this PR in Draft until corpus conformance and docs checks are green.
