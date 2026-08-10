# Dev → Dogfood → Main promotion flow

Status: active delivery policy.

Weave uses three promotion lanes:

- `dev`: normal integration branch and the base for feature branches. Feature PRs are cut from `dev` and return to `dev` after the review/refactor loop, feature-specific tests, acceptance/Gherkin/Cucumber mappings, docs/evidence, and PR-safe CI/contracts/unit/acceptance/docs gates.
- `dogfood`: persistent LAN dogfood branch and candidate/test-stack promotion lane. Promotion PRs from `dev` to `dogfood` run the feature-relevant E2E/live/dogfood validation for that candidate; missing feature-relevant Gherkin/Cucumber scenarios or deterministic mappings must be added by this stage at the latest. Advancing this branch deploys or updates the local test stack on the dedicated Mac runner.
- `main`: stable/release-capable branch after dogfood validation. A commit may reach `main` only after it has passed the credential-free exact-candidate `testApp` product flow, deployed idempotently to dogfood, produced the verified iOS candidate, and has a ready physical-iPhone human-testing manifest.

## Why this exists

`dev` proves that the repository builds and the offline/product-contract gates pass. Feature work belongs here first: branch from `dev`, PR back to `dev`, and add the feature-specific tests, acceptance scenarios, documentation, and evidence while the review/refactor loop is still cheap.

`dogfood` is the always-testable LAN stack and candidate validation truth. It is intended for human dogfood, physical iPhone checks, and integration evidence against the same deployed stack instead of one-off local shells. It is not a disposable release-only stack.

`main` must not receive commits that have bypassed either `dev` integration or `dogfood` deployment.

## Explicit Candidate Cut

An ordinary merge to `dev` runs merge CI only. It does not publish release images or start the Fresh product proof. A release owner cuts a candidate explicitly from the protected `dev` workflow definition and names one exact lowercase commit already contained in `origin/dev`:

```bash
git fetch origin dev
candidate_sha="$(git rev-parse origin/dev)"
gh workflow run candidate-images.yml --ref dev -f "candidate_sha=$candidate_sha"
```

The read-only source gate rejects a non-`dev` workflow ref, malformed SHA, checkout mismatch, or commit outside protected `dev` before package-write authority is available. The publish job uses the protected `candidate-cut` GitHub environment and derives every tag, OCI revision, Keycloak build record, manifest field, artifact name, and Linux/AMD64 Fresh proof from the verified SHA. Repository configuration must restrict that environment to `dev` and require release-owner approval.

If one candidate was cut more than once, dogfood deliberately refuses to guess. Supply the exact successful run ID through `candidate_images_run_id`; the consumer still verifies the workflow path, dispatch event, protected branch, run title, manifest bytes/digest, source/spec commits, three image digests, attestations, semantic realm definition, and local OCI identities before use. No downstream lane rebuilds or relabels a candidate image.

Candidate Cut binds only environment-neutral Keycloak identity:

- `realmDefinition.semanticRealmSourceDigest`
- `realmDefinition.migrationDefinitionDigest`

It never publishes one environment's rendered `realm.json` as candidate authority.

## Persistent LAN dogfood stack

The test stack is deployed by the `Test Stack Deploy` GitHub Actions workflow. Candidate evidence keeps two immutable identities: the protected `dev` source commit used to build all artifacts and the `dogfood` lane merge commit being validated and deployed. Neither may be inferred from the other or omitted.

- workflow file: `.github/workflows/test-stack-deploy.yml`
- trigger: successful exact-candidate `Live Stack Product Flow` `workflow_run` from a `dogfood` push, or a manual recovery dispatch that names a commit with existing successful isolated evidence
- candidate E2E workflow: `.github/workflows/live-stack-e2e.yml` on promotion PRs targeting `dogfood` and manual dispatch
- runner: dedicated self-hosted macOS ARM64 runner `weave-live-mac-mini`
- public local entrypoint: `https://weave.test:44443/`
- platform config: `https://api.weave.test:44443/api/platform/config`
- local CA bootstrap: `http://weave.test:44080/weave-local-ca.pem`

Normal operator entrypoints are profile-driven infra commands. The stack renders environment-specific Keycloak configuration from the shared semantic realm source plus the reviewed dogfood overlay and dogfood-owned public JWKS. Generated realm bytes are deployment artifacts, not maintained sources.

## Fresh generation vs later update

The standards-first dogfood cutover is a Fresh Start. It has no legacy database, Keycloak realm, provider object, runtime credential, or volume adoption/migration path. Before the first persistent mutation, the governed workflow must produce the exact manifest-bound deletion plan, private recovery evidence for the retired generation, isolated restore probe, and typed `DELETE_OLD_WEAVE:<plan-sha256>` approval. Normal promotion cannot substitute for that approval.

For the newly empty realm, Keycloak startup import establishes the static baseline. A bounded post-import migration applies only FGAP state that Keycloak import cannot express. That Fresh-Start operation does not fabricate a backup requirement for the new empty realm; it is authorized by machine-verifiable Fresh-Start plan/apply evidence.

After the new generation has been established, later candidates default to update mode within that generation:

