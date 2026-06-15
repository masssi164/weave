# ADR-003: Weaver runtime isolation preflight

## Status

Accepted as Sprint 12 preflight; runtime execution remains disabled by default.

## Decision

The first self-hosted Weaver preflight path is a managed runtime pool with one logical runtime context per active user/trust boundary, signed `WeaverRuntimeProfile` input, deny-by-default egress, isolated workspace/state/memory, sidecar/job-runner execution for high-risk tools, quota cleanup, audit publication, and support-safe evidence. Per-user Docker containers and MicroVMs are high-isolation deployment options after production-readiness gates pass; they are not the v0.1 default product architecture. Docker rootless may reduce host risk but is **not a strong sandbox by itself**. The hardening path evaluates gVisor/runsc or Firecracker for stronger kernel isolation before broader runtime claims.

## Alternatives considered

- Runtime pool with logical isolation and sidecar/job-runner isolation: selected v0.1 scaffolding stance because it separates product invariants from a specific container lifecycle.
- Docker rootless only: insufficient as a security boundary by itself.
- Per-user Docker containers by default: too much operational and security destiny for v0.1 before gates prove patching, quotas, cleanup, incident response, and support-bundle safety.
- gVisor/runsc: stronger syscall isolation, higher operational complexity.
- Firecracker/MicroVMs: strong VM boundary, more image/network lifecycle complexity.
- Long-lived per-user containers: easier caching, worse cleanup and evidence surface.

## Required controls before enablement

- per-user runtime profile and workspace identity;
- one active user/trust boundary per logical runtime context, with inactive users represented only by stored state/profile until activated; dedicated containers/MicroVMs are optional backing sandboxes after gates pass;
- separate state, workspace, and agent directory per runtime;
- deny-by-default egress with declared capability grants;
- internal network access only to Weave API, Weave MCP Gateway, and allowed channel/MCP proxies;
- filesystem isolation and no implicit host mounts;
- lifecycle cleanup, CPU/memory/disk quotas, and stale-session reap;
- profile reload/restart on admin changes and rollback to the previous signed RuntimeProfile;
- SecretRef/OAuth broker and short-lived runtime token only, never raw secrets in runtime profiles or tool results;
- audit events for install, grant, invoke, deny, cleanup, and support bundle export;
- support bundle redaction for prompts, payloads, tokens, cookies, private keys, and provider bodies.

## Product boundary

Runtime profiles are `disabled`, `profile_incomplete`, `blocked`, or `ready_for_spike`; no marketplace, broad third-party execution, or autonomous production writes are in Sprint 12 scope.
