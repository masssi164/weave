# Retired Sprint 32 Beta-path evidence

Status: historical, superseded on 2026-07-20 by the accepted Agent Runtime Control, workload-only MCP, and strict target-contract specifications.

The former Sprint 32 fixture modeled an in-process per-user Docker runtime, a human-facing MCP approval path, and broad offline Beta claims. Those assumptions are not valid evidence for the current architecture and the executable fixture/check have been removed.

Current evidence lives in:

- `docs/evidence/weaver-security-privacy-accessibility-report.md` for the ARC isolation, external encrypted state, Keycloak workload identity, lifecycle, and support-safety boundary;
- `docs/weave-contract-java-mcp.md` for workload-only MCP admission, exchanged bearer use, current-context revalidation, and the intentionally empty domain catalog;
- `infra/docs/weaver-runtime-lifecycle.md` for idempotent provisioning, reconciliation, revoke, and runtime-state deletion;
- the canonical `weave-specs/domains/agent-runtime-control/spec.md` and ADR 0012 for the binding target.

This page is retained only so historical Sprint 32 closure links explain why their former evidence no longer gates releases. It makes no current implementation or release claim.
