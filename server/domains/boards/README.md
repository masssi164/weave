# Boards runtime ownership

This real Gradle library owns the existing Boards HTTP adapter and wire records, application use cases,
provider ports/adapters, typed capability settings and module-local Boot imports. It depends on Core
identity, authorization, audit and capability contracts, with no App or sibling-domain implementation.
Its runtime imports apply only to servlet contexts; schema and operator tooling do not create handlers.

The former App `service/BoardsFacadeService`, `controller/BoardsController`, `model/boards` and
`boards` implementations are deleted from their old locations. Provider implementations live beneath
`boards.provider`; business rules and values stay beneath `boards.domain` where implemented.
No production entity or repository is invented for a feature that currently has no database authority.

This is a future feature, excluded from the intended Files/Calendar/Chat release. Full selected App
composition is still guarded while extraction continues. Existing incomplete or disabled provider
behavior remains explicit; compiling this project does not qualify it as production-ready.

Boards preserves local workspace behavior and gated OpenProject read sync, DAV Deck/Vikunja spike
adapters, authorization and required audit before mutations. The bound repository is required;
registry wiring no longer hides a missing owning adapter behind a synthetic pending status.
Existing settings are retained during extraction; no additional build-time provider selector is added.

From repository root:

```sh
./gradlew :server:domains:boards:check
```

Owning units run separately from `contextTest`, which uses actual `AutoConfiguration.imports` discovery
and verifies real handlers, uniqueness, omitted siblings and non-web exclusion. Full HTTP/security
regressions stay in App's integration lane until selected application composition is complete.
