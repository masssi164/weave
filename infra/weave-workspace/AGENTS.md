# Compose Workspace Guide

This directory implements the single supported local/single-host deployment authority described
by specification ADR 0022. The exact operator environments and Compose topology profiles are
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
- `scripts/dogfood_lifecycle.py`: exact-checkout build plus bounded up/down/reset for the fixed
  dogfood project. Reset preserves the external TLS root and removes only the three session
  volumes, with a one-time closed cleanup for the known unlabeled legacy stack.
- `backup.sh` and `adoption-rehearsal.sh`: production/recovery utilities. They are not dogfood or
  human-test prerequisites.

## Required sequence

```text
./gradlew devUp
./gradlew devDown
./gradlew dogfoodUp
./gradlew dogfoodDown
./gradlew dogfoodReset
```

Dogfood accepts digest-pinned images or exact local image IDs built from the clean checked-out
commit; production remains digest-only. Dogfood/e2e/prod require a private `WEAVE_ENV_FILE`. The optional
`WEAVE_SPEC_CORPUS_ROOT` process coordinate is accepted only when it is an absolute Git worktree
root at the exact commit in `specs/weave-specs.lock.json`.

`dev` contains only its Compose dependencies; run Server, MCP, and Admin Console on the host, with
H2 permitted only for the host server. Set `COMPOSE_PROFILES=dev,dev-tools` in a private dev
environment only when Mailpit is needed. Native dogfood includes the backend and MCP tier and
keeps exactly PostgreSQL, native Files, and Mailpit as resettable session volumes. Caddy and
Keycloak local state are ephemeral; TLS is bind-mounted from the operator-owned host directory.
Production uses its separate persistent policy and external SMTP.
E2E includes isolated Mailpit, sets `WEAVE_E2E_STACK_SCOPE=isolated`, and requires a bounded unique
`WEAVE_E2E_RUN_ID`; cleanup may
target only that derived namespace.

The Gradle tasks prepare and invoke the canonical Compose model; never hand-author the finalized
descriptor. `dogfoodDown` preserves the three session volumes. `dogfoodReset` removes only the
fixed `weave-dogfood` project/session resource names and immediately starts a clean stack. It does
not touch `WEAVE_TLS_ROOT`.

## Maintenance rules

- Never put credentials in Compose variables, generated public env files,
  reports, logs, or support bundles. Secret files are regular, non-symlink, least-readable files.
- Keep workload scope `mcp.tools`, exact resource/audience binding, per-cell workload clients,
  and `private_key_jwt`; do not restore shared/public MCP credentials or bearer relay.
- Production-only Keycloak migration must fail closed on partial or ambiguous readback, a
  non-empty second plan, missing bootstrap-authority deletion, or any stale artifact/receipt digest.
- Routine startup must not reconcile Keycloak state or mount a bootstrap credential.
- Never remove session volumes from `down`. Dogfood reset and isolated E2E cleanup must remain
  bounded to their exact fixed or run-derived resource names.
- Run `../../gradlew infraStatic` plus relevant profile config and Keycloak tasks after changes.