- consume the exact backend, MCP, and custom Keycloak Runtime image digests proven by the isolated candidate run;
- verify that the candidate semantic realm definition matches the environment render source;
- render the dogfood-specific overlay and public JWKS deterministically;
- for an existing non-empty realm, require the versioned migration path with private backup and isolated restore rehearsal before static IAM mutation;
- keep only state created under the current generation;
- reject pre-generation, unowned, or undeclared resources instead of adopting them;
- run operator checks;
- produce final support-safe `realmEvidence` after semantic readback and convergence;
- upload the support-safe `weave-test-stack-evidence` artifact.

Persistent dogfood contains only configured human identities. Run-scoped `testApp` owners, members, files, cells, and workload clients belong only to the isolated namespace and are removed with it.

There is no persistent reset input. The one-time generation cut and any later persistent state deletion are separate protected operations and are never part of normal promotion.

## Environment-specific realm evidence

Each environment renders its own Keycloak deployment artifacts from the same candidate semantic definition. Environment-specific values may legitimately differ:

- public URLs and redirect origins;
- SMTP coordinates and SecretRefs;
- organization presentation metadata;
- public JWKS derived from environment-owned private keys;
- `overlayDigest`;
- `renderedRealmDigest`;
- `semanticReadbackDigest`.

Cross-environment comparison therefore requires identical `semanticRealmSourceDigest` and `migrationDefinitionDigest`, not byte-identical realm JSON. Same-environment convergence requires stable overlay/render identity plus successful semantic readback.

Private JWKs, passwords, tokens, cookies, client secrets, and SecretRef payloads are forbidden in generated realm and support-safe evidence artifacts.

## Dogfood candidate validation

A promotion PR from `dev` to `dogfood` consumes an existing explicit Candidate Cut and binds its manifest to the exact lane candidate. The comprehensive `./gradlew testApp` Fresh proof first runs natively on Linux/AMD64 during the cut. The `Live Stack Product Flow` repeats the manifest-bound product journey on Apple Silicon/OrbStack while re-verifying the exact image IDs without rebuilding artifacts. Disposable E2E must prove the exact run-owned namespace is absent before creating resources; profile name alone is never sufficient evidence of a Fresh realm.

The journey creates an owner, collaborator, and outsider through real invitations and Keycloak required actions in Chromium, uses fresh Authorization Code + PKCE sessions, proves two complete Chat/Files/Calendar/Home/Profile collaboration passes, direct provider readback, JPA/PostgreSQL durability, provider outage/retry idempotency, callback replay, workload OAuth and MCP revoke/regrant, then destroys only its isolated namespace. Only that successful artifact chain can trigger persistent `Test Stack Deploy`.

## Main promotion gate

The `Main Promotion Gate` workflow enforces the branch order:

1. the protected source candidate that built every artifact is contained in `origin/dev`;
2. the evaluated lane candidate is contained in `origin/dogfood`, and its tree is byte-identical to the protected source candidate without rebuilding artifacts;
3. successful manifest-bound dogfood E2E/live evidence exists for the lane candidate and exact protected source;
4. a successful `Test Stack Deploy` workflow run exists for that lane/source pair on branch `dogfood`;
5. deployment evidence proves the dogfood environment render is derived from the candidate semantic realm definition and has a verified semantic Keycloak readback;
6. a successful `iOS Dogfood` distribution run exists for the same deployment, candidate manifest, and three image digests;
7. a separate successful `Physical iPhone Human Test` run contains the tester-confirmed twenty-step protocol for the installed build;
8. a support-safe schema-v5 `human-testing-readiness.json` artifact evaluates to `ready` for the same lane, source, specification, manifest, images, realm evidence, runs, physical protocol, and a fresh persistent-runtime provider-health observation collected after that protocol;
9. the root contract-authority architecture check still passes.

If any of these checks fail, the candidate is not eligible for `main`.

## Provider model

The persistent `dogfood` stack is a Weave-owned durable validation environment. It does not attach to unrelated household services by default. External providers remain explicit opt-in adapters behind canonical Weave domains.

Default native platform posture:

- Identity/Auth: Keycloak
- Files: `weave-native` + BlobStore
- Calendar: `weave-native`
- Chat: `weave-native`
- Reverse proxy/TLS/CA: Caddy
- Database: PostgreSQL
- Weave Server and workload-only Weave MCP runtime

Optional external interoperability/provider adapters such as Matrix/Synapse, Nextcloud, OpenProject, Authentik, Slack, or Teams must be selected explicitly and must not become implicit deployment dependencies.

## iPhone dogfood expectation

The desired tester experience is:

1. install/trust the Weave Local Development CA once on the iPhone;
2. install or open the dogfood app build configured for `https://api.weave.test:44443/api/platform/config`;
3. sign in once;
4. later open Weave and return to the same test-stack organization without re-running setup scripts.

Invite/QR handoff remains useful for first enrollment and reset cases, but should not be required every time the app opens. Actual access control is the provisioned account, organization membership, and identity-provider session.

## Release discipline

A change that affects sign-in, backend facade contracts, OpenAPI consumers, MCP/tool exposure, provider boundaries, realm semantics, local stack topology, or onboarding must normally prove:

- ordinary PR CI on `dev`;
- generated OpenAPI/admin/client freshness where relevant;
- MCP/root architecture gates where relevant;
- candidate semantic realm definition and migration-definition integrity when IAM changes;
- promotion PR evidence from `dev` to `dogfood`, including feature-relevant Gherkin/Cucumber scenarios or deterministic mappings;
- persistent `dogfood` deployment and semantic realm readback;
- targeted human or automated dogfood evidence before promotion to `main`.
