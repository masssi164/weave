# Integrations Instructions

`lib/integrations/` hosts reusable external-service boundaries that are shared across multiple features.

Rules:
- place cross-feature auth, session, capability, and shared protocol orchestration here instead of inside a feature
- keep the same clean-architecture split inside each integration: `presentation/`, `domain/`, and `data/`
- expose reusable contracts/providers that features can consume without importing another feature's internals
- do not let integrations depend on feature presentation code or feature-owned domain models they are meant to serve

Ownership guidance:
- keep feature-specific mapping and user-facing state inside the owning feature
- keep server/session/account lifecycle rules in the integration when those rules are not feature-specific
- if a new integration becomes large enough to guide contributors, add an `AGENTS.md` inside that integration subtree
