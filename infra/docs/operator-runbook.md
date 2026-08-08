# Operator runbook

This is the minimum operator layer for the monorepo `infra/` single-host path.
It is meant to remove the remaining tribal knowledge around install, verify, recovery, and routine maintenance.

## 1. Before install

Prepare these explicitly:

- DNS for `<tenant_domain>`, `api`, `auth`, `matrix`, and the raw `files` protocol/admin fallback;
- a private reviewed copy of `weave-workspace/environments/dogfood.env.example`,
  `e2e.env.example`, or `prod.env.example`, stored outside the checkout;
- exact digest references for every dogfood/e2e/prod image;
- absolute `WEAVE_GENERATED_ROOT`, `WEAVE_SECRET_ROOT`, and `WEAVE_TLS_ROOT` paths;
- an unprivileged deployment operator able to run the rootless one-shot Server migration command;
- TLS certificate/key material and an operator-owned mode-0700 backup root outside the checkout.

Recommended file permissions on the host:

- reviewed public environment file: root-owned mode `0444` or `0644`;
- every credential/SecretRef file below `WEAVE_SECRET_ROOT`: mode `0600`;
- generated, secret, TLS, and backup roots: mode `0700`;
- TLS private keys and private backup artifacts: mode `0600`.

The reviewed environment contains public coordinates only. It must not contain passwords,
tokens, private keys, assertions, client secrets, or any other credential-shaped value.

## 2. Secrets inventory and rotation

Single-host credentials are individual named files below `WEAVE_SECRET_ROOT`. The checked-in
`scripts/init_secrets.py` inventory is the canonical filename set. Track owner, creation,
rotation, consumers, and expiry for database credentials; MAS/Matrix secrets and signing keys;
the Nextcloud actor credential; OIDC client credentials; Matrix Application Service tokens;
RuntimeProfile signing and RuntimeState wrapping keys; ARC administration credentials; and
per-cell workload private keys.

Repeated `secrets-init`, `prepare`, and install operations preserve an existing valid generation.
A missing, symlinked, empty, over-readable, or ambiguous SecretRef fails closed. Dogfood/e2e/prod do
not invent a replacement credential during ordinary deployment.

Rotation guidance:

1. create the new SecretRef generation under the operator's protected rotation procedure;
2. take a candidate-bound private backup and complete an isolated restore rehearsal;
3. rotate through overlap where the protocol supports it;
4. run the protected Keycloak/Nextcloud reconciliation and read-back verification;
5. verify fresh login, token revocation, provider access, and affected workload identities;
6. delete the retired generation only after every retained consumer and backup reference permits it.

Treat MAS signing keys, the RuntimeProfile signing key ring, and the RuntimeState
wrapping key ring as durable trust roots. Rotate through overlap and pair the change with a private
backup, restore rehearsal, reconciliation, and explicit consumer validation. Never remove a
wrapping key while a retained state generation still references it. Normal backend startup must
not invent a missing trust root.

## 3. Install and upgrade flow

```bash
cd weave
git fetch --no-tags --prune origin \
  '+refs/heads/dev:refs/remotes/origin/dev'
export WEAVE_ENV_FILE=/absolute/path/to/reviewed-dogfood.env
export WEAVE_CANDIDATE_COMMIT="$(git rev-parse HEAD)"
python3 tools/candidate_source_mapping.py \
  --repository . \
  --lane-candidate "$WEAVE_CANDIDATE_COMMIT" \
  --output build/evidence/candidate-source-mapping.json
export WEAVE_IMAGE_SOURCE_COMMIT="$(
  jq -er '.sourceCandidateCommit' build/evidence/candidate-source-mapping.json
)"
bash infra/weave-workspace/install.sh dogfood
bash infra/weave-workspace/release-verify.sh
bash infra/weave-workspace/operator-check.sh dogfood
```

Notes:

