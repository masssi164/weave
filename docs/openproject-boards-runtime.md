# OpenProject Boards runtime profile

OpenProject is the first real provider-backed Boards path for Weave, but Weave remains the product UX. OpenProject is a backend/provider engine only.

## Default posture

- The core local stack does not require OpenProject.
- `TF_VAR_boards_preview_provider=local-preview` by default.
- `TF_VAR_boards_openproject_runtime_enabled=false` and `TF_VAR_boards_openproject_read_sync_enabled=false` by default.
- `TF_VAR_boards_openproject_provider_writes_enabled=false` for the MVP/runtime path.
- The backend-held API token is never written to `app-config.env`, Flutter config, or support-safe summaries.

## External OpenProject connector

Use this path for organizations that already run OpenProject. In a private, chmod 600 env file, set:

```bash
TF_VAR_boards_preview_runtime_enabled=true
TF_VAR_boards_preview_provider=openproject
TF_VAR_boards_openproject_runtime_enabled=true
TF_VAR_boards_openproject_read_sync_enabled=true
TF_VAR_boards_openproject_context_authorization_enabled=false
TF_VAR_boards_openproject_audit_consent_enabled=false
TF_VAR_boards_openproject_provider_writes_enabled=false
TF_VAR_boards_openproject_auth_mode=service-token
TF_VAR_boards_openproject_base_url=https://openproject.example
TF_VAR_boards_openproject_api_token=replace-with-read-only-service-token
```

Then rerun `./install.sh` or `terraform apply` for the infrastructure stage. The backend consumes these values server-side only.

## Optional self-hosted/demo profile

For full-suite/demo/data-sovereignty validation, start the isolated OpenProject compose profile after the Weave Docker network exists:

```bash
export TF_VAR_openproject_secret_key_base="$(openssl rand -hex 64)"
docker compose -f weave-workspace/docker-compose.openproject.yml --profile openproject up -d
```

Then configure the backend read-sync gate to use the Docker-network URL:

```bash
TF_VAR_boards_preview_runtime_enabled=true
TF_VAR_boards_preview_provider=openproject
TF_VAR_boards_openproject_runtime_enabled=true
TF_VAR_boards_openproject_read_sync_enabled=true
TF_VAR_boards_openproject_auth_mode=service-token
TF_VAR_boards_openproject_base_url=http://weave-openproject
TF_VAR_boards_openproject_api_token=replace-with-openproject-service-token
```

The profile exposes only a direct operator port (`TF_VAR_openproject_host_port`, default `48086`). It is not mounted under `/boards`, `/api`, `/files`, `/calendar`, or any other Weave product route.

## Promotion gates still closed

Before provider writes or agent/team writes are enabled, a later backend/infra slice must prove:

1. Context/Space/ReBAC authorization is active for provider references.
2. audit/consent evidence exists and refusal paths fail closed.
3. provider webhook verification, cursor/idempotency, and redaction behavior are tested.
4. support bundles redact every provider credential and raw upstream error.
5. Live Stack E2E proves read-only Boards alongside Chat, Calendar, and Files.
