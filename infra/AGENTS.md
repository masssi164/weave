# Infrastructure Repository Guide

`infra/weave-workspace/` is the Compose-owned single-host deployment model. The binding
contract is the pinned Weave specification corpus, especially ADR 0017, plus the root and
repository `AGENTS.md` files. Do not reintroduce executable OpenTofu/Terraform, HCL state,
`TF_VAR_*` inputs, or a compatibility deployment path.

## Authority and environments

- `compose.yaml` owns the common service graph.
- The operator environments are exactly `dev`, `dogfood`, `prod`, and `e2e`; branch names never
  select one. `compose.dogfood.yaml` and `compose.e2e.yaml` give persistent dogfood and disposable
  E2E distinct public entry paths.
- `dev` starts PostgreSQL and Keycloak in Compose while Server, MCP, and Admin Console run on the
  host. Only the host server may use H2 for the fast development loop.
- `dogfood` and `prod` are persistent application-tier deployments. `e2e` is disposable and must
  use a run-unique Compose project, resource namespace, generated root, SecretRef root, and ports.
- `COMPOSE_PROFILES` in the reviewed environment file selects exactly one matching environment
  profile. Development may additionally select `dev-tools` for Mailpit; dogfood, prod, and E2E
  reject optional profiles. The transitional Matrix, Nextcloud, RuntimeState, and Identity Ops
  dependencies remain in the existing graph until their owning migration tranches remove or
  optionalize them.
- Public deployment coordinates live in reviewed environment files. Credentials are individual
  mode-0600 files below `WEAVE_SECRET_ROOT`; never place secret values in env files, Compose
  models, evidence, logs, or support bundles.

## Operator entry points

Use `weave-workspace/compose.sh <dev|dogfood|prod|e2e> <command>` with one of:

- `secrets-init`, `render`, `config`, `prepare`, `up`, `down`, `ps`, or `logs`;
- `identity-plan`, `identity-apply`, or `identity-verify`.

`dogfood`, `prod`, and `e2e` require `WEAVE_ENV_FILE` pointing to a private reviewed file. E2E
also requires `WEAVE_E2E_STACK_SCOPE=isolated` and a valid `WEAVE_E2E_RUN_ID`. The normal
deployment sequence is `secrets-init -> render -> config -> prepare -> identity-apply -> up ->
identity-verify`. A normal `down` never removes persistent volumes.

`backup.sh` creates a private, quiesced, candidate-bound backup below an operator-owned mode-0700
directory outside the checkout. `adoption-rehearsal.sh` verifies that backup through an isolated
restore before an unlabeled former deployment resource can be adopted. Never mutate or adopt a
persistent dogfood/prod resource without exact ownership labels or an explicitly selected
`persistent-dogfood` deployment context after the approved manifest-bound Fresh Start.
`fresh-start-backup-rehearsal.sh` is the separate hard-cut recovery proof: it backs up and restores
the retired generation in an isolated namespace without migrating credential state or authorizing
adoption. Never substitute the adoption receipt for Fresh Start evidence.

## Validation and safety

- Run `./gradlew infraStatic`, the profile-specific `compose*Config` task, and the relevant
  protected Keycloak plan/verify task.
- `./gradlew noExecutableOpenTofuCheck` must remain green.
- Keep image digests pinned for dogfood/e2e/prod, Compose resources explicitly named and labeled,
  reconciliation idempotent, and teardown bounded to exact isolated ownership evidence.
- Never weaken TLS, auth, provider readiness, secret permissions, backup checks, or support-safe
  redaction to make a test pass.
- Update root specs/contracts before changing public topology, URLs, credentials, provider
  boundaries, CI/E2E behavior, or environment inputs.

## Global Weave agent baseline

- Write code, comments, documentation, PRs, and issues in English unless editing localization.
- Follow the repository Gitflow and release-evidence rules; green static checks alone are not
  human dogfood evidence.
- Preserve unrelated user changes in the shared worktree.
