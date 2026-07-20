---
id: WEAVE-SPEC-0009
title: Domain-first MCP tools
version: 0.1.0
status: proposed
domain: weaver-mcp
owner: weave-security-compliance-lead
github_issue: null
supersedes: []
depends_on:
- WEAVE-SPEC-0000
acceptance_features:
- e2e/features/weave_spec_0009_acceptance.feature
evidence_gates:
- ./gradlew specContract
- ./gradlew acceptanceContract
---

# Feature 0009: Domain-first MCP tools

> Obsolete as independent product/domain truth. The pinned corpus ADR
> `WEAVE-ADR-0003` and `WEAVE-DOMAIN-AGENT-RUNTIME-CONTROL` now own this meaning.
> This packet remains temporarily as repo-side acceptance traceability.

Corpus sources: `WEAVE-STEERING-SPEC-KIT-OPERATING-MODEL`, provider portability principles, domain context map.

Weaver-facing MCP tools expose Weave domain capabilities, not provider adapters. Names, policies, audit receipts, approvals, and runtime profile grants use domain verbs and nouns (`calendar.search_events`, `files.share_item`, `chat.send_message`). Provider references remain support-safe evidence behind Weave server/domain facades.

2026-06-12 Northstar amendment: domain-first MCP naming is a hard gate in both Weave and Weaver. Provider-first, adapter-first, vendor-prefixed, or raw service tool names must fail lint/review unless explicitly documented as redacted operator-only evidence outside member/runtime discovery.


## Acceptance

- Tool taxonomy is organized by Weave domains.
- Adapter/provider terms are hidden from user-facing tool names and policy grants.
- Provider evidence may be captured only as redacted `ProviderRef`/capability evidence behind the facade.
