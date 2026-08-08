# Native platform Compose status

This document records the executable operator boundary for the native-provider integration
tranche. The environment file is the authority for `COMPOSE_PROFILES`; the wrapper and direct
Compose commands therefore resolve the same graph. `compose.sh` remains required for initial
mutation because it enforces SecretRef permissions, provenance labels, resource ownership, and
isolated cleanup.

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

The supported development flow starts only Keycloak through the guarded infrastructure wrapper,
then runs the application processes on the host. The helpers validate and load the generated
public coordinates before invoking the corresponding Gradle `bootRun` task:

```bash
cd infra/weave-workspace
./compose.sh dev up
cd ../..
python3 infra/weave-workspace/scripts/run_host_dev_server.py boot --root infra/weave-workspace
python3 infra/weave-workspace/scripts/run_host_dev_mcp.py
cd admin-console && npm run dev
```

The host Server and Keycloak use their local H2/dev-file stores. Compose does not start
PostgreSQL, Server, MCP, or Admin Console in `dev`; Mailpit is an optional developer tool rather
than a default dependency. The direct Gradle tasks remain `:server:bootRun` and
`:weave-mcp-server:bootRun`; the helpers exist so a clean shell receives the exact generated
OIDC, key-file, provider, and loopback coordinates without sourcing a shell fragment.

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

E2E activates Mailpit with implicit TLS and the isolated RuntimeState proof service. Its generated
root, SecretRefs, ports, network, and volumes are derived from the run ID. Cleanup remains limited
to the exact ownership-labeled namespace.

## Dogfood and production blockers

The supported commands are:

```bash
cd infra/weave-workspace
WEAVE_ENV_FILE=/absolute/path/to/reviewed-dogfood.env ./compose.sh dogfood up
WEAVE_ENV_FILE=/absolute/path/to/reviewed-prod.env ./compose.sh prod up
```

The canonical Keycloak 26.7 File Vault is mounted by the shared Compose graph. A mode `0600`
`smtp-password` SecretRef is required in dogfood/prod; the rendered realm import may reference it
only through the vault alias, and no SMTP username secret exists.

Do not bypass these guards by putting a password in rendered realm JSON, a Compose environment, or
`kcadm` process arguments. Do not describe dogfood or production as ready until the Vault/import
and bootstrap-absence proofs pass.
