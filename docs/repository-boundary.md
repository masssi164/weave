# Weave / Weaver repository boundary

This document prevents product/runtime drift between the two repositories.

## Weave repository owns

- Product semantics and canonical domains.
- Server-side canonical domain services, abstract adapter contracts, and current organization/domain adapter assignment.
- Adapter/provider registry, posture, caveats, migration paths, and readiness evidence.
- Flutter client member journeys and provider-neutral collaboration UX.
- Control/Admin: organization administration, user groups including Weaver-facing groups, adapter switching, policy preview/apply, diagnostics, support bundles, and claim gates.
- Optional infrastructure for full greenfield/base setup; attach-to-existing provider deployments remain valid when adapters are already present.
- MCP/domain-tool action registry, risk, ApprovalReceipt policy, audit/evidence payload boundaries, and support-safe tool semantics.
- The Weave MCP/domain-tool server that Weaver calls, starting with calendar and files tool families.
- Signed `WeaverRuntimeProfile` generation and projection: model aliases, channel projection, allowed MCP tools, sandbox/tool policy, CredentialRefs, revocation, rollback, and audit requirements.

## Weaver repository owns

- The OpenClaw-derived per-user AI harness/runtime.
- RuntimeProfile loading, signature/hash/expiry/revocation validation, generated config rendering, reload/restart/rollback handling, and member-mode lockdown.
- The `weave-chat` channel plugin implementation.
- Enforcement that member runtime config contains Weave channel/profile/CredentialRef data only, never provider-native channel config or raw provider secrets.
- Runtime-side tool/MCP denial and audit export for every model/channel/tool/MCP/reload/revocation/rollback decision.

## Shared contract

- The stable member communication path is Weave-owned at product/API level and Weaver-owned at channel-plugin/runtime level.
- Weave may mention `weave-chat` as the runtime plugin target, but must not pretend to implement OpenClaw runtime internals.
- Weaver may mention Weave domains, providers, and MCP tools as consumed profile/tool contracts, but must not become the source of truth for provider selection, domain semantics, approval policy, or audit evidence semantics.
- Provider-native transports stay Weave backend `providerRef` values. Normal member-mode Weaver config must not expose Matrix/Slack/Teams/iMessage/Telegram/etc. channel setup as an escape hatch.

## Review rule

Any change touching Weaver claims in Weave must answer: “Is this product/policy/tool-profile generation, or runtime/plugin enforcement?” If it is runtime/plugin enforcement, the implementation belongs in `masssi164/weaver` and Weave should carry only the contract and projection.
