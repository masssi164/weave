# Contract and docs index

This index classifies contract-like Weave docs so product truth, implementation evidence, and release evidence do not blur.

| Class | Meaning | Examples | Rules |
| --- | --- | --- | --- |
| Conceptual SSOT | Product architecture and vocabulary | [Product architecture](product-architecture.md), [Glossary](glossary.md), [Repository boundary](repository-boundary.md) | Must not encode live personal/operator configuration or unsourced legal claims. |
| Registry contract | Central registries with required fields | [Adapter/provider registry](architecture/canonical-domain-adapter-registry.md), [MCP/domain-tool action registry](architecture/mcp-domain-tool-action-registry.md), [Domain registry v1](domain-registry-v1.md) | Missing provider links require explicit `unknown/not-yet-selected`; action authority belongs only to tool actions. |
| Implementation contract | Repo-local product contracts used by code/tests | [Acceptance contracts](acceptance-contracts.md), `server/src/main/resources/contracts/`, `e2e/scenario_mappings.json` | Must map to executable or fixture-backed gates where feasible. |
| Operator contract | Deployment, readiness, migration, recovery, and support evidence | [Bootstrap foundation](bootstrap-foundation-contract.md), [Provider portability](architecture/provider-portability.md), [Operator recovery limits](operator-recovery-known-limitations.md) | Must keep destructive/live actions approval-gated and support-safe. |
| Release/evidence contract | Claim, release, and readiness proof | [Quality evidence](quality-and-evidence.md), [Release notes](release-notes/index.md), `release/` | Must not outpace evidence; release notes stay out of the README. |
| Legal-risk note | Sourced risk context, not legal advice | [Jurisdiction legal-risk note](jurisdiction-legal-risk-note.md) | Specific regimes may appear here as context only, never as product guarantees. |
| Transitional spec projection | Repo-local projection of pinned spec corpus or sprint tasks | `specs/`, `.specify/`, [Spec-driven development](spec-driven-development.md) | If repo and spec corpus disagree, create explicit conformance/spec-change work. |

## Drift rules

- README is a compact product front door, not a release wall or large matrix host.
- Canonical domains define semantics; adapters implement domains; tool actions own operation semantics.
- ApprovalReceipt appears only for MCP/domain-tool actions.
- Weaver is an OpenClaw-derived per-user harness/agent profile embedded by Weave policy, the Weave channel, and Weave MCP/domain tools; raw OpenClaw configuration or unrestricted runtime tools must not bypass those product boundaries.
- Public claims require evidence links or explicit unknown/not-yet-selected markers.
