# Core ownership

Apply root and Server instructions and the shared Java/Spring standard. Core contains only
independently justified identity, organization, authorization/security, foundational Spaces,
audit, operation intents, provider-binding/discovery and shared schema/readiness infrastructure.

- Pure values/ports remain in `model`; no Spring/JPA/provider dependencies there.
- Persistence packages belong to their actual capability, never a generic persistence project.
- No optional-domain implementation, SDK, controller, entity or repository dependency.
- Member/session revocation must work without Chat and preserve existing hashes/table semantics.
- Runtime wiring registers through module-local Boot imports. Do not add fake configuration to
  the model or broad scanning across optional domains.
- Run `:server:core:check`, `:server:core:model:check`, architecture and relevant PostgreSQL gates.
