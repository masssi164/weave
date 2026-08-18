# Operator runbook

This is the minimum operator layer for the monorepo `infra/` single-host path.
It is meant to remove the remaining tribal knowledge around install, verify, recovery, and routine maintenance.

## 1. Before install

Prepare these explicitly:

- LAN DNS for the reviewed product, API, auth, and native Files endpoints;
- a private reviewed copy of `weave-workspace/environments/dogfood.env.example`,
  `e2e.env.example`, or `prod.env.example`, stored outside the checkout;
- exact digest references for production images; dogfood may use exact local image IDs built from
  the clean checked-out commit;
- absolute `WEAVE_GENERATED_ROOT`, `WEAVE_SECRET_ROOT`, and `WEAVE_TLS_ROOT` paths;
- TLS certificate/key material in the stable operator-owned `WEAVE_TLS_ROOT` outside Compose
  volumes. A dogfood reset must not rotate or remove it.

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

## 3. Development and dogfood lifecycle

```bash
cd weave
export WEAVE_ENV_FILE=/absolute/path/to/reviewed-dogfood.env
./gradlew dogfoodUp
```

Notes:

- `compose.yaml` plus the selected `compose.dev.yaml`, `compose.dogfood.yaml`,
  `compose.prod.yaml`, or `compose.e2e.yaml` overlays is the only deployment model;
- `dogfoodUp` requires a clean exact checkout, builds backend and MCP from that commit, prepares
  the canonical Compose model, waits for readiness, and starts no alternate deployment engine;
- repeated `dogfoodUp` preserves the PostgreSQL, native Files, and Mailpit session volumes;
- `dogfoodDown` stops the stack and preserves those volumes and the external TLS directory;
- `dogfoodReset` removes only the fixed dogfood project and the three session volumes, performs
  the bounded one-time cleanup of the known unlabeled legacy Weave stack when present, and starts
  an empty stack. It needs no backup, receipt, deletion plan, issue comment, or approval token;
- Matrix, Nextcloud, MinIO, and Weaver are absent from the default dogfood profile;
- the direct backend/MCP host ports are loopback-bound. Public `/actuator` is denied;
  `/api/health/live` and `/api/health/ready` remain the public operational contract.

Production migration, backup, and restore policy remains separate and must not be copied into the
resettable dogfood feedback loop.

For development, run provider dependencies separately from the host server:

```bash
./gradlew devUp
./gradlew devRun
# In another shell after development:
./gradlew devDown
```

Only this `dev` host process uses H2. Flyway still owns its schema and Hibernate still uses
`ddl-auto=validate`. PostgreSQL remains mandatory for providers, integration tests, dogfood,
e2e, prod, backup/recovery, and every release claim.

## 4. Routine verification

Use these in order for the default dogfood stack:

1. `./gradlew dogfoodUp`
2. `WEAVE_ENV_FILE=<reviewed-env> infra/weave-workspace/compose.sh dogfood ps`
3. run the full isolated E2E task before handing the same commit to a human tester.

Provider-specific operator checks apply only when their explicit optional profile is selected; they
must not make Matrix, Nextcloud, object storage, or Weaver a default dogfood prerequisite.

## 5. Production backup boundary

Backups are not required before `dogfoodReset`. The three dogfood volumes are development-session
convenience and carry no durability promise. The repository's backup and restore helpers belong to
the separately reviewed production/recovery boundary described below.

The repository does not schedule or export backups. The operator supplies a mode-0700 location
outside the checkout and binds every private consistency set to the exact candidate:

```bash
WEAVE_CANDIDATE_COMMIT=<exact-sha> \
WEAVE_BACKUP_ROOT=/var/backups/weave \
WEAVE_ENV_FILE=/absolute/path/to/reviewed-prod.env \
bash weave-workspace/backup.sh prod
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

Minimum expectation before calling a future production stack release-ready:

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

The former unlabeled development runtime is not adopted. The first `dogfoodReset` validates and
removes only the closed legacy Weave container, volume, and network names before Compose starts
the new native stack. Bind mounts and the external TLS directory are never cleanup targets.

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

Physical-iPhone readiness requires the normal user to complete the invitation, perform a normal
OIDC sign-in, and test the exact dogfood commit after full integrated E2E is green.

## 7. Stop, clean rebuild, and destructive reset

Use the task matching the intended session behavior:

1. **Start/update:** `./gradlew dogfoodUp`; existing session volumes and TLS remain.
2. **Stop:** `./gradlew dogfoodDown`; existing session volumes and TLS remain.
3. **Empty development session:** `./gradlew dogfoodReset`; exactly the fixed project, its network,
   and its PostgreSQL/native Files/Mailpit volumes are replaced. TLS remains.
4. **Isolated E2E cleanup:** `teardown.sh e2e --isolated` requires the deterministic run
   namespace, exact ownership labels, exact candidate, identity evidence, and explicit volume-removal
   confirmation. It refuses persistent resources and any label mismatch.

`dogfoodReset` never applies to production and never accepts arbitrary project or volume names.

## 8. Minimum observability and triage

Useful commands:

```bash
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
docker logs --tail=100 weave-backend
docker logs --tail=100 weave-mcp-server
docker logs --tail=100 weave-keycloak
WEAVE_ENV_FILE=/absolute/path/to/reviewed-dogfood.env \
  infra/weave-workspace/compose.sh dogfood ps
```

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

Routine persistent deployment must preserve the existing PostgreSQL, Mailpit, Caddy, and native
Files volumes and the public TLS CA/leaf fingerprints while converging the same Compose model a
second time. Deployment automation must not obtain a realm-admin credential merely to inspect a
human subject or session. Human continuity is instead proven by the activated owner through normal
OIDC/session evidence; identity recovery uses the Keycloak database backup, not user recreation.

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
