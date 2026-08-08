# Native platform Compose status

This document records the executable operator boundary for the native-provider integration
tranche. It deliberately distinguishes what is runnable now from the remaining Keycloak bootstrap
work. The desired single-command `docker compose --env-file .env.<environment> up -d` interface is
not yet qualified: `compose.sh` remains required because it enforces SecretRef permissions,
provenance labels, resource ownership, and isolated cleanup.

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

Compose still defines Keycloak as the only normal development dependency and keeps application
processes on the host. The deferred FGAP migration executor is currently qualified only for
backup-gated dogfood/prod. Dev therefore remains deliberately fail-closed at the migration gate;
do not bypass it by fabricating a receipt. Once the separately reviewed disposable-environment
migration contract lands, the intended commands remain:

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
than a default dependency. Until the disposable migration contract is qualified, `up` leaves
Keycloak imported but returns a blocking migration error before host application readiness. The direct Gradle tasks remain `:server:bootRun` and
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
to the exact ownership-labeled namespace. The same deferred FGAP migration currently blocks this
lane rather than accepting a production backup proof outside its scope.

## Dogfood and production

The supported commands are:

```bash
cd infra/weave-workspace
WEAVE_ENV_FILE=/absolute/path/to/reviewed-dogfood.env ./install.sh dogfood
WEAVE_ENV_FILE=/absolute/path/to/reviewed-prod.env ./install.sh prod
```

Dogfood keeps Mailpit as persistent activation-sensitive infrastructure, requires implicit TLS, and
has no SMTP shared secret. Production requires a non-secret reviewed SMTP username and a mode-0600
`smtp-password` SecretRef mounted only into Keycloak File Vault. Both consume the same generated,
secret-free realm import. The inactive migration-only services mount one temporary mode-0600
SecretRef; normal Keycloak, Server, and MCP services do not. A valid receipt proves the temporary
bootstrap authority was deleted and negatively read back before application startup.
