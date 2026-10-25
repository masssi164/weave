# Guest Access runtime ownership

Owns the existing guest contract/invitation HTTP adapters and values, explicit-policy service
and typed settings. Shared organization membership, invitations and authorization remain Core.
The former App guest service/controller/model/configuration locations are retired. Default denial,
restricted capability allowlist, pending invitation status and no implicit internal-user merge are
preserved. This limited future implementation does not establish durable guest provisioning or
production readiness; it is outside the Files/Calendar/Chat release.

This is a real Gradle project with module-local `AutoConfiguration.imports`; it has no App dependency.
Final release exclusion requires selected App composition, which remains guarded during extraction.

From repository root:

```sh
./gradlew :server:domains:guest:check
```

Owning units and actual Boot discovery run in separate `test` and `contextTest` lanes. Tests supply
real shared ORM prerequisites, inspect actual handler mappings, prove sibling exclusion and non-web
serving absence. Combined API, authorization, PostgreSQL and application gates remain required.
