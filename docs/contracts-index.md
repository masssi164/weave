# Weave contracts index

Status: classification index for canonical, supporting, evidence, and historical contract-like docs.

## Canonical product/domain contracts

These documents define current implementation-facing product truth in this repo, subject to the pinned specification corpus.

| Document | Role |
| --- | --- |
| [Product architecture](product-architecture.md) | Product architecture SSOT for why, who, components, domains, adapters, Weaver, evidence, and integration path. |
| [Canonical terminology](glossary.md) | Shared vocabulary for docs, issues, PRs, and implementation language. |
| [Canonical domain registry v1](domain-registry-v1.md) | Human-readable registry rules, provider states, adapter declarations, and adapter status posture. |
| [Canonical domains](architecture/canonical-domains.md) | Domain purposes, canonical objects, provider candidates, and risks. |
| [Sovereign domain contracts](sovereign-domain-contracts.md) | Human-readable companion for Wave 1 domain evidence fields and exposure model. |
| [Provider replacement and anti-silo contract](provider-replacement-and-anti-silo-contract.md) | Provider exchange, migration, source-of-truth, loss reports, and support-safe diagnostics. |
| [Governed Weaver runtime security contract](governed-weaver-runtime-security-contract.md) | Weaver policy, runtime, tools, approvals, isolation, and OpenClaw projection boundary. |

## Supporting contracts

These docs support current slices or domain-specific implementation without replacing canonical vocabulary.

- [Admin-suite readiness and setup contract](admin-suite-readiness-setup-contract.md)
- [Organization embedding contract](organization-embedding-contract.md)
- [Identity provisioning strategy](identity-provisioning-strategy.md)
- [Space anchor contract](space-anchor-contract.md)
- [Workflow context contract](workflow-context-contract.md)
- [Weave Control bootstrap-to-client contract](weave-control-bootstrap-to-client-contract.md)
- [Bootstrap foundation contract](bootstrap-foundation-contract.md)
- [Chat provider switch contract](chat-provider-switch-contract.md)
- [Matrix Chat migration proof boundary](matrix-chat-migration-proof.md)
- [Provider portability contract](architecture/provider-portability.md)
- [No-unaccounted-data-loss portability contract](architecture/no-unaccounted-data-loss.md)
- [Weaver OpenClaw-derived runtime profile](architecture/weaver-openclaw-profile.md)

## Evidence and claim-control docs

These docs record proof, claim boundaries, or release readiness. They are evidence artifacts, not new product vocabulary.

- [Product trust and provider-choice claim matrix](product-trust-provider-choice-claim-matrix.md)
- [Product reality foundation](product-reality-foundation.md)
- [Free provider lab](free-provider-lab.md)
- [v0.1 golden path](v0.1-golden-path.md)
- [v0.1 release notes](release-notes/v0.1.md)
- [Unreleased notes](release-notes/unreleased.md)
- Sprint closure reports and release evidence reports under `docs/`.

## Historical or research-oriented docs

These may inform future work, but cannot silently override canonical contracts.

- `docs/research/**`
- Older sprint plans/closure reports once superseded by merged canonical docs and issue state.
- Provider spike artifacts and candidate comparisons until promoted by adapter evidence.

## Classification rule

If a contract-like doc conflicts with `docs/product-architecture.md`, `docs/glossary.md`, `docs/domain-registry-v1.md`, the machine-readable domain registry, or the pinned specification corpus, treat the conflict as a spec-change or conformance-fix task. Do not let implementation convenience redefine domain meaning.