- `compose.yaml` plus the selected `compose.dev.yaml`, `compose.dogfood.yaml`,
  `compose.prod.yaml`, or `compose.e2e.yaml` overlays is the only deployment model;
- `install.sh dogfood|prod` is the supported idempotent apply path and runs
  `secrets-init → render → prepare → verified private backup → bounded Keycloak migration → up`;
- dogfood/e2e/prod require a private reviewed `WEAVE_ENV_FILE`, exact image digests, and an exact candidate
  commit; never use `:latest`;
- for dogfood promotion, `WEAVE_CANDIDATE_COMMIT` remains the checked-out lane SHA used by
  runtime, backup, deployment, and human evidence. Locally built images use
  `WEAVE_IMAGE_SOURCE_COMMIT`, derived by the protected workflow from fetched `origin/dev`.
  The source must be an ancestor with the same Git tree, and the closed three-image mapping is
  recorded in `candidate-source-mapping.json`; never accept the source as workflow input. The
  successful isolated run retains those exact IDs on the locked runner, and persistent dogfood
  verifies and consumes them without rebuilding;
- the Server migration CLI runs rootless and one-shot without the Docker socket, proves an empty
  second plan, deletes its temporary bootstrap client, and negatively verifies absence;
- build/publish both `weave-backend` and `weave-mcp-server`; MCP is a separate workload boundary
  and must not be folded into the member API process;
- repeated migration apply validates the existing receipt without mutation. Repeated `install.sh`
  must preserve volume identity, credentials, organization/person identity, and sessions;
- Nextcloud trusts only the exact Caddy container address on the deployment network. Do not widen
  trusted proxies or disable brute-force protection;
- the direct backend/MCP/provider host ports are loopback-bound. Public `/actuator` is denied;
  `/api/health/live` and `/api/health/ready` remain the public operational contract.

Transition boundary: Matrix and Nextcloud are optional provider profiles and no general identity
reconciler remains. The deferred FGAP migration is qualified only for backup-gated dogfood/prod;
dev and disposable E2E remain fail-closed pending their separately reviewed migration contract.

For development, run provider dependencies separately from the host server:

```bash
./gradlew :infra:composeDevDependenciesReady
./gradlew :server:serverDevBoot
./gradlew :server:serverDevHostSmoke
```

Only this `dev` host process uses H2. Flyway still owns its schema and Hibernate still uses
`ddl-auto=validate`. PostgreSQL remains mandatory for providers, integration tests, dogfood,
e2e, prod, backup/recovery, and every release claim.

## 4. Routine verification

Use these in order:

1. `WEAVE_ENV_FILE=<reviewed-env> bash weave-workspace/release-verify.sh`
2. `WEAVE_ENV_FILE=<reviewed-env> bash weave-workspace/operator-check.sh <dogfood|prod>`
3. `WEAVE_ENV_FILE=<reviewed-env> weave-workspace/compose.sh <environment> ps`

What `operator-check.sh` adds beyond `release-verify.sh`:

- confirms the core containers exist and are running
- checks loopback health endpoints for Keycloak, MAS, Synapse, and backend
- checks the public product, backend, auth, OIDC-gated Matrix facade, southbound Matrix provider, and raw Nextcloud fallback routes through the configured release URLs
- checks that the default Matrix workspace aliases resolve (`#weave-workspace`, `#announcements`, `#general`, and `#help` on the configured southbound Matrix provider)
- checks that `weave-backend` has the required server-side Files/Calendar Nextcloud actor env and that the actor user exists in Nextcloud
- checks backend and MCP readiness plus support-safe Agent Runtime state/workload-identity
  summaries; readiness closes when signing/wrapping trust, the workload-administration boundary,
  or external RuntimeStateStore is unavailable
- verifies the canonical `weave-workspace` CalDAV collection used by the provider-neutral Calendar facade; private personal calendars remain outside that service-actor projection

