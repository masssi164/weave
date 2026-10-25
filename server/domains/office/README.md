# Office runtime ownership

This real Gradle library owns the existing Office HTTP adapter and wire records, application use cases,
provider ports/adapters, typed capability settings and module-local Boot imports. It depends on Core
identity, authorization, audit and capability contracts, with no App or sibling-domain implementation.
Its runtime imports apply only to servlet contexts; schema and operator tooling do not create handlers.

The former App `service/OfficeFacadeService`, `controller/OfficeController`, `model/office` and
`office` implementations are deleted from their old locations. Provider implementations live beneath
`office.provider`; business rules and values stay beneath `office.domain` where implemented.
No production entity or repository is invented for a feature that currently has no database authority.

This is a future feature, excluded from the intended Files/Calendar/Chat release. Full selected App
composition is still guarded while extraction continues. Existing incomplete or disabled provider
behavior remains explicit; compiling this project does not qualify it as production-ready.

Office retains its explicit disabled provider/session boundary; no document server bridge is invented.

From repository root:

```sh
./gradlew :server:domains:office:check
```

Owning units run separately from `contextTest`, which uses actual `AutoConfiguration.imports` discovery
and verifies real handlers, uniqueness, omitted siblings and non-web exclusion. Full HTTP/security
regressions stay in App's integration lane until selected application composition is complete.
