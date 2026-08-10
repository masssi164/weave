# Compose Workspace Guide

This directory implements the single supported local/single-host deployment authority described
by specification ADR 0017. The exact operator environments and Compose topology profiles are
`dev`, `dogfood`, `prod`, and `e2e`; do not add a parallel Compose graph, compatibility selector,
or executable OpenTofu/Terraform fallback. `dev-tools`, `provider-matrix`,
`provider-nextcloud`, and `storage-s3` are the closed optional profile set. Each provider/storage
profile requires its exact matching provider configuration and remains fail-closed until qualified.

## Owned files

- `compose.yaml`: common PostgreSQL, Keycloak, Caddy, backend, MCP, native storage, and optional
  provider definitions with stable named networks/volumes and ownership labels.
- `compose.<environment>.yaml`: narrow environment-specific overlays.
- `environments/`: public deployment coordinates. Dogfood/e2e/prod examples must be copied to a
  private operator file supplied through `WEAVE_ENV_FILE`.
- `scripts/compose_env.py`: closed environment, naming, and pinned-spec-corpus trust boundary.
- `scripts/init_secrets.py`: idempotent dev/dogfood/e2e secret initialization and prod secret
  validation. It must never print values.
- `scripts/render_config.py`: deterministic renderer from the pinned canonical desired state.
- `scripts/compose_runtime.py` and `compose.sh`: narrow preparation, invariant verification,
  migration, and proof tooling. Ordinary prepared lifecycle uses native Compose with the generated
  `.env.<environment>` descriptor.
- `keycloak/`: canonical realm projection support plus narrowly scoped OAuth verification helpers.
  Static state enters through realm import; dynamic human lifecycle belongs to Weave Server.
- `database/postgres-reconcile.sh`: idempotent provider database/role and reconciliation-control
  schema convergence.
- `backup.sh` and `adoption-rehearsal.sh`: private candidate-bound backup and isolated adoption
  proof. Normal stop/update never removes data.
- `fresh-start-backup-rehearsal.sh`: private backup plus isolated restore proof for a hard cut;
  it never migrates or authorizes adoption of the retired generation.

## Required sequence

```text
compose.sh <environment> secrets-init
compose.sh <environment> render
compose.sh <environment> config
compose.sh <environment> prepare
compose.sh <environment> keycloak-migration-apply
compose.sh <environment> up
```

Dogfood/e2e/prod require digest-pinned images and a private `WEAVE_ENV_FILE`. The optional
`WEAVE_SPEC_CORPUS_ROOT` process coordinate is accepted only when it is an absolute Git worktree
root at the exact commit in `specs/weave-specs.lock.json`.

`dev` contains only Keycloak infrastructure; run Server, MCP, and Admin Console on the host, with
H2 permitted only for the host server. Set `COMPOSE_PROFILES=dev,dev-tools` in a private dev
environment only when Mailpit is needed. Dogfood/prod include the backend and MCP tier; dogfood
also keeps persistent Mailpit for initial invitation capture, while prod uses external SMTP.
E2E includes isolated Mailpit, sets `WEAVE_E2E_STACK_SCOPE=isolated`, and requires a bounded unique
`WEAVE_E2E_RUN_ID`; cleanup may
target only that derived namespace.

After `configure` (dev) or the explicit migration (dogfood/prod), ordinary lifecycle is:
`docker compose --env-file .env.<environment> up -d`, `ps`, `logs`, and `down`. Missing derived
provenance fails Compose interpolation; never hand-author the finalized descriptor.

## Maintenance rules

- Never put credentials in Compose variables, generated public env files,
  reports, logs, or support bundles. Secret files are regular, non-symlink, least-readable files.
- Keep workload scope `mcp.tools`, exact resource/audience binding, per-cell workload clients,
  and `private_key_jwt`; do not restore shared/public MCP credentials or bearer relay.
- The bounded Keycloak migration must fail closed on partial or ambiguous readback, a non-empty
  second plan, missing bootstrap-authority deletion, or any stale artifact/receipt digest.
- Routine startup must not reconcile Keycloak state or mount a bootstrap credential.
- Never remove persistent volumes from `down`. Destructive isolated cleanup requires exact
  ownership labels and run binding.
- Run `../../gradlew infraStatic` plus relevant profile config and Keycloak tasks after changes.