The default Matrix workspace is provisioned by `weave-workspace/provision-matrix-default-workspace.sh` during install. See `docs/matrix-default-workspace.md` for aliases, the owner/admin-limited `announcements` policy, and current member/guest automation limits.

## 5. Backup expectations

The repository does not schedule or export backups. The operator supplies a mode-0700 location
outside the checkout and binds every private consistency set to the exact candidate:

```bash
WEAVE_CANDIDATE_COMMIT=<exact-sha> \
WEAVE_BACKUP_ROOT=/var/backups/weave \
WEAVE_ENV_FILE=/absolute/path/to/reviewed-dogfood.env \
bash weave-workspace/backup.sh dogfood
```

The helper quiesces the application/provider writers, writes the consistency set, and restarts
only the services it observed running. A successful directory is named
`weave-<profile>-<UTC timestamp>-<candidate prefix>` and contains exactly:

- `postgres.sql`;
- `nextcloud-data.tgz`, `synapse-data.tgz`, `keycloak-data.tgz`;
- `caddy-data.tgz` and `caddy-config.tgz`;
- `matrix-appservice.tgz`;
- `private-config-secrets.tgz`, containing the generated, SecretRef, and TLS consistency roots;
- `BackupManifest.json`, using only `weave.compose-private-backup.v3` and binding the candidate,
  candidate manifest, profile, Compose project, database fingerprint, the immutable PostgreSQL
  dump-client image, exact non-template database inventory and its support-safe digest, quiesced
  services, runtime inventory, byte counts, and SHA-256 hashes.

Every listed artifact and the manifest are private and must never be uploaded as support evidence.
Backup creation uses an owner-only staging directory and publishes the completed consistency set
with one atomic rename. A failed backup removes only its exact staging directory after the
quiesced services have been restarted.
Support bundles are not backups and exclude database content, provider/member content, raw logs,
credentials, signed assertions, and private configuration.

The backup remains bound to `WEAVE_CANDIDATE_COMMIT`, the lane authority, and to
`WEAVE_CANDIDATE_MANIFEST_DIGEST`. Image provenance is a separate support-safe mapping from that
lane commit to `WEAVE_IMAGE_SOURCE_COMMIT` and the four immutable image IDs; do not relabel a
lane-built image or substitute the source commit in backup receipts.

Minimum expectation before calling the stack release-ready:

- backups run on a schedule owned by the operator
- at least one recent backup is stored off-host or on snapshot-backed storage
- one isolated restore/adoption rehearsal has verified every archive and service database;
- one current support-safe restore receipt is bound to the manifest hash and candidate.

## 6. Restore outline and smoke

The repository currently exposes only a non-mutating v3 integrity preflight:

```bash
WEAVE_RESTORE_PREFLIGHT_ONLY=true \
  bash weave-workspace/restore-private-backup.sh <private-backup-dir>
```

The validator rejects the retired v1 schema, compatibility variants, path traversal, unsafe links,
special archive members, missing artifacts, and any size/hash mismatch. Direct persistent restore
apply remains `Guarded`; `restore-private-backup.sh` has no mutating code path. A reviewed
Compose/control-store restore workflow must prove an isolated destructive rehearsal, exact named
resources, credential continuity, database/volume reconciliation, rollback, and post-restore
identity/session/provider readiness before apply can be enabled.

For the one-time migration from the former unlabeled runtime, run:

```bash
WEAVE_CANDIDATE_COMMIT=<exact-sha> \
WEAVE_BACKUP_ROOT=/var/backups/weave \
WEAVE_ENV_FILE=/absolute/path/to/reviewed-dogfood.env \
bash weave-workspace/adoption-rehearsal.sh dogfood
```

