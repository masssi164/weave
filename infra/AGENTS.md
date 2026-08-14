# Infrastructure Repository Guide

`infra/weave-workspace/` is the Compose-owned single-host deployment model. The binding
contract is the pinned Weave specification corpus, especially ADR 0022, plus the root and
repository `AGENTS.md` files. Do not reintroduce executable OpenTofu/Terraform, HCL state,
`TF_VAR_*` inputs, or a compatibility deployment path.

## Authority and environments

- `compose.yaml` owns the common service graph.
- The operator environments are exactly `dev`, `dogfood`, `prod`, and `e2e`; branch names never
  select one. `compose.dogfood.yaml` and `compose.e2e.yaml` give persistent dogfood and disposable
  E2E distinct public entry paths.
- `dev` starts Keycloak with its dev-file store in Compose while Server, MCP, and Admin Console
  run on the host. Only the host server may use H2 for the fast development loop.
- `dogfood` is a resettable application-tier development session. `prod` retains a separate
  persistent policy. `e2e` is disposable and must use a run-unique Compose project, resource
  namespace, generated root, SecretRef root, and ports.
- `COMPOSE_PROFILES` in the reviewed environment file selects exactly one matching environment
  profile. Development may additionally select `dev-tools`; provider/storage profiles are selected
  only with their exact matching provider configuration. Native Files, Calendar, and Chat are the
  default. Matrix, Nextcloud, and S3-compatible storage are optional and fail closed until their
  deployment contracts are qualified. No general Identity Ops authority remains.
- Public deployment coordinates live in reviewed environment files. Credentials are individual
  mode-0600 files below `WEAVE_SECRET_ROOT`; never place secret values in env files, Compose
  models, evidence, logs, or support bundles.

## Operator entry points

Use the root Gradle lifecycle tasks for ordinary development:

- `devUp` and `devDown` operate the local dependency stack;
- `dogfoodUp` and `dogfoodDown` start or stop the fixed LAN stack while preserving its session;
- `dogfoodReset` removes only the fixed dogfood project and its PostgreSQL, native Files, and
  Mailpit session volumes, then recreates an empty stack. Operator-owned TLS is never removed.

`weave-workspace/compose.sh <dev|dogfood|prod|e2e> <command>` remains the lower-level interface for
rendering, inspection, and production-only migration operations. Preparation creates a
secret-free mode-0600 `.env.<environment>` descriptor.

`dogfood`, `prod`, and `e2e` require `WEAVE_ENV_FILE` pointing to a private reviewed file. E2E
also requires `WEAVE_E2E_STACK_SCOPE=isolated` and a valid `WEAVE_E2E_RUN_ID`. Native dogfood does
not require a backup, migration receipt, deletion manifest, or approval token. Optional Matrix,
Nextcloud, S3, and Weaver services remain off unless an explicit qualified profile selects them.
A normal `down` never removes session volumes.

Backup, adoption, and migration utilities are production/recovery policy and are not prerequisites
for `dev`, resettable `dogfood`, E2E, or human testing. Do not attach retired resources to the new
dogfood project. The bounded compatibility cleanup in `dogfoodReset` may remove only its closed
list of known unlabeled legacy Weave resources after every target passes its preflight.

## Validation and safety

- Run `./gradlew infraStatic`, the profile-specific `compose*Config` task, and the relevant
  backup-gated Keycloak migration/receipt check.
- `./gradlew noExecutableOpenTofuCheck` must remain green.
- Keep images immutable by digest or exact local image ID where ADR 0022 allows it, Compose
  resources explicitly named and labeled, and teardown bounded to the exact selected project.
- Never weaken TLS, auth, provider readiness, secret permissions, backup checks, or support-safe
  redaction to make a test pass.
- Update root specs/contracts before changing public topology, URLs, credentials, provider
  boundaries, CI/E2E behavior, or environment inputs.

## Global Weave agent baseline

- Write code, comments, documentation, PRs, and issues in English unless editing localization.
- Follow the repository Gitflow and release-evidence rules; green static checks alone are not
  human dogfood evidence.
- Preserve unrelated user changes in the shared worktree.
