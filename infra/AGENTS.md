# Infrastructure Repository Guide

`infra/weave-workspace/` is the Compose-owned single-host deployment model. The binding
contract is the pinned Weave specification corpus, especially ADR 0017, plus the root and
repository `AGENTS.md` files. Do not reintroduce executable OpenTofu/Terraform, HCL state,
`TF_VAR_*` inputs, or a compatibility deployment path.

## Authority and profiles

- `compose.yaml` owns the common service graph.
- `compose.dev.yaml`, `compose.test.yaml`, and `compose.prod.yaml` are the only environment
  overlays and profiles. Production publication uses the exact digest-pinned `prod` model; it
  is not a fourth profile.
- `dev` runs provider dependencies in Compose and the Spring Boot server on the host. Provider
  databases remain PostgreSQL; only the host server may use H2 for the fast development loop.
- `test` and `prod` run the application tier with PostgreSQL. Isolated E2E uses the test
  topology under a run-unique Compose project and resource namespace.
- Public deployment coordinates live in reviewed environment files. Credentials are individual
  mode-0600 files below `WEAVE_SECRET_ROOT`; never place secret values in env files, Compose
  models, evidence, logs, or support bundles.

## Operator entry points

Use `weave-workspace/compose.sh <dev|test|prod> <command>` with one of:

- `secrets-init`, `render`, `config`, `prepare`, `up`, `down`, `ps`, or `logs`;
- `identity-plan`, `identity-apply`, or `identity-verify`.

`test` and `prod` require `WEAVE_ENV_FILE` pointing to a private reviewed file. The normal
deployment sequence is `secrets-init -> render -> config -> prepare -> identity-apply -> up ->
identity-verify`. A normal `down` never removes persistent volumes.

`backup.sh` creates a private, quiesced, candidate-bound backup below an operator-owned mode-0700
directory outside the checkout. `adoption-rehearsal.sh` verifies that backup through an isolated
restore before an unlabeled former deployment resource can be adopted. Never mutate or adopt a
persistent test/prod resource without exact ownership labels or an explicitly selected
`persistent-adoption` deployment context backed by a verified backup.

## Validation and safety

- Run `./gradlew infraStatic`, the profile-specific `compose*Config` task, and the relevant
  protected Keycloak plan/verify task.
- `./gradlew noExecutableOpenTofuCheck` must remain green.
- Keep image digests pinned for test/prod, Compose resources explicitly named and labeled,
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
