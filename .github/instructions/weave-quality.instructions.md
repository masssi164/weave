---
applyTo: "**"
---

# Weave quality gate instructions

When reviewing or editing Weave changes:

- Follow strict GitHub Spec Kit flow for product/domain changes: specify -> plan -> tasks -> implement.
- Require repo-local spec references and mapped Gherkin acceptance contracts before product claims merge.
- Use canonical evidence vocabulary: `evidenceMode` is `offline-spec` or `live-runtime`; provider/domain `realityLevel` follows `docs/product-reality-foundation.md`.
- Treat `offline-spec` / `contract_only` fixture evidence as insufficient for live-runtime, release-ready, or customer-ready claims.
- Require a named Fachveto owner for product/spec, architecture, security/privacy, accessibility, provider, release, or evidence risk.
- Use GitHub Copilot review first while available; if unavailable or insufficient, require a matching subagent reviewer with equivalent or stronger capability.
- Keep Massimo's OpenClaw agent hierarchy and personal runtime configuration out of product repo files.
- Never include secrets, raw tokens, raw provider payloads, personal OpenClaw config, or unsafe diagnostics in checked-in evidence.
