# ADR-003: Weaver runtime isolation preflight

## Status

Superseded by the pinned Agent Runtime Control specification and
`weave-specs/architecture/adr/0008-agent-runtime-control.md`. This file records the earlier
container-isolation preflight only; it is not a current storage or profile contract.

## Decision

The retained isolation findings remain valid: runtime cells are short-lived, deny egress by
default, use a read-only base filesystem plus ephemeral scratch, enforce quotas, and publish
support-safe audit evidence. Docker rootless may reduce host risk but is **not a strong sandbox by
itself**. gVisor/runsc or Firecracker remain candidate stronger isolation adapters.

The accepted replacement tightens the storage boundary: a cell owns zero durable bytes. WebDAV
holds allowlisted portable workspace content, encrypted RuntimeStateStore holds runtime state,
Secret Manager holds credentials, and Agent Runtime Control holds lifecycle/profile bindings.

## Alternatives considered

- Docker rootless only: insufficient as a security boundary by itself.
- gVisor/runsc: stronger syscall isolation, higher operational complexity.
- Firecracker: strong VM boundary, more image/network lifecycle complexity.
- Long-lived per-user containers: easier caching, worse cleanup and evidence surface.

## Required controls before enablement

- one entitlement-bound RuntimeCell and signed RuntimeProfile v2;
- one active user/trust boundary per runtime context/container, with inactive users represented only by stored state/profile until activated;
- no durable cell-local state, workspace volume, session volume, or agent directory;
- deny-by-default egress with declared capability grants;
- internal network access only to Weave API, Weave MCP Gateway, and allowed channel/MCP proxies;
- filesystem isolation and no implicit host mounts;
- lifecycle cleanup, CPU/memory/disk quotas, and stale-session reap;
- profile revalidation and cell replacement on current-policy changes;
- SecretRef/OAuth broker and short-lived runtime token only, never raw secrets in runtime profiles or tool results;
- audit events for install, grant, invoke, deny, cleanup, and support bundle export;
- support bundle redaction for prompts, payloads, tokens, cookies, private keys, and provider bodies.

## Product boundary

Runtime profiles are `disabled`, `profile_incomplete`, `blocked`, or `ready_for_spike`; no marketplace, broad third-party execution, or autonomous production writes are in Sprint 12 scope.
