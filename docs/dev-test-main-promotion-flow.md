# Dev → Dogfood → Main promotion flow

Status: active delivery policy.

Weave uses three promotion lanes:

- `dev`: normal integration branch and the base for feature branches. Feature PRs are cut from `dev` and return to `dev` after the review/refactor loop, feature-specific tests, acceptance/Gherkin/Cucumber mappings, docs/evidence, and PR-safe CI/contracts/unit/acceptance/docs gates.
- `dogfood`: persistent LAN dogfood branch and candidate/test-stack promotion lane. Promotion PRs from `dev` to `dogfood` run the feature-relevant E2E/live/dogfood validation for that candidate; missing feature-relevant Gherkin/Cucumber scenarios or deterministic mappings must be added by this stage at the latest. Advancing this branch deploys or updates the local test stack on the dedicated Mac runner. This branch is named `dogfood` because legacy `test/...` branches already occupy Git's `refs/heads/test/` namespace.
- `main`: stable/release-capable branch after dogfood validation. A commit may
  reach `main` only after it has passed the credential-free exact-candidate
  `testApp` product flow, deployed idempotently to dogfood, produced the
  verified iOS candidate, and has a ready physical-iPhone human-testing
  manifest.

## Why this exists

`dev` proves that the repository builds and the offline/product-contract gates pass. Feature work belongs here first: branch from `dev`, PR back to `dev`, and add the feature-specific tests, acceptance scenarios, documentation, and evidence while the review/refactor loop is still cheap.

`dogfood` is the always-testable LAN stack and candidate validation truth. It is intended for human dogfood, physical iPhone checks, and integration evidence against the same deployed stack instead of one-off local shells. It is not a disposable release-only stack.

`main` must not receive commits that have bypassed either `dev` integration or `dogfood` deployment.

## Persistent LAN test stack

The test stack is deployed by the `Test Stack Deploy` GitHub Actions workflow. Candidate evidence keeps two immutable identities: the protected `dev` source commit used to build all artifacts and the `dogfood` lane merge commit being validated and deployed. Neither may be inferred from the other or omitted:

- workflow file: `.github/workflows/test-stack-deploy.yml`
- trigger: successful exact-candidate `Live Stack Product Flow` `workflow_run`
  from a `dogfood` push, or a manual recovery dispatch that names a commit with
  existing successful isolated evidence
- candidate E2E workflow: `.github/workflows/live-stack-e2e.yml` on promotion PRs targeting `dogfood` and manual dispatch
- runner: dedicated self-hosted macOS ARM64 runner `weave-live-mac-mini`
- public local entrypoint: `https://weave.test:44443/`
- platform config: `https://api.weave.test:44443/api/platform/config`
- local CA bootstrap: `http://weave.test:44080/weave-local-ca.pem`

The workflow and developers use the module-owned Gradle interface:

- `./gradlew :infra:composeTestUp`
- `./gradlew :infra:identityTestVerify`
- `./gradlew :infra:composeTestReady`

The task implementations call the same closed scripts used by CI. Humans do not paste or
reconstruct the underlying commands for normal test-stack use. The visible entrypoint is the
`dogfood` delivery result and the iPhone app pointed at the persistent `test` runtime.

## Fresh generation vs later update

The standards-first dogfood cutover is a Fresh Start. It has no legacy database, Keycloak realm, provider object, runtime credential, or volume adoption/migration path. Before the first persistent mutation, the governed workflow must produce the exact manifest-bound deletion plan, private backup, isolated restore probe, and typed `DELETE_OLD_WEAVE:<plan-sha256>` approval. Normal promotion cannot substitute for that approval.

After the new generation has been established, later candidates default to update mode within that generation:

- consume the exact backend, MCP, Identity Ops, and custom Keycloak Runtime image digests proven by the
  isolated candidate run;
