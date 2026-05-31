# ADR-003: Weaver runtime isolation preflight

## Status

Accepted as Sprint 12 preflight; runtime execution remains disabled by default.

## Decision

The first self-hosted Weaver preflight path is per-user, short-lived containers with deny-by-default egress, read-only base filesystem, explicit writable scratch, quota cleanup, audit publication, and support-safe evidence. Docker rootless may reduce host risk but is **not a strong sandbox by itself**. The hardening path evaluates gVisor/runsc or Firecracker for stronger kernel isolation before broader runtime claims.

## Alternatives considered

- Docker rootless only: insufficient as a security boundary by itself.
- gVisor/runsc: stronger syscall isolation, higher operational complexity.
- Firecracker: strong VM boundary, more image/network lifecycle complexity.
- Long-lived per-user containers: easier caching, worse cleanup and evidence surface.

## Required controls before enablement

- per-user runtime profile and workspace identity;
- deny-by-default egress with declared capability grants;
- filesystem isolation and no implicit host mounts;
- lifecycle cleanup, CPU/memory/disk quotas, and stale-session reap;
- SecretRef/OAuth broker only, never raw secrets in runtime profiles or tool results;
- audit events for install, grant, invoke, deny, cleanup, and support bundle export;
- support bundle redaction for prompts, payloads, tokens, cookies, private keys, and provider bodies.

## Product boundary

Runtime profiles are `disabled`, `profile_incomplete`, `blocked`, or `ready_for_spike`; no marketplace, broad third-party execution, or autonomous production writes are in Sprint 12 scope.
