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
TF_VAR_boards_openproject_context_authorization_enabled=true
TF_VAR_boards_openproject_audit_consent_enabled=false
TF_VAR_boards_openproject_provider_writes_enabled=false
TF_VAR_boards_openproject_auth_mode=service-token
TF_VAR_boards_openproject_base_url=https://openproject.example
TF_VAR_boards_openproject_api_token=replace-with-read-only-service-token
```

Then rerun `./install.sh` or `tofu apply` for the infrastructure stage. The backend consumes these values server-side only.

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
TF_VAR_boards_openproject_context_authorization_enabled=true
TF_VAR_boards_openproject_auth_mode=service-token
TF_VAR_boards_openproject_base_url=http://weave-openproject
TF_VAR_boards_openproject_api_token=replace-with-openproject-service-token
```

The profile exposes only a direct operator port (`TF_VAR_openproject_host_port`, default `48086`). It is not mounted under `/boards`, `/api`, `/files`, `/calendar`, or any other Weave product route.

## Live Stack E2E

OpenProject Boards runtime evidence lives behind an explicit operator/live-stack gate because it depends on a running Weave stack, a real app token from Keycloak, and optionally an external or self-hosted OpenProject service.

After `install.sh` has produced `.generated/bootstrap.env`, run the Weave API path check:

```bash
bash weave-workspace/openproject-boards-live-e2e.sh
```

By default this validates the safe default posture: `/api/boards/preview` is reachable only through Weave and fails closed with a support-safe Boards error, and provider writes are refused without leaking OpenProject secrets, raw provider URLs, or `/api/v3` paths. The manual full-stack smoke workflow runs this fail-closed gate after the core smoke test.

For an enabled read-only provider run, first configure the backend with the OpenProject variables above, point it at either the external connector or the optional self-hosted profile, and ensure Context/Space authorization has a membership for the smoke-test principal. Then run:

```bash
WEAVE_OPENPROJECT_LIVE_E2E_EXPECT_ENABLED=true \
  bash weave-workspace/openproject-boards-live-e2e.sh
```

The enabled mode requires the response source to be `openproject-read-sync-backend-facade`, provider capabilities to report `openproject`, sync metadata to be read-only/context-scoped/support-safe, and provider-neutral projects, boards, and tasks to be present. It also rechecks that write attempts remain refused until the later audit/consent promotion.

For a live Context/Space denial proof, enable the OpenProject runtime but configure the smoke-test principal without a matching Boards Context/Space membership. Then run:

```bash
WEAVE_OPENPROJECT_LIVE_E2E_EXPECT_CONTEXT_DENIED=true \
  bash weave-workspace/openproject-boards-live-e2e.sh
```

This mode requires HTTP 403 with `boards-forbidden` and still applies the no-secret/no-raw-OpenProject-output checks.

## Promotion gates still closed

Before provider writes or agent/team writes are enabled, a later backend/infra slice must prove:

1. Context/Space/ReBAC authorization stays active for provider references.
2. audit/consent evidence exists and refusal paths fail closed.
3. provider webhook verification, cursor/idempotency, and redaction behavior are tested.
4. support bundles redact every provider credential and raw upstream error.
5. Live Stack E2E proves read-only Boards alongside Chat, Calendar, and Files.
