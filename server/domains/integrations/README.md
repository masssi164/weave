# Integrations and interoperability runtime ownership

Owns the existing connector-manifest validation and internal boundary, Slack/Teams sandbox
orchestration, HTTP adapters and wire values, Slack signature verification and secret resolver
port, consent/write-audit envelopes, provider metadata, typed settings and connector schema.
Provider implementations live beneath `provider.slack`; shared membership and security stay Core.
The former App connector/interop service, controller, model and configuration locations are retired.
Sandbox dry-run output remains explicit, provider credentials are not delivered to members, and
no marketplace or production provider delivery is invented. This remains future release scope.

This is a real Gradle project with module-local `AutoConfiguration.imports`; it has no App dependency.
Final release exclusion requires selected App composition, which remains guarded during extraction.

From repository root:

```sh
./gradlew :server:domains:integrations:check
```

Owning units and actual Boot discovery run in separate `test` and `contextTest` lanes. Tests supply
real shared ORM prerequisites, inspect actual handler mappings, prove sibling exclusion and non-web
serving absence. Combined API, authorization, PostgreSQL and application gates remain required.
