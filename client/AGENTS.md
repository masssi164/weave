# Weave Client Agent Rules

Weave uses a feature-first clean architecture under `lib/features/<feature>/`, with shared cross-feature protocol/platform code living under `lib/integrations/<integration>/`. New work and refactors should follow those ownership boundaries even where older code still uses transitional folders like `models/` or top-level `providers/`.

Placement rules:
- `presentation/` for screens, widgets, and feature UI composition
- `presentation/providers/` for Riverpod providers, notifiers, and UI-facing controllers
- `domain/` for entities, use cases, and repository contracts
- `data/` for repository implementations, datasources, and DTOs

Integration placement:
- use `lib/integrations/<integration>/` for reusable auth/session/capability/protocol logic that is shared by multiple features
- keep the same `presentation/`, `domain/`, and `data/` split inside integrations so shared boundaries stay predictable
- move feature-specific transport mapping back into the owning feature once the code stops being reusable across features

Feature boundaries:
- Features may depend on `core/`, shared reusable UI, and `lib/integrations/`, but must not import another feature's `data/` layer directly.
- Cross-feature integration should go through domain contracts, app-level orchestration, or shared core abstractions.
- Integrations may depend on shared app foundations such as `core/`, `features/auth/`, and `features/server_config/`, but must not depend on feature presentation code or feature-specific entities they are meant to serve.

App-config alignment:
- Members supply only the public organization URL or an invitation. The generated User `GET /api/platform/config` operation returns the canonical OrgManifest v2; consume its validated issuer, client ID, User API and Weave Matrix facade endpoints.
- Infrastructure renders installation configuration behind that accepted contract. Do not infer provider hosts, add manual issuer/client overrides or fetch a second bootstrap contract.
- app redirect URIs are `com.massimotter.weave:/oauthredirect` and `com.massimotter.weave:/logout`
- user-facing Files and Calendar operations consume the generated Weave User API; selected providers and their endpoints remain behind server adapters
- Member onboarding and release qualification require validated HTTPS origins and preserved CA trust. HTTP protocol fixtures remain test-only and must not relax the product bootstrap contract.
- iPhone delivery requires two concurrent real iOS apps using the shared member scenario with two ordinary members and current shared-Space grants. Runtime fixtures carry the current run identity; do not compile per-run credentials or identities into reusable artifacts. macOS desktop checks remain manual-only. Physical preparation preserves the owner's app/session and observes a retained workspace or normal sign-in; owner HIL remains pending until the human reports it.

Accessibility is mandatory:
- interactive controls must provide at least `48x48` logical touch targets
- icon-only actions must have semantics labels
- complex widgets must expose a correct reading order
- composite controls should merge/group semantics when they should be read as one unit

Validation from `client/`:
- `flutter pub get`
- `flutter gen-l10n`
- `dart run build_runner build --force-aot --delete-conflicting-outputs`
- `dart format --output=none --set-exit-if-changed .`
- `flutter analyze --fatal-infos`
- `flutter test`

Monorepo alignment:
- Product acceptance features live in `../e2e/features/`; keep `../e2e/scenario_mappings.json` updated with client evidence.
- Live integration setup may use reviewed generated infrastructure configuration, but the actual Flutter member flow discovers bootstrap through the public organization URL and canonical OrgManifest v2.
- Do not reintroduce client-side provider secrets or direct provider calls.

## Global Weave agent baseline

- Write agent instructions, PRs, issues, code comments, and documentation in English unless an explicit localization file requires another language.
- Follow `docs/developer-handbook.md`, `docs/gitflow-pr-workflow.md`, `docs/weave-operating-model.md`, and relevant domain docs before coding, opening PRs, merging, or declaring work complete.
- If the user asks to finish a sprint/milestone, derive acceptance from GitHub issues/milestones, repo specs/tasks, docs, CI policy, and evidence; do not require the user to restate issue acceptance criteria.
- Use protected `main`, short-lived branches, exactly one `release-notes-*` label per PR, smallest meaningful local gates, green CI, fallback review evidence, and GitHub closure verification before reporting completion.
