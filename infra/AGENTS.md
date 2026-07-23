# Infrastructure Repository Guide

`infra/weave-workspace/` is the Compose-owned single-host deployment model. The binding
contract is the pinned Weave specification corpus, especially ADR 0016, plus the root and
repository `AGENTS.md` files. Do not reintroduce executable OpenTofu/Terraform, HCL state,
`TF_VAR_*` inputs, or a compatibility deployment path.

## Authority and profiles

- `compose.yaml` owns the common service graph.
- `compose.dev.yaml`, `compose.dogfood.yaml`, and `compose.main.yaml` are the only environment
  overlays and profiles. Production publication uses the exact digest-pinned `main` model; it
  is not a fourth profile.
- `dev` runs provider dependencies in Compose and the Spring Boot server on the host. Provider
  databases remain PostgreSQL; only the host server may use H2 for the fast development loop.
- `dogfood` and `main` run the application tier with PostgreSQL. Isolated E2E uses the dogfood
  topology under a run-unique Compose project and resource namespace.
- Public deployment coordinates live in reviewed environment files. Credentials are individual
  mode-0600 files below `WEAVE_SECRET_ROOT`; never place secret values in env files, Compose
  models, evidence, logs, or support bundles.

## Operator entry points

Use `weave-workspace/compose.sh <dev|dogfood|main> <command>` with one of:

- `secrets-init`, `render`, `config`, `prepare`, `up`, `down`, `ps`, or `logs`;
- `keycloak-plan`, `keycloak-apply`, or `keycloak-verify`.

`dogfood` and `main` require `WEAVE_ENV_FILE` pointing to a private reviewed file. The normal
deployment sequence is `secrets-init -> render -> config -> prepare -> keycloak-apply -> up ->
keycloak-verify`. A normal `down` never removes persistent volumes.

`backup.sh` creates a private, quiesced, candidate-bound backup below an operator-owned mode-0700
directory outside the checkout. `adoption-rehearsal.sh` verifies that backup through an isolated
restore before an unlabeled former deployment resource can be adopted. Never mutate or adopt a
persistent dogfood/main resource without exact ownership labels or the matching successful
adoption receipt.

## Validation and safety

- Run `./gradlew infraStatic`, the profile-specific `compose*Config` task, and the relevant
  protected Keycloak plan/verify task.
- `./gradlew noExecutableOpenTofuCheck` must remain green.
- Keep image digests pinned for dogfood/main, Compose resources explicitly named and labeled,
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
