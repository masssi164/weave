# Sovereign domain contracts

Weave treats product domains as the stable layer and provider adapters as replaceable services. This page is the human-readable companion to the machine-readable registry in `specs/0004-domain-registry/canonical-domain-registry-v1.json` and its server resource copy.

## What is locked

Every Wave 1 domain entry defines:

- `domainId` and Wave marker;
- user and organization objects;
- read and write capability keys;
- minimum open protocols;
- auth and identity assumptions;
- audit requirements;
- portability/export contract;
- jurisdiction and vendor exposure descriptor;
- Weaver tool mode: `none`, `read-only`, `approval-write`, or `governed-write`;
- primary and secondary adapter candidates.

The conformance gate `tools/domain_registry_check.py` fails when a Wave 1 domain misses those fields, when adapter reality levels drift, or when required Wave 2/later placeholders disappear.

## Wave 1 domains

Wave 1 currently covers the implemented product vocabulary: identity, people/contacts, spaces, chat, files, documents, calendar, boards/tasks, calls/meetings, decisions/audit, notifications, health/Control Room, and Weaver runtime metadata.

Adapters are listed in the registry with free/open-source/self-hostable paths first where realistic. Commercial or jurisdiction-sensitive candidates stay documented as candidates until adapter-specific evidence promotes them.

## Wave 2 and later placeholders

The registry intentionally keeps placeholders for AI runtime gateway, MCP/tool registry, search/indexing, notes, secrets, mail, and backup/export. A placeholder is not a shipped capability. It exists so product planning does not silently lose domains that Massimo identified as core to the sovereignty thesis.

Search and indexing are explicitly `derived_rebuildable`: they may accelerate discovery, but they are not the canonical source of truth for business records.

## Sovereignty and exposure model

The contract does not claim Weave is immune to legal process or that every deployment is fully sovereign. It requires every domain to record hosting model, provider operator, likely data residency and jurisdiction exposure, subprocessors where known, export limitations, and lock-in risk.

This makes exposure visible before admins bind adapters, promote migration paths, or allow Weaver tool use. It also keeps provider names in admin/operator posture rather than normal member UX.

## Evidence links

- Domain registry fixture: `specs/0004-domain-registry/canonical-domain-registry-v1.json`
- Server resource copy: `server/src/main/resources/canonical-domain-registry-v1.json`
- Registry gate: `tools/domain_registry_check.py`
- Canonical domain overview: [`docs/domain-registry-v1.md`](domain-registry-v1.md)
- Provider portability contract: [`docs/architecture/provider-portability.md`](architecture/provider-portability.md)
- Product claim matrix: [`docs/product-trust-provider-choice-claim-matrix.md`](product-trust-provider-choice-claim-matrix.md)
