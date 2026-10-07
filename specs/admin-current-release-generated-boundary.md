# Current-release Admin generated-client boundary

Status: implementation conformance for the pinned Weave Specification Corpus
`71a2093d91ad300bc733ede66080bddc90f97e60`, especially
`steering/release-2026-10-product-consolidation.md` and
`steering/product-constitution.md`. This packet does not independently define
product scope.

The active Admin Console invokes authorized Admin operations and transport
models from the server-generated Admin OpenAPI artifact. Its credential-free
pre-login platform configuration lookup uses the separately generated User
client and `PlatformConfigResponse` model, without turning an Admin session
into a User session. Organization setup, invitations,
effective policy, provider category/readiness and support-safe audit remain
available through that client and a separately authorized Admin session.

Private Runner and broad ARC Cell orchestration are deferred from #1470. The
historical Agent Runtime panel must not remain an active Admin UI path backed
by handwritten `/api/admin/agent-runtimes/**` requests and parallel transport
DTOs. Remove that panel and transport from the current Admin bundle while
preserving the existing server-side security and integrity implementation,
tests and Git history for the separately gated later portfolio. Do not add
Agent Runtime routes to the current Admin OpenAPI artifact merely to keep an
out-of-scope UI panel working.

Regression checks assert that the Admin UI source has no handwritten normal
Weave HTTP path builder or Agent Runtime control and that its supported current
operations still compile and pass with the generated Admin and public User
clients. Existing
negative HTTP/security probes and future optional deferred-feature tests may
continue outside the current #1470 Admin acceptance claim.
