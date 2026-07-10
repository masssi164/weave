# Dev → Dogfood → Main promotion flow

Status: active delivery policy.

Weave uses three promotion lanes:

- `dev`: normal integration branch and the base for feature branches. Feature PRs are cut from `dev` and return to `dev` after the review/refactor loop, feature-specific tests, acceptance/Gherkin/Cucumber mappings, docs/evidence, and PR-safe CI/contracts/unit/acceptance/docs gates.
- `dogfood`: persistent LAN dogfood branch and candidate/test-stack promotion lane. Promotion PRs from `dev` to `dogfood` run the feature-relevant E2E/live/dogfood validation for that candidate; missing feature-relevant Gherkin/Cucumber scenarios or deterministic mappings must be added by this stage at the latest. Advancing this branch deploys or updates the local test stack on the dedicated Mac runner. This branch is named `dogfood` because legacy `test/...` branches already occupy Git's `refs/heads/test/` namespace.
- `main`: stable/release-capable branch after dogfood validation. A commit may reach `main` only after it has been integrated through `dev`, validated through `dogfood`, and has green dogfood E2E/live evidence plus human-test signoff where the change requires it.

## Why this exists

`dev` proves that the repository builds and the offline/product-contract gates pass. Feature work belongs here first: branch from `dev`, PR back to `dev`, and add the feature-specific tests, acceptance scenarios, documentation, and evidence while the review/refactor loop is still cheap.

`dogfood` is the always-testable LAN stack and candidate validation truth. It is intended for human dogfood, physical iPhone checks, and integration evidence against the same deployed stack instead of one-off local shells. It is not a disposable release-only stack.

`main` must not receive commits that have bypassed either `dev` integration or `dogfood` deployment.

## Persistent LAN test stack

The test stack is deployed by the `Test Stack Deploy` GitHub Actions workflow:

- workflow file: `.github/workflows/test-stack-deploy.yml`
- trigger: push to `dogfood` or manual `workflow_dispatch`
- candidate E2E workflow: `.github/workflows/live-stack-e2e.yml` on promotion PRs targeting `dogfood` and manual dispatch
- runner: dedicated self-hosted macOS ARM64 runner `weave-live-mac-mini`
- public local entrypoint: `https://weave.test:44443/`
- platform config: `https://api.weave.test:44443/api/platform/config`
- local CA bootstrap: `http://weave.test:44080/weave-local-ca.pem`

The workflow uses the repo infrastructure scripts as implementation detail:

- `infra/weave-workspace/install.sh`
- `infra/weave-workspace/smoke-test.sh`
- `infra/weave-workspace/operator-check.sh`

Humans should not need to run those directly for normal test-stack use. The visible entrypoint is the `dogfood` branch deployment result and the iPhone app pointed at the dogfood stack.

## Update vs reset

The persistent test stack defaults to update mode:

- rebuild or pull the backend/MCP runtime images;
- run `install.sh` idempotently;
- keep stack data unless a reset is explicitly requested;
- run smoke/operator checks;
- upload a support-safe `weave-test-stack-evidence` artifact.

Destructive reset is manual only through the workflow input `reset_stack=true`. It removes local test-stack data and must not be used as the normal promotion path.

## Dogfood candidate validation

A promotion PR from `dev` to `dogfood` is the normal place for full or feature-relevant live validation. The `Live Stack E2E` workflow must run against the promotion candidate, generate acceptance-contract evidence from `e2e/features/` and `e2e/scenario_mappings.json`, and upload support-safe artifacts. It may destructively reset its temporary validation stack, but the persistent dogfood stack is updated separately by `Test Stack Deploy` after the candidate lands on `dogfood`.

The old pattern of a scheduled destructive full-E2E run from `main` is not the target model. `main` may keep lightweight smoke, release, or tag checks, but it must not be the primary noisy/destructive full-stack reset lane.

## Main promotion gate

The `Main Promotion Gate` workflow enforces the branch order:

1. the candidate commit is contained in `origin/dev`;
2. the same candidate commit is contained in `origin/dogfood`;
3. successful dogfood candidate E2E/live evidence exists for that commit;
4. a successful `Test Stack Deploy` workflow run exists for that commit on branch `dogfood`;
5. the root contract-authority architecture check still passes.

If any of these checks fail, the candidate is not eligible for `main`.

Bootstrap note: GitHub only treats new workflow files as branch-protection candidates after they exist on the protected/default branch. The first rollout of this policy therefore requires one explicitly reviewed bootstrap promotion that installs the workflows on `main`; after that, normal `main` PRs are expected to be guarded by this workflow and repository branch protection.

## Provider model

The persistent `dogfood` stack is a disposable Weave-owned dogfood environment. It should not attach to Massimo's `~/server` services by default.

Default local providers:

- Identity/Auth: Keycloak
- Chat: Matrix/Synapse + MAS
- Files/Calendar: Nextcloud
- Reverse proxy/TLS/CA: Caddy
- Database: Postgres
- Weave backend and Weave MCP runtime
- Boards: `local-workspace` by default, with OpenProject gated separately

Attaching to existing home services such as Authentik or Nextcloud under `~/server` is a later `attach-existing-home` profile. It must start read-only/preflight, must not copy secrets into the repo, and must not mutate household services without explicit approval. Repository delivery remains GitHub-only.

## iPhone dogfood expectation

The desired tester experience is:

1. install/trust the Weave Local Development CA once on the iPhone;
2. install or open the dogfood app build configured for `https://api.weave.test:44443/api/platform/config`;
3. sign in once;
4. later open Weave and return to the same test-stack organization without re-running setup scripts.

Invite/QR handoff remains useful for first enrollment and reset cases, but should not be required every time the app opens. Wording must be precise: the current join/handoff link is a non-secret enrollment handoff, not bearer access. Actual access control is the provisioned account, organization/workspace membership, and identity-provider session.

## Release discipline

A change that affects sign-in, backend facade contracts, OpenAPI consumers, MCP/tool exposure, provider boundaries, local stack topology, or onboarding must normally prove:

- ordinary PR CI on `dev`;
- generated OpenAPI/admin/client freshness where relevant;
- MCP/root architecture gates where relevant;
- promotion PR evidence from `dev` to `dogfood`, including feature-relevant Gherkin/Cucumber scenarios or deterministic mappings;
- persistent `dogfood` stack deployment;
- targeted human or automated dogfood evidence before promotion to `main`.
