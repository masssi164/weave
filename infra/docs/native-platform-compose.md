# Native platform Compose status

This document records the executable operator boundary for the native-provider integration
tranche. Direct Compose owns ordinary lifecycle after one narrow invariant-preparation step.
`compose.sh` remains only for SecretRef/config rendering, provenance and resource ownership
verification, the bounded Keycloak migration, and isolated cleanup. Preparation writes one
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
processes on the host. Prepare the local SecretRefs and deterministic realm import once, then use
native Compose for ordinary lifecycle:

```bash
cd infra/weave-workspace
./compose.sh dev configure
docker compose --env-file .env.dev up -d
cd ../..
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

E2E activates Mailpit with implicit TLS and the isolated RuntimeState proof service. Its generated
root, SecretRefs, ports, network, and volumes are derived from the run ID. Cleanup remains limited
to the exact ownership-labeled namespace. The same deferred FGAP migration currently blocks this
lane rather than accepting a persistent-realm recovery proof outside its scope.

## Dogfood and production

The Keycloak post-import step has two explicit precondition paths; they are not interchangeable:

- **Fresh Start dogfood cutover:** the approved Fresh Start plan first retires the previous owned
  generation. Its canonical apply evidence proves that every approved target was removed. The
  recreated empty realm imports the new baseline and runs only the bounded FGAP post-import step.
  No backup of the retired realm is presented as a migration prerequisite and no legacy realm
  state is transferred.
- **Persistent non-empty realm upgrade:** a later dogfood or production baseline migration remains
  backup- and restore-rehearsal-gated before any static IAM mutation.

Both paths bind the exact candidate, environment, realm baseline, migration definition and
precondition proof. Both require complete semantic readback, an empty second plan, deletion of the
temporary bootstrap authority and negative readback before application readiness. Routine startup
performs neither reconciliation nor migration.

For a normal persistent upgrade the operator runs the explicit migration before ordinary Compose
convergence:

```bash
cd infra/weave-workspace
WEAVE_ENV_FILE=/absolute/path/to/reviewed-dogfood.env ./compose.sh dogfood keycloak-migration-apply
docker compose --env-file .env.dogfood up -d

WEAVE_ENV_FILE=/absolute/path/to/reviewed-prod.env ./compose.sh prod keycloak-migration-apply
docker compose --env-file .env.prod up -d
```

A Fresh Start is not initiated with the command above. It runs through the governed Fresh Start
plan/apply/recreate lifecycle so the exact plan and apply-evidence digests reach the recreation
process and become the Keycloak migration precondition proof.

Dogfood keeps Mailpit as persistent activation-sensitive infrastructure, requires implicit TLS, and
has no SMTP shared secret. Production requires a non-secret reviewed SMTP username and a mode-0600
`smtp-password` SecretRef mounted only into Keycloak File Vault. Both consume a reproducible,
secret-free realm render derived from the candidate-bound semantic source. The inactive
migration-only services mount one temporary mode-0600 SecretRef; normal Keycloak, Server, and MCP
services do not.
