# Weave Repository Instructions

Weave uses a feature-first clean architecture under `lib/features/<feature>/`. New work and refactors should follow this layout even where older features still use transitional folders like `models/` or top-level `providers/`.

Placement rules:
- `presentation/` for screens, widgets, and feature UI composition
- `presentation/providers/` for Riverpod providers, notifiers, and UI-facing controllers
- `domain/` for entities, use cases, and repository contracts
- `data/` for repository implementations, datasources, and DTOs

Feature boundaries:
- Features may depend on `core/` and shared reusable UI, but must not import another feature's `data/` layer directly.
- Cross-feature integration should go through domain contracts, app-level orchestration, or shared core abstractions.

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
