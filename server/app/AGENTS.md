# Weave App composition

App is the only Boot executable. Read `server/AGENTS.md` and the root Java standard.
Use the repository root wrapper and `:server:app:` task paths. Shared build mechanics
remain under `server/gradle`; the parent resolves domain selection once.

App owns composition and application-wide entry points. Existing feature implementations
are still transitional here; move each with its tests, resources and dependencies into
its real domain, with module-local auto-configuration. Do not add optional business logic
to App or introduce domain-to-App dependencies. Keep the packaging guard until excluded
production classes, SDKs and controllers are absent from actual artifacts.

Files now registers its actual runtime through module-local Boot imports. The transitional
App scan excludes its business namespace, retaining Boot's test/auto-configuration filters.
Do not import Files private services to implement platform business behavior; use narrow
Core contributions and preserve API projections at the transport boundary. Partial App API/
schema exports reject until real selected composition is available, just like partial packaging.
