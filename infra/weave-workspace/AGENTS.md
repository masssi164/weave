# Compose Workspace Guide

This directory implements the single supported local/single-host deployment authority described
by specification ADR 0017. The exact operator environments are `dev`, `dogfood`, `prod`, and
`e2e`; do not add a parallel Compose graph or executable OpenTofu/Terraform fallback. The legacy
internal profile selector `test` is CI compatibility only while external-provider dependencies
remain executable, not a fifth operator environment.

## Owned files

- `compose.yaml`: common PostgreSQL, Keycloak, MAS, Synapse, Nextcloud, Caddy, backend, and MCP
  service graph with stable named networks/volumes and ownership labels.
- `compose.<environment>.yaml`: narrow environment-specific overlays.
- `environments/`: public deployment coordinates. Dogfood/e2e/prod examples must be copied to a
  private operator file supplied through `WEAVE_ENV_FILE`.
- `scripts/compose_env.py`: closed environment, naming, and pinned-spec-corpus trust boundary.
- `scripts/init_secrets.py`: idempotent dev/dogfood/e2e secret initialization and prod secret
  validation. It must never print values.
- `scripts/render_config.py`: deterministic renderer from the pinned canonical desired state.
- `scripts/compose_runtime.py` and `compose.sh`: the only normal lifecycle interface.
- `keycloak/`: rootless one-shot desired-state reconciliation through the pinned official
  Keycloak `kc.sh` and `kcadm.sh` interfaces. It must not retain a broad administrator or
  expose raw Admin REST responses.
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
compose.sh <environment> identity-apply
compose.sh <environment> up
compose.sh <environment> identity-verify
```

Dogfood/e2e/prod require digest-pinned images and a private `WEAVE_ENV_FILE`. The optional
`WEAVE_SPEC_CORPUS_ROOT` process coordinate is accepted only when it is an absolute Git worktree
root at the exact commit in `specs/weave-specs.lock.json`.

`dev` contains only Keycloak infrastructure; run Server, MCP, and Admin Console on the host, with
H2 permitted only for the host server. Dogfood/prod include the backend and MCP tier.
E2E sets `WEAVE_E2E_STACK_SCOPE=isolated` and a bounded unique `WEAVE_E2E_RUN_ID`; cleanup may
target only that derived namespace.

## Maintenance rules

- Never put credentials in Compose variables, generated public env files,
  reports, logs, or support bundles. Secret files are regular, non-symlink, least-readable files.
- Keep workload scope `mcp.tools`, exact resource/audience binding, per-cell workload clients,
  and `private_key_jwt`; do not restore shared/public MCP credentials or bearer relay.
- Identity Ops plan/apply/verify must be idempotent and fail closed on partial or ambiguous
  readback, non-empty second plan, secret-capture failure, or redaction findings.
- Do not delete managed or unmanaged Keycloak resources in ordinary reconciliation. Tombstones
  require their separately verified protected path.
- Never remove persistent volumes from `down`. Destructive isolated cleanup requires exact
  ownership labels and run binding.
- Run `../../gradlew infraStatic` plus relevant profile config and Keycloak tasks after changes.
