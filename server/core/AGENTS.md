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

Platform capability policy evaluates installed domain declarations; each domain owns its grants,
readiness and configuration defaults. Absent contributions are absent internally, not synthetic
unavailable features. Preserve the accepted full-runtime wire projection until coordinated
reduced-runtime contracts are accepted and composed. Configuration classes construct beans;
substantive capability rules/readiness belong in concrete owning capability classes.

Profile values/ports, JPA and use cases belong to Identity; device hashing/revocation/storage
belongs to Security with domain-owned scope declarations. Shared Space adapters and HTTP error/
authentication adapters require actual imports discovery, including omitted-protocol tests.
Never add a Matrix error/path branch back into shared HTTP security.

Identity owns current-member queries, invitation values/ports/persistence/use cases and shared
profile/identity/invitation HTTP adapters. Mounted HMAC reference material loads at real serving
readiness or first use; the ordered readiness hook must still fail startup before serving or
startup workflows when the exact SecretRef is invalid. Construction for AOT never reads it.
