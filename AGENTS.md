# Weave Repository Instructions

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
- treat `../weave-inf` as the infrastructure SSOT for app OIDC and endpoint defaults
- the app OIDC client is infrastructure-managed as `weave-app`; do not reintroduce manual client-ID entry for the normal setup flow
- app redirect URIs are `weaveapp://login/callback` and `weaveapp://logout/callback`
- user-facing files defaults should derive to `files.<tenant_domain>`, while compatibility-sensitive storage fields may still use `nextcloud*` names internally
- local development stacks may legitimately use `http://` issuer and service URLs

Accessibility is mandatory:
- interactive controls must provide at least `48x48` logical touch targets
- icon-only actions must have semantics labels
- complex widgets must expose a correct reading order
- composite controls should merge/group semantics when they should be read as one unit

Validation:
- `flutter pub get`
- `flutter gen-l10n`
- `dart run build_runner build --delete-conflicting-outputs`
- `dart format --output=none --set-exit-if-changed .`
- `flutter analyze --fatal-infos`
- `flutter test`
