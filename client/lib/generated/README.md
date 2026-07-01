# Generated OpenAPI models

`openapi_models.dart` is generated from the server-owned artifact at
`contracts/openapi/weave-openapi.json`.

Regenerate it from the repository root:

```bash
./gradlew generateClientOpenApiModels
```

Check freshness without accepting changes:

```bash
./gradlew checkClientOpenApiModelsFresh
```

Do not edit the generated Dart file by hand. The server OpenAPI contract remains
the single source of truth; client feature repositories can adopt these models in
small follow-up PRs.
