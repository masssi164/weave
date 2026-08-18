# Native platform Compose status

This document records the executable operator boundary for the native-provider integration
tranche. Direct Compose owns ordinary lifecycle after one narrow invariant-preparation step.
`compose.sh` remains only for SecretRef/config rendering, provenance and resource ownership
verification, bounded production migration, and isolated cleanup. Preparation writes one
mode-0600, secret-free `.env.<environment>` descriptor with the environment overlay, profiles,
candidate/spec identity, and resource coordinates required by native Compose.

## Authority and provider model

Weave Server is the product authority for Files, Calendar, and Chat. The default provider selection
is `weave-native`; PostgreSQL/JPA owns canonical metadata and the native Files payload volume owns
binary content. Keycloak remains the identity/OAuth authority. PostgreSQL, Caddy, and the native
Files volume are technical infrastructure, not product providers. MCP remains a separate process
without independent product persistence or a Keycloak administrative credential.

Nextcloud, Synapse/MAS, and S3-compatible storage are optional southbound definitions behind the
`provider-nextcloud`, `provider-matrix`, and `storage-s3` profiles. The renderer derives profiles
from explicit provider selections; these services are absent from the native default. External
Matrix/Nextcloud selection and native Files S3 selection currently fail closed until their
manifest-bound IAM and file-based credential contracts are implemented and qualified.

## Development

Compose defines Keycloak as the only normal development dependency and keeps application
processes on the host. The deterministic realm import includes the development identity roles;
there is no second migration step. Use the root lifecycle for ordinary operation:

```bash
./gradlew devUp
./gradlew :server:bootRun
./gradlew :weave-mcp-server:bootRun
cd admin-console && npm run dev
```

The host Server and Keycloak use their local H2/dev-file stores. Compose does not start
PostgreSQL, Caddy, Server, MCP, or Admin Console in `dev`; Mailpit is an optional developer tool
rather than a default dependency. The host launch helpers remain available when a clean shell
needs the generated OIDC, key-file, provider, and loopback coordinates without sourcing a shell
fragment.

## Disposable E2E

Copy `environments/e2e.env.example` to a private reviewed file, replace every placeholder digest,
then run an isolated namespace:

```bash
cd infra/weave-workspace
WEAVE_E2E_STACK_SCOPE=isolated \
WEAVE_E2E_RUN_ID=<unique-run-id> \
WEAVE_ENV_FILE=/absolute/path/to/reviewed-e2e.env \
./compose.sh e2e up
```

E2E activates Mailpit with implicit TLS and the explicit services required by the complete product
flow. Its generated root, SecretRefs, ports, network, and volumes are derived from the run ID.
The realm import uses the same development identity-role projection as dogfood, so invitation
testing does not depend on a backup, receipt, or post-import FGAP migration. Cleanup remains
limited to the exact ownership-labeled namespace.

## Dogfood and production

Dogfood is resettable development state. `dogfoodUp` preserves its three session volumes;
`dogfoodReset` removes and recreates only those volumes while retaining the host-owned TLS
identity. Both start an immediately usable import-initialized realm:

```bash
./gradlew dogfoodUp
./gradlew dogfoodReset
```

Production retains the separately qualified, backup-gated fine-grained identity migration:

```bash
cd infra/weave-workspace
WEAVE_ENV_FILE=/absolute/path/to/reviewed-prod.env ./compose.sh prod keycloak-migration-apply
docker compose --env-file .env.prod up -d
```

Dogfood keeps Mailpit as persistent activation-sensitive infrastructure, requires implicit TLS, and
has no SMTP shared secret. Production requires a non-secret reviewed SMTP username and a mode-0600
`smtp-password` SecretRef mounted only into Keycloak File Vault. Both consume a reproducible,
secret-free realm render derived from the pinned semantic source. Production migration-only
services mount one temporary mode-0600 SecretRef; normal Keycloak, Server, and MCP services do not.