- apply the `test` profile idempotently;
- keep only state created under the current generation;
- reject pre-generation, unowned, or undeclared resources instead of adopting them;
- run operator checks;
- upload a support-safe `weave-test-stack-evidence` artifact.

Persistent dogfood contains only configured human identities. Run-scoped
`testApp` owners, members, files, cells, and workload clients belong only to the
isolated namespace and are removed with it.

There is no persistent reset input. The one-time generation cut and any later persistent state deletion are separate protected operations and are never part of normal promotion.

The protected `dogfood` GitHub environment configures two absolute runner paths:

- `WEAVE_TEST_REVIEWED_ENV_FILE`: root-owned mode `0444` or `0644`, containing only reviewed
  public `test` coordinates;
- `WEAVE_TEST_BACKUP_ROOT`: operator-owned mode `0700`, outside the checkout.

The deployment fails before mutation if either path is absent, weakly permissioned, or symlinked.
For the generation cut it also fails unless the current plan, private backup, restore probe, exact
typed approval, and candidate manifest agree. Undeclared existing resources are drift, not an
adoption opportunity.

## Dogfood candidate validation

A promotion PR from `dev` to `dogfood` is the normal place for the manifest-bound full live validation. `Live Stack Product Flow` runs `./gradlew testApp` against immutable images built once from the protected `dev` source and binds the result to the exact lane candidate. It creates an owner, collaborator, and outsider through real invitations and Keycloak required actions in Chromium, uses fresh Authorization Code + PKCE sessions, proves two complete Chat/Files/Calendar/Home/Profile collaboration passes, direct Synapse readback, JPA/PostgreSQL durability, provider outage/retry idempotency, callback replay, workload OAuth and MCP revoke/regrant, then destroys only its isolated namespace. Only that successful artifact chain can trigger persistent `Test Stack Deploy`. Flutter system-browser authentication and VoiceOver remain physical-device gates; the separate fresh Simulator gate is explicitly fixture UI evidence.

The old pattern of a scheduled destructive full-E2E run from `main` is not the target model. `main` may keep lightweight smoke, release, or tag checks, but it must not be the primary noisy/destructive full-stack reset lane.

## Main promotion gate

The `Main Promotion Gate` workflow enforces the branch order:

1. the protected source candidate that built every artifact is contained in `origin/dev`;
2. the evaluated lane candidate is contained in `origin/dogfood`, and its tree is byte-identical to the protected source candidate without rebuilding artifacts;
3. successful manifest-bound dogfood E2E/live evidence exists for the lane candidate and exact protected source;
4. a successful `Test Stack Deploy` workflow run exists for that lane/source pair on branch `dogfood`;
5. a successful `iOS Dogfood` distribution run exists for the same deployment, candidate manifest, and four image digests;
6. a separate successful `Physical iPhone Human Test` run contains the tester-confirmed twenty-step protocol for the installed build;
7. a support-safe schema-v3 `human-testing-readiness.json` artifact evaluates to `ready` for the same lane, source, specification, manifest, images, runs, physical protocol, and a fresh persistent-runtime provider-health observation collected after that protocol;
8. the root contract-authority architecture check still passes.

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

The workflow never converts checkboxes into human evidence. After the real tester completes the
physical protocol, `.github/workflows/physical-iphone-human-test.yml` validates the submitted
support-safe twenty-step record against the exact deployment and iOS distribution artifacts. Only
its successful artifact may feed `.github/workflows/human-testing-readiness.yml`.

## Release discipline

A change that affects sign-in, backend facade contracts, OpenAPI consumers, MCP/tool exposure, provider boundaries, local stack topology, or onboarding must normally prove:

- ordinary PR CI on `dev`;
- generated OpenAPI/admin/client freshness where relevant;
- MCP/root architecture gates where relevant;
- promotion PR evidence from `dev` to `dogfood`, including feature-relevant Gherkin/Cucumber scenarios or deterministic mappings;
- persistent `dogfood` stack deployment;
- targeted human or automated dogfood evidence before promotion to `main`.