This backs up the existing exact-named runtime, restores all volume inventories and PostgreSQL
service databases in an isolated namespace, verifies the Weave realm and private SecretRef
continuity, and emits an owner-controlled mode-`0600`, candidate-bound adoption receipt at the
canonical generated-state path. Export that exact path as `WEAVE_ADOPTION_RECEIPT` for the
subsequent `:infra:composeDogfoodUp`; the runtime rejects a missing, stale, symlinked, weakly permissioned,
wrong-candidate, wrong-project, or resource-incomplete receipt. The former state is retained as
restricted migration evidence, not an executable rollback engine.

Do not use that adoption path for the manifest-bound Fresh Start. The hard cut has no legacy
credential migration or state adoption. Before requesting its exact destructive approval, run:

```bash
WEAVE_CANDIDATE_COMMIT=<exact-sha> \
WEAVE_CANDIDATE_MANIFEST_DIGEST=sha256:<exact-manifest-digest> \
WEAVE_BACKUP_ROOT=/private/mode-0700/path \
WEAVE_ENV_FILE=/absolute/path/to/reviewed-dogfood.env \
bash weave-workspace/fresh-start-backup-rehearsal.sh dogfood
```

This command quiesces the exact former runtime, creates the same private Compose v3 consistency
set, restores every archived provider volume and PostgreSQL service database into a newly generated
isolated namespace, verifies the Weave realm, and removes every rehearsal-owned container, network,
volume, and temporary password file. Its mode-`0600` support-safe receipt is written inside the
private backup directory as `FreshStartBackupRehearsal.json`. The receipt explicitly records
`legacyStateMigrated=false`, `adoptionAuthorized=false`, and `cleanupVerified=true`; it is recovery
evidence for the hard cut, never authority to import or attach the restored state.

The restore server and SQL client both use the immutable PostgreSQL image recorded from the source
runtime. Provider-volume extraction uses the candidate's immutable GNU-tar helper without network
access. The receipt binds both helper images, the candidate manifest, the database-inventory
digest, exact provider-volume inventory digests, and successful cleanup.

When `freshStartPlan` uses `-PrecoveryDecision=verified-backup`, also supply
`-PrecoveryReceipt=/absolute/private/path/FreshStartBackupRehearsal.json`. Planning rejects a
missing, stale, weakly permissioned, candidate-mismatched, non-cleaned, migration-authorizing, or
manifest-mismatched receipt. Only the receipt SHA-256 and the support-safe
`-PrecoveryEvidenceRef=...` are written to the canonical deletion plan.

`restore-smoke.sh` does not restore or delete data. After a separately approved restore/rehearsal it
revalidates the v3 consistency set, runs `operator-check.sh`, and verifies Matrix Application Service
mounts plus Agent Runtime trust/readiness. It emits `weave.compose-restore-receipt.v2` with only a
manifest hash, backup-ID hash, exact candidate/profile/project binding, and support-safe outcomes.
If a deliberately empty disposable Matrix database must be reprovisioned, use:

```bash
WEAVE_RESTORE_SMOKE_REPROVISION_MATRIX=true bash weave-workspace/restore-smoke.sh <backup-dir>
```

That option reruns only the idempotent default Matrix workspace projection before the checks.

For artifact-only preflight without touching a live stack, use:

```bash
WEAVE_RESTORE_SMOKE_ARTIFACTS_ONLY=true bash weave-workspace/restore-smoke.sh <backup-dir>
```

Artifact-only mode performs full manifest/hash/archive integrity verification, emits a non-release
receipt, and explicitly records that no restore or service readiness was proven.

### Lost or expired pending identity

Use Keycloak's normal invitation resend/revoke lifecycle first. An expired email link is not a
reason to delete or replace the user. After a successful OIDC sign-in, the provider-neutral
session-reconciliation use case updates native organization entitlement and the client performs at
most one standard refresh-token grant before workspace bootstrap.

The former `Dogfood Pending Identity Recovery` mutation path is deliberately guarded. Its root
supervisor, sanitizer image, custom provider JAR, direct member helper, and runtime-profile
assumptions are retired authorities. The workflow refuses execution and cannot emit readiness.
A future destructive retirement path must run against an isolated `e2e` clone of the dogfood
identity topology, prove current private backup and isolated restore rehearsal, bind an exact
subject and tombstone, run through the Server identity boundary, and remain unable to delete active
or ambiguous identities. It must never use the persistent dogfood namespace as its test fixture.

