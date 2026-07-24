# Compose Workspace Guide

This directory implements the single supported local/single-host deployment authority described
by pinned specification ADR 0016. The exact profiles are `dev`, `dogfood`, and `main`; do not add
a parallel Compose graph or executable OpenTofu/Terraform fallback.

## Owned files

- `compose.yaml`: common PostgreSQL, Keycloak, MAS, Synapse, Nextcloud, Caddy, backend, and MCP
  service graph with stable named networks/volumes and ownership labels.
- `compose.<profile>.yaml`: narrow environment-specific overlays.
- `environments/`: public deployment coordinates. Dogfood/main examples must be copied to a
  private operator file supplied through `WEAVE_ENV_FILE`.
- `scripts/compose_env.py`: closed environment, naming, and pinned-spec-corpus trust boundary.
- `scripts/init_secrets.py`: idempotent dev/dogfood secret initialization and main secret
  validation. It must never print values.
- `scripts/render_config.py`: deterministic renderer from the pinned canonical desired state.
- `scripts/compose_runtime.py` and `compose.sh`: the only normal lifecycle interface.
- `keycloak/`: protected desired-state reconciliation, sanitizer, and disposable pinned `kcadm`
  boundary. It must not retain a broad administrator or expose raw Admin REST responses.
- `database/postgres-reconcile.sh`: idempotent provider database/role and reconciliation-control
  schema convergence.
- `backup.sh` and `adoption-rehearsal.sh`: private candidate-bound backup and isolated adoption
  proof. Normal stop/update never removes data.

## Required sequence

```text
compose.sh <profile> secrets-init
compose.sh <profile> render
compose.sh <profile> config
compose.sh <profile> prepare
compose.sh <profile> keycloak-apply
compose.sh <profile> up
compose.sh <profile> keycloak-verify
```

Dogfood/main require digest-pinned images and a private `WEAVE_ENV_FILE`. The optional
`WEAVE_SPEC_CORPUS_ROOT` process coordinate is accepted only when it is an absolute Git worktree
root at the exact commit in `specs/weave-specs.lock.json`.

`dev` contains provider dependencies only; run Spring Boot separately with the `dev` profile and
H2. Dogfood/main include the backend and MCP application tier and use PostgreSQL. Isolated E2E
sets `WEAVE_E2E_STACK_SCOPE=isolated` and a bounded unique `WEAVE_E2E_RUN_ID`; cleanup may target
only that derived namespace.

## Maintenance rules

- Never put credentials in Compose variables, command arguments, generated public env files,
  reports, logs, or support bundles. Secret files are regular, non-symlink, least-readable files.
- Keep workload scope `mcp.tools`, exact resource/audience binding, per-cell workload clients,
  and `private_key_jwt`; do not restore shared/public MCP credentials or bearer relay.
- Keycloak plan/apply/verify must be idempotent, fenced by PostgreSQL, and fail closed on partial
  readback, stale lease, temporary-authority cleanup failure, or redaction findings.
- Do not delete managed or unmanaged Keycloak resources in ordinary reconciliation. Tombstones
  require their separately verified protected path.
- Never remove persistent volumes from `down`. Destructive isolated cleanup requires exact
  ownership labels and run binding.
- Run `../../gradlew infraStatic` plus relevant profile config and Keycloak tasks after changes.
