# Weave App composition

App is the only Boot executable. Read `server/AGENTS.md` and the root Java standard.
Use the repository root wrapper and `:server:app:` task paths. Shared build mechanics
remain under `server/gradle`; the parent resolves domain selection once.

App owns composition and application-wide entry points. Actual domain runtimes own their
services, controllers, persistence, providers and resources through module-local imports.
Do not add optional business logic or domain-to-App dependencies. Core platform integration
also registers through Core imports; App scanning must not duplicate those handlers.

Production dependencies, schema export and isolated API export use the same resolver.
All-domain integration fixtures stay test-only. `verifySelectedRuntime` rejects excluded
transitive projects; `verifySelectedArtifact` inspects the executable libraries, manifest and
class ownership. Reduced API/schema outputs have selection-specific paths. Never substitute
hidden beans, skipped tasks or a manual export facade for actual artifact exclusion.

See the living ownership plan for current checks and blockers. The required closed workspace/
Matrix projection, remaining App Manuals declaration, schema initializer cutover and coordinated
client activation are unfinished; selected packaging alone does not qualify reduced product behavior.
