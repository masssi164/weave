# Weave

Weave gives organizations a collaboration layer that stays understandable and controllable when legal, jurisdictional, contractual, operational, cost, security, governance, or provider requirements change.

It combines stable member-facing collaboration with admin/operator control over provider posture, adapter exchange, evidence, readiness, audit, and governed AI assistance.

<p align="center">
  <img src="client/assets/images/weave_logo.png" alt="Weave logo, an interlaced blue and teal knot" width="220">
</p>

## Vision

Collaboration should belong to the organization, not to whichever provider happens to host chat, files, calendars, tasks, identity, or AI this year.

Weave keeps product semantics stable while the implementation underneath can be inspected, governed, staged, replaced, or migrated with evidence. Members work in Weave. Admins and operators see which providers implement each capability, what posture they carry, what is ready, what is guarded, and which next action is safe.

## Problem

Organizations regularly need to react to changing requirements:

- legal and jurisdictional exposure;
- provider lock-in and pricing shifts;
- contractual or compliance pressure;
- security and hosting requirements;
- governance, audit, and approval expectations;
- migration, rollback, and support realities.

Typical suites hide those concerns behind provider-specific surfaces. Weave makes them explicit without turning everyday collaboration into an infrastructure console.

## Solution

Weave separates the product from the implementation:

- **Canonical domains** define stable Weave capabilities such as chat, files, calendar, tasks, identity, search, audit, and admin/control-room.
- **Adapters and providers** implement those domains and carry posture, readiness, caveats, evidence, and migration boundaries.
- **MCP/domain tools** are the action surface. Reads, writes, sends, deletes, provider switches, migrations, approvals, audit, and evidence belong to tool actions, not to domains or adapters by themselves.
- **The Admin Control Room** shows provider posture, readiness, support-safe diagnostics, policy preview, and next safe actions.
- **Weaver** is the governed AI assistant line: a per-user OpenClaw-derived harness/agent that is reached through the Weave channel and can act only through Weave-provided MCP/domain tools.

## Weaver

Weaver is separate from Weave, but built for it.

The intended product model is:

- every enabled user gets a governed Weaver agent/runtime boundary;
- the Weaver runtime is an OpenClaw-derived clone/harness profile, generated from Weave policy rather than manually configured by the member;
- users reach Weaver through the stable **Weave channel**;
- Weave exposes an MCP server with allowed domain tools;
- Weaver calls those Weave tools, and Weave routes the action through the appropriate domain, adapter, provider, approval policy, and audit path.

That keeps AI assistance inside Weave’s product and governance boundary: user rights plus organization-whitelisted capabilities decide what the assistant can see or do.

## Product principles

- **Stable collaboration semantics:** member UX speaks Weave, not provider topology.
- **Provider exchange with evidence:** provider replacement is supported through contracts, posture, caveats, migration plans, and readiness gates.
- **Action-specific governance:** authority lives on MCP/domain-tool actions with risk, audit, and ApprovalReceipt policy.
- **Support-safe operations:** diagnostics and evidence avoid raw secrets, private prompts, member payloads, and raw provider internals.
- **Clear jurisdiction posture:** legal and jurisdictional risks are described as sourced context and adapter/provider exposure, with stronger claims gated by evidence.
- **Accessibility-aware product work:** accessible collaboration is a design and evidence requirement, not a decorative release checkbox.

## Product screenshots

Screenshots are checked-in product assets for current dogfood-ready paths and should be read as UI evidence for those paths, not as broad availability claims for guarded capabilities.

- [Setup start](docs/assets/marketing/01-setup-start.svg)
- [Custom Weave chat](docs/assets/marketing/03-chat-room.svg)
- [Files and documents](docs/assets/marketing/04-files-documents.svg)
- [Settings and readiness](docs/assets/marketing/05-settings.svg)

## Repository layout

- `client/` — Flutter member UX and client contracts.
- `server/` — Weave-owned domain facades, authorization, audit, and provider boundaries.
- `admin-console/` — admin readiness, policy, setup, and control-room surfaces.
- `infra/` — Docker/OpenTofu operator stack, profiles, backup/restore, runtime lifecycle, and support bundles.
- `e2e/` — product-language scenarios and sanitized evidence mappings.
- `specs/` — product specifications, plans, tasks, and fixtures.
- `docs/` — product, architecture, operator, developer, release, and evidence docs.
- `release/` — release manifests, provider-lab evidence, and compatibility metadata.

## Read next

- [Glossary](docs/glossary.md)
- [Product architecture SSOT](docs/product-architecture.md)
- [Canonical-domain adapter/provider registry](docs/architecture/canonical-domain-adapter-registry.md)
- [MCP/domain-tool action registry](docs/architecture/mcp-domain-tool-action-registry.md)
- [Contract and docs index](docs/contract-docs-index.md)
- [Jurisdiction and legal-risk context](docs/jurisdiction-legal-risk-note.md)
- [Release notes](docs/release-notes/index.md) and [quality evidence](docs/quality-and-evidence.md)

## Local gates

Use the smallest meaningful subset for a change, and `./gradlew ci` for cross-stack work.

```bash
python3 tools/product_architecture_claim_guard.py
python3 tools/domain_registry_check.py
python3 tools/docs_check.py
./gradlew acceptanceContract docsCheck releaseEvidenceCheck --console=plain
git diff --check
```

Follow the [developer handbook](docs/developer-handbook.md), [PR workflow](docs/gitflow-pr-workflow.md), and [operating model](docs/weave-operating-model.md).