Physical-iPhone readiness still requires the normal user to complete the current invitation,
perform a normal OIDC sign-in, and pass the standard Test Stack Deploy plus physical-device
acceptance chain. Neither an email resend nor a guarded recovery dispatch proves readiness.

## 7. Stop, clean rebuild, and destructive reset

Use the least destructive action that solves the problem:

1. **Stop/restart:** `WEAVE_ENV_FILE=<reviewed-env> weave-workspace/compose.sh <profile> down`,
   followed by `install.sh <profile>`. `down` never removes named volumes or SecretRefs.
2. **Repair declared drift:** rerun render/config/prepare and the explicit Keycloak migration.
   Require the receipt-bound second plan to be empty; normal startup never reconciles identity.
3. **Rollback:** restore the previous coherent application image set and control/data snapshot under
   the reviewed release procedure. Do not rely on old/new API coexistence.
4. **Isolated E2E cleanup only:** `teardown.sh e2e --isolated` requires the deterministic run
   namespace, exact ownership labels, exact candidate, identity evidence, and explicit volume-removal
   confirmation. It refuses persistent resources and any label mismatch.

There is no generic persistent destructive reset or clean-rebuild command. Persistent dogfood/prod
deletion, crypto-shred, or full restore requires its own step-up, backup, evidence, and approval
workflow. Never delete named volumes, generated trust, or credentials merely to repair a failed
deployment.

## 8. Minimum observability and triage

Useful commands:

```bash
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
docker logs --tail=100 weave-backend
docker logs --tail=100 weave-mcp-server
docker logs --tail=100 weave-keycloak
docker logs --tail=100 weave-mas
docker logs --tail=100 weave-synapse
docker logs --tail=100 weave-nextcloud
bash weave-workspace/operator-check.sh
bash weave-workspace/release-verify.sh
```

`operator-check.sh` starts by diagnosing the Weave-local `weave_synapse_data` volume for `weave-synapse`: expected owner `991:991`, mode `0750` on `/data` and `/data/media_store`, and write access for the Matrix signing-key path. This check is intentionally scoped to `weave-synapse` and must not be used as evidence about any separate `homelab-synapse` service.

For support requests, prefer a redacted support bundle over hand-copying raw logs:

```bash
WEAVE_ENV_FILE=<reviewed-env> bash weave-workspace/support-bundle.sh dogfood
```

To include the backend's cached provider capability health, pass a short-lived owner/admin/operator bearer token with `admin_control_plane.readiness_read`; the bundle calls only the authenticated `/api/admin/provider-capability-health` route and strict-allowlists its support-safe schema. A workflow may instead stage either that exact response or the support-safe `weave.provider-health-metrics-summary.v1` emitted from loopback-only cached Actuator gauges in `WEAVE_PROVIDER_HEALTH_EVIDENCE_FILE`. The two schemas have independent exact-field allowlists; a raw Actuator response, unknown field, inconsistent overall state, probe-triggering source, or raw metric payload is rejected.

When Nextcloud authentication throttling is suspected, run:

```bash
bash weave-workspace/nextcloud-auth-security-audit.sh --output /tmp/nextcloud-auth-audit.json
```

The audit is read-only. It groups recent invalid-authentication and throttle events by salted source hash, classifies known Caddy/backend container addresses, and reports only canonical method/route classes plus aggregate configured-backend-actor attribution. It never prints raw addresses, actors, URLs, messages, or provider payloads, and never changes protection or resets counters.

Protected deployment automation may read cached Micrometer measurements directly from
`http://127.0.0.1:${WEAVE_BACKEND_HOST_PORT}/actuator/metrics`. These host-local reads do not
execute provider probes. Do not publish or proxy this endpoint; use the authenticated admin
provider-capability-health facade for product/control-plane access.

