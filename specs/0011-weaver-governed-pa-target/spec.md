---
id: WEAVE-SPEC-0011
title: Historical Weaver target conformance projection
version: 0.2.0
status: deprecated
domain: agent-runtime-control
owner: weave-product-lead
github_issue: 1177
supersedes: []
depends_on:
  - WEAVE-SPEC-0007
acceptance_features:
  - e2e/features/weave_spec_0011_acceptance.feature
evidence_gates:
  - ./gradlew specCorpusConformance
  - ./gradlew acceptanceContract
---

# Historical Weaver target conformance projection

## Supersession boundary

This repo-local packet is retained only as implementation conformance evidence for stable
scenario mappings. Product and domain truth lives in the pinned corpus at
`domains/agent-runtime-control/spec.md`. The old `weaver-governed-pa` bounded context and
reusable `ApprovalReceipt` product model are superseded.

Weaver remains the product capability and OpenClaw-derived runtime implementation. Agent Runtime
Control is the Weave bounded context that owns entitlement references, signed RuntimeProfiles,
cell lifecycle, workspace materialization revisions, approval challenges, revocation, and
support-safe audit correlation. Collaboration objects and domain side-effect authorization stay
with their owning domains.

## Conformance requirements

- Keycloak group/capability state and organization policy gate runtime entitlement; revocation
  wins over queued work.
- Each cell is isolated per person, disposable, and reconstructable from canonical WebDAV
  workspace content plus encrypted external runtime state.
- A current signed RuntimeProfile constrains runtime configuration and discovery but cannot grant
  a domain permission.
- Guarded actions use authenticated Matrix/OpenClaw resolution and short-lived signed single-use
  ApprovalDecisionEvidence bound to an exact challenge, action, and argument digest.
- Every receiving domain independently validates both member and MCP workload principals,
  entitlement, rights, policy, object scope, tool contract, arguments, expiry, and revocation.
- The owning domain atomically claims the evidence `jti` with an operation intent, uses one
  canonical idempotency key, reconciles uncertain outcomes, and writes immutable ActionEvidence
  only for the final observed result.
- Heartbeat and wake behavior is deduplicated, fail-closed, and support-safe. No raw credentials,
  provider payloads, private memory, or local operator configuration enters evidence.

## Acceptance and evidence mapping

The stable `WEAVE-SPEC-0011` scenario tags remain historical identifiers while their bounded
context is recorded as `agent-runtime-control`. They prove conformance to the pinned corpus and
must not be cited as an independent product specification.
