# Keycloak deployment and reconciliation contract

The normative authority is the pinned Weave Specification Corpus, especially ADR 0016 and the
`weave.keycloak-desired-state/v1`, environment-overlay, sanitizer-profile, and reconciliation-
receipt contracts. This document is their implementation/operator projection.

## Ownership

- Keycloak is the organization identity authority and upstream authentication broker.
- MAS is the Matrix-facing authorization server and delegates authentication to Keycloak.
- Flutter, Admin Console, and web clients use Authorization Code with PKCE S256 through the system
  browser. Password/direct grants and embedded login are disabled.
- The fixed realm baseline is reconciled only by the protected Compose Keycloak path.
- Agent Runtime Control owns dynamic `weaver-cell-{cellId}` workload clients; they are never part of
  the fixed baseline or inferred from a human role.
- Product server startup does not import, mutate, or carry a second realm baseline.

## Desired state

`keycloak/desired_state_authority.py` loads the exact canonical examples from the pinned corpus,
validates their RFC 8785 revisions, and renders one closed overlay for `dev`, `dogfood`, or `main`.
The overlay may change only public HTTPS origins, SMTP transport, organization metadata, named
SecretRefs, and the exact Keycloak image digest. It cannot add users, roles, grants, flows, token
policy, or arbitrary client configuration.

The baseline owns the realm, Organizations feature, `owner|admin|member|guest` roles, group
hierarchy, scopes/mappers, fixed clients, service-account grants, token-exchange policy, exact
redirect/logout URIs, and human/workload credential separation. Dogfood/main SMTP requires
implicit TLS; main requires external SMTP credential SecretRefs.

## Protected `kcadm` boundary

`compose.sh <profile> keycloak-plan|keycloak-apply|keycloak-verify` calls one immutable externally
installed supervisor generation. The candidate process supplies only:

- exact profile, reviewed environment path, candidate/specification commits, nonce, and
  reconciliation ID;
- exact approved Keycloak and sanitizer image digests;
- public runtime coordinates and named SecretRef roots.

The supervisor is root-owned and approved separately with `./gradlew keycloakSupervisorInstall`.
It owns the Docker/host-control channel and receipt signing key; candidate code receives neither.
Before any mutation it acquires the PostgreSQL reconciliation lease and monotonic fence, attests
Keycloak stopped, creates one run-bound bootstrap-admin service, starts Keycloak, runs the pinned
`kcadm` distribution only through the closed sanitizer, reads back complete state, deletes the
temporary authority, proves new-grant denial and last-token expiry, clears transient state, and
signs a flattened Ed25519 JWS receipt.

The sanitizer denies credential, installation, bulk import/export, key, user-credential, and
unlisted endpoints before allowlisting. It emits typed projections and coverage digests only; raw
Admin REST request/response bodies never reach disk, stdout, logs, evidence, or support bundles.
Any failed cleanup, partial coverage, stale fence, redaction finding, residual authority, or
residual sensitive state quarantines the lease and cannot produce success.

Promotion evidence uses two distinct commit authorities. `WEAVE_CANDIDATE_COMMIT` is the exact
checked-out lane commit (for example, the dogfood merge commit) and binds reconciliation, backup,
deployment, and human evidence. `WEAVE_IMAGE_SOURCE_COMMIT` is the protected `dev` ancestor whose
tree is byte-identical to that lane commit; it binds the backend, MCP, Keycloak, and sanitizer
image revisions. Protected workflows derive the source from the fetched `origin/dev` ref and emit
`candidate-source-mapping.json`; it is not a dispatch input or an operator-selected replacement.
The successful isolated E2E run retains that exact immutable four-image set on the dedicated
locked runner. Persistent dogfood downloads the source-run mapping and verifies each local image
ID and revision before deployment; it never rebuilds or substitutes those images.

Ordinary reconciliation creates and updates but never deletes managed or unmanaged resources.
Deletion requires one separately approved, candidate/backup/fingerprint/time-bound tombstone.

## Credential classes

Human/public clients use PKCE and rotating refresh tokens. Each Weaver cell uses its own
confidential service-account client with `private_key_jwt`, exact resource/audience, and a scope
ceiling. There is no shared Weaver client and no public MCP client. Internal token exchange uses
Keycloak Standard Token Exchange v2 with exact target audience downscoping; inbound bearer tokens
are never relayed.

Every persistent credential is a mode-0600 named file below `WEAVE_SECRET_ROOT`. The reviewed
environment file contains no secret values. Repeated deployment preserves valid generations;
explicit rotation is separately audited and verified. The run-specific bootstrap-admin secret is
the only intentionally ephemeral credential and is destroyed before a receipt may succeed.

## Profile behavior

- `dev`: local dependency Compose project, local image IDs permitted, Mailpit plaintext only on the
  private container endpoint, host Spring server on H2.
- `dogfood`: persistent named volumes, digest-pinned images, implicit-TLS Mailpit, PostgreSQL server,
  external supervisor, federation disabled.
- `main`: release-capable digest-pinned model, external implicit-TLS SMTP, PostgreSQL server,
  protected publication approval, federation disabled unless a later trust profile is accepted.
- isolated E2E: the dogfood topology under a deterministic run namespace; its clients, ports,
  volumes, SecretRefs, identities, receipts, and cleanup cannot satisfy persistent evidence.

## Reproducible tasks

```bash
./gradlew keycloakDevImageBuild keycloakSanitizerImageBuild
./gradlew keycloakDevPlan keycloakDevApply keycloakDevVerify

git fetch --no-tags --prune origin \
  '+refs/heads/dev:refs/remotes/origin/dev'
export WEAVE_CANDIDATE_COMMIT="$(git rev-parse HEAD)"
python3 tools/candidate_source_mapping.py \
  --repository . \
  --lane-candidate "$WEAVE_CANDIDATE_COMMIT" \
  --output build/evidence/candidate-source-mapping.json
export WEAVE_IMAGE_SOURCE_COMMIT="$(
  jq -er '.sourceCandidateCommit' build/evidence/candidate-source-mapping.json
)"
WEAVE_ENV_FILE=/absolute/path/to/reviewed-dogfood.env \
WEAVE_KEYCLOAK_SUPERVISOR=/opt/weave/keycloak-supervisor/<generation>/supervisor.py \
./gradlew keycloakDogfoodPlan keycloakDogfoodApply keycloakDogfoodVerify
```

The second protected plan must be empty. Readiness requires a current verified receipt bound to
the exact specification, candidate, image, deployment, lease/fence, desired/observed revisions,
nonce, and verification time. A valid-looking self-selected report is not evidence.