For the persistent dogfood candidate workflow, capture `persistent-dogfood-observation.sh capture`
before the first install and after the second, then run `compare`. The helper requires the
persistent dogfood scope and rejects isolated-E2E coordinates. It compares only hashes/counts for
the immutable human subject, Mailpit volume/message state, TLS CA/leaf identity, and active session
set. Database content is never copied into evidence.

Set `WEAVE_SUPPORT_BUNDLE_RUN_CHECKS=true` when you want the bundle to include fresh `operator-check.sh` and `release-verify.sh` output. The bundle includes public URL/config summaries, container status, disk/volume summaries, strict cached provider-health evidence, the sanitized Nextcloud authentication-source audit, and only a count/content-set hash for recent smoke/operator/verify artifacts found under `.generated`. Raw service/provider logs and raw prior diagnostic artifacts are deliberately excluded because generic redaction cannot prove removal of actor/content identifiers. It is a diagnostics artifact only: it is **not** a backup and cannot restore Postgres databases, Matrix media, Nextcloud files/calendar data, Caddy ACME state, or generated secrets. Review the archive before sharing externally.

Escalate quickly when any of these fail:

- Keycloak discovery does not match the public issuer URL
- backend readiness is not `up`
- Nextcloud `status.php` is not installed/healthy
- Matrix delegated auth discovery, client versions, or `/authorize` is unavailable
- MCP protected-resource metadata or loopback readiness is unavailable
- Agent Runtime signing/wrapping trust is missing, a per-cell Keycloak client is ambiguous, or
  RuntimeState readiness reports a storage/key failure

## 9. Known single-host limits

These are still intentionally out of scope for this repo slice:

- automated backup scheduling
- secret manager integration
- external KMS/secret-manager custody for RuntimeState wrapping keys (the mounted file-key adapter
  is dogfood-only)
- production Weaver cell scheduling and cross-node checkpoint reconstruction
- connector runtime enablement, including reviewed provider callback exposure and revocable provider secret references
- HA or zero-downtime upgrades
- centralized metrics or alert routing
- fully declarative Nextcloud bootstrap beyond the supported `install.sh` path

## Sprint 12 provider-aware backup, restore, upgrade, and schema migration contract

Backup/restore order for a self-hosted Weave stack is:

1. freeze release promotion and capture generated config, release version, provider manifests, and support-safe readiness summary;
2. backup secrets/TLS material through the operator secret store without embedding raw values in evidence;
3. backup databases for Keycloak, MAS/Synapse, Nextcloud, OpenProject/Boards, LiveKit/TURN state where applicable, Weave backend/admin metadata, and provider selection/audit stores;
4. backup data volumes for Synapse media, Nextcloud files, OpenProject attachments, Weave evidence artifacts, and static admin/client assets;
5. archive generated config, schema versions, migration manifests, and support-safe content hashes; and
6. run artifact-only restore smoke before promotion, then one live rehearsal before stronger production claims.

Restore order reverses dependencies: secrets/TLS and generated config, databases, data volumes, identity (Keycloak), Matrix/MAS/Synapse, Nextcloud, OpenProject/Boards, LiveKit/TURN, Weave backend, Admin Console/client, then readiness checks and acceptance gates.

Provider schema migrations require dry-run evidence, a backup-required marker, rollback/archive evidence, post-migration readiness checks, and an audit-linked approval. Missing stale, forged, or incompatible evidence blocks apply.

Support bundles redact WOPI/JWT material, SCIM payloads, Matrix E2EE keys/plaintext, provider payload bodies, OAuth tokens, cookies, private keys, and Weaver SecretRefs. Observability minimums include health/readiness, backup freshness, certificate expiry, disk pressure, provider readiness, auth spikes, and LiveKit/TURN reachability.
