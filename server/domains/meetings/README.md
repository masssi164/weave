# Meetings ownership

This future-domain library owns its existing conversation-attached use case, HTTP handlers,
wire records and workspace capability declaration. It is implemented future scope, with no claim
of release inclusion or production readiness. The accepted full API paths and audit/error identifiers
remain unchanged; module ownership does not create a new public contract.

Core supplies shared authorization/audit contracts. The explicitly required Chat runtime supplies
the narrow conversation admission API and canonical membership recheck. This owner never reads
Chat entities, repositories or private adapters. Existing in-memory records are scoped by organization,
Space and conversation; this is not durable persistence. The historical default-channel alias remains
local to this future feature. Missing Chat selection is rejected rather than silently included.

Run owning units and actual Boot imports discovery separately:

```sh
./gradlew :server:domains:meetings:test
./gradlew :server:domains:meetings:contextTest
./gradlew :server:domains:check -Pdomains=chat,meetings
```

The context lane installs the actual required Chat graph and uses its JNI build. Ordinary future-domain
unit tests use the narrow admission double and do not start App. Full API and PostgreSQL qualification
remain separate App lanes. See [Server ownership](../../../docs/architecture/server-domain-ownership.md).
