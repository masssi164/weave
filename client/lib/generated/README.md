# Generated User API client

`user_api/` contains the generated User HTTP operations and transport models
from `contracts/openapi/weave-user-openapi.json`. It uses OpenAPI Generator
7.17.0, pinned by version and SHA-256 in `tools/generate_client_user_api.py`.
The script applies narrow, fail-closed transport corrections for optional
profile preferences, binary upload errors, and bodyless operations.

```bash
./gradlew generateClientUserApi
./gradlew checkClientUserApiFresh
```

The generated User client is separate from the Admin contract. App-specific
session binding, strict bootstrap validation, and feature/domain mapping live
outside `user_api/`.
