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

`user_api/` contains the generated User HTTP operations and transport models
from `contracts/openapi/weave-user-openapi.json`. It uses OpenAPI Generator
7.17.0, pinned by version and SHA-256 in `tools/generate_client_user_api.py`.
The script applies one deterministic correction for optional profile preferences:
the generator otherwise sends an empty map in an unrelated partial update.

```bash
./gradlew generateClientUserApi
./gradlew checkClientUserApiFresh
```

The generated User client is separate from the Admin contract. App-specific
session binding and feature/domain mapping live outside `user_api/`.
