---
id: WEAVE-SPEC-0007
title: Historical governed-runtime conformance projection
version: 0.2.0
status: deprecated
domain: agent-runtime-control
owner: weave-security-compliance-lead
github_issue: 1177
supersedes: []
depends_on:
- WEAVE-SPEC-0001
acceptance_features:
- e2e/features/weave_spec_0007_acceptance.feature
evidence_gates:
- ./gradlew specCorpusConformance
- ./gradlew acceptanceContract
---

# Historical governed-runtime conformance projection

## Supersession boundary

This repository-local packet is implementation evidence, not product or domain truth. The pinned
Weave Specification Corpus owns the accepted contract in
`domains/agent-runtime-control/spec.md`, ADR 0012, the signed RuntimeProfile schemas, the signed
ApprovalDecisionEvidence schemas, and the ActionEvidence schema. The former
`weaver-governed-pa` bounded context and reusable `ApprovalReceipt` authority are obsolete.

The stable `WEAVE-SPEC-0007` identifier and acceptance feature remain temporarily so historical
implementation evidence can be mapped during cleanup. They must not be used to introduce a
second approval lifecycle or to authorize a provider side effect.

## Intent

Prove that Weave consumes the canonical Agent Runtime Control contract: optional per-person
Weaver cells are entitlement-bound, disposable, driven by a current signed RuntimeProfile, and
restricted to provider-neutral Weave domain tools.

## In scope

- Conformance evidence for current entitlement, signed profile validation, least-privilege tool
  discovery, support-safe audit correlation, and fail-closed revocation.
- An approval challenge resolved through the authenticated encrypted Matrix/OpenClaw path.
- Short-lived, signed, single-use ApprovalDecisionEvidence bound to the exact action and
  arguments.
- Independent authorization and idempotent operation claiming by the receiving Weave domain,
  followed by immutable ActionEvidence for the final observed result.

## Out of scope

- A Weave approval inbox or open approval-request state machine.
- A receipt, profile, entitlement, or MCP annotation granting domain permission.
- Raw provider credentials, APIs, payloads, or operator runtime configuration.
- Claims of an ACID transaction across an external provider.

## Functional requirements

- The runtime MUST consume only a current, trusted, signed RuntimeProfile for its person and
  organization.
- The MCP boundary MUST validate exact workload/member principals, audience, reduced scope,
  current entitlement, tool contract, and RuntimeProfile before domain dispatch.
- A guarded side effect MUST require valid single-use ApprovalDecisionEvidence, but the owning
  domain MUST still reauthorize identity, policy, rights, object scope, action, arguments,
  expiry, and revocation.
- The owning domain MUST atomically claim the evidence `jti` with an operation intent, preserve
  one idempotency key across retries, reconcile uncertain provider outcomes, and record final
  ActionEvidence.
- Runtime and support artifacts MUST remain support-safe and MUST NOT contain credentials or
  private provider payloads.

## RuntimeProfile projection model

RuntimeProfile is a signed, short-lived desired-state ceiling and correlation artifact. It
contains references and maximum permitted capabilities; it is not an authorization grant.
Profiles are verified through the canonical trust-discovery/JWKS boundary and fail closed on
tampering, expiry, revocation, wrong subject, wrong organization, or stale policy.
The current corpus defines `runtimeProfileHash` as the lowercase `sha256:` digest of the RFC 8785
JCS UTF-8 payload bytes, excluding the protected header and signature. Overlap-key re-signing
therefore preserves semantic identity while every payload change produces another hash.

## Initial tool set

Tool availability is derived from the current canonical capability registry and receiving-domain
authorization. This historical packet does not own or freeze a tool list.

## Acceptance mapping

- `@weave-spec-0007-runtime-profile-from-policy` remains historical conformance evidence for
  signed, policy-derived RuntimeProfile behavior.
- `@weave-spec-0007-tool-approval-receipt-fail-closed` is a stable historical tag; its current
  meaning is signed ApprovalDecisionEvidence plus independent domain authorization and immutable
  ActionEvidence, never reusable receipt authority.
