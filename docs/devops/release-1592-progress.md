# Release qualification implementation progress for #1592

This is an implementation and evidence record. The pinned specification corpus remains normative. No automation or human result is implied by a plan or a merged specification.

## Scope and authority

Primary task type: `cross-repo-contract`. The accepted corpus governs bootstrap, delivery and persistence; this monorepo implements and records evidence. Scoped client, Server, infrastructure and native-test work uses separate worktrees; the integration branch preserves the existing release issue graph and unrelated checkout changes.

| Contradiction | Authority and historical origin | Affected consumers and resolution |
| --- | --- | --- |
| Two discovery routes/models | Accepted OrgManifest v2 and release profile supersede the earlier v1 organization manifest projection | Flutter, generated User/Admin consumers and browser completion consume one v2 schema at the existing User route; remove v1 and its generator workaround |
| Provider configuration in persisted profiles | Canonical v2 contains the necessary transport values; older profiles independently stored duplicated fields | Store only origin, validated manifest and timestamp; derive transport and refresh origin-bearing legacy records once |
| Hibernate update versus immutable Flyway chain | Earlier ADR0018 conflicted with shipped migration history and later initializer receipts | Accepted PR115/ADR0023 establishes Flyway-only DDL, early serving fence, independently replayed catalog and separate migration credentials |
| Creator-only versus shared Files access | Previously private-only implementation did not satisfy accepted shared workspace UI collaboration | V13 preserves PRIVATE rows; explicit SPACE scope uses current grants for A/B and denies C, revoked users and stale writes |
| Single iOS proof versus collaboration acceptance | Historical native v1 run predates owner clarification in #1592 and accepted PR155 | Native v2 requires overlapping independent real Flutter apps and seven scenario proofs; old evidence remains historical |
| Historical enterprise/Candidate Cut bootstrap controlling release | Superseded documents and optional fixture tasks still appeared in an old aggregate gate | Align the deterministic release aggregate with accepted direct Compose delivery; keep historical provenance without reinstating its architecture |
| Issue, branch and candidate statuses conflated | Historical Project fields have different meanings; accepted delivery requires verified candidate evidence | Keep existing Status and Delivery stage fields; actual promotion validator gates HIL/Main and rejects superseded candidates |

## Accepted decisions and contradiction resolution

- Corpus PR155 merged at `b8b5f11f18b5cee9ddca8b095f498a721351a61e`: OrgManifest v2 is the single bootstrap contract delivered through generated User `GET /api/platform/config`. The obsolete v1 `/api/organization/manifest` route/model and generated-client postprocessor were redundant authorities and have been removed. Domain capability queries remain authenticated per-member product authorization, not a second bootstrap contract.
- Existing corpus PR115 was narrowed to the persistence authority discrepancy and merged at `2f73c9d0962d86cd797c95b7ed0894c9337ccc11`. ADR0023 supersedes the older Hibernate-update epoch while preserving Java/Spring/JPA boundaries and the immutable Flyway migration history already on Main. The lock now pins this accepted revision.
- Files and Calendar remain generated User API consumers; Matrix uses the accepted native Client-Server facade. No provider discovery, northbound DAV, independent Matrix OAuth registration, deferred Runner or Calls scope is added.
- Existing private Files rows already carried workspace references. A forward V13 PRIVATE/SPACE discriminator preserves existing privacy instead of inferring shared access. Shared access uses current Space grants; creator provenance is not global permission.
- Persisted client profiles now contain only organization origin, validated manifest and validation time. Transport values are derived, and origin-bearing legacy profiles are one-way migration input requiring fresh bootstrap before authentication.
- Standard Dogfood 80/443 bindings are represented in its example profile; other profiles retain configurable ports. Explicit HTTP redirects target each configured public HTTPS origin, including nonstandard ports.
- The new native evidence version rejects missing, sequential, single-client, reused-identity, API-only or incomplete collaboration proof. Human acceptance remains an explicit owner action after exact automated/deployment/physical preparation evidence.

## Verified results on 2026-10-12

- Accepted corpus Main CI [38280978951](https://github.com/masssi164/weave-specs/actions/runs/38280978951) passed for `2f73c9d0962d86cd797c95b7ed0894c9337ccc11`.
- Authorized guarded reset executed against protected Dogfood `d9a25cb31a6a5f9f9324e66876dad0fa2e2853ad`, preserving TLS/CA, reviewed secrets and unrelated resources. Reset/schema initialization/receipt check passed; backend, MCP, gateway, Keycloak, PostgreSQL and Mailpit were healthy. This restores the prior deployment; it does not qualify the new source. Support-safe pre-reset schema diagnostics and historical failure evidence were preserved.
- Server canonical-bootstrap/authorization acceptance: 40 focused executions passed. Member entry page: 18 focused executions passed, with canonical link/QR, English/German labels and no request-derived organization authority.
- Shared Files: 37 deterministic executions and four real PostgreSQL tests passed, including A/B shared access, C denial, private preservation, revocation, stale versions and V12-to-V13 upgrade.
- Matrix participant directory: 69 deterministic executions and six real PostgreSQL tests passed, including bounded paging, current Space membership and organization/issuer isolation. Native SDK tests: 26 passed; focused Flutter Chat UI/repository tests: 36 passed.
- Native invitation acceptance: 98 focused Flutter Chat/bridge tests passed; the isolated client suite passed 640 tests with one existing offline-only skip. Actual native-hook Apple Silicon Rust compilation passed. The subsequent Files scope-rebuild correction passed 73 focused tests. These are deterministic checks, not iOS collaboration proof.
- Canonical profile persistence/refresh: 28 focused Flutter tests and static analysis passed. The earlier full-client baseline passed 655 tests with one existing offline-only skip; source changes require subsequent focused and CI validation.
- Promotion and Project verifier: 31 regression tests passed, including actual rejection of historical/single-iOS artifacts before physical preparation, negative dual-simulator/authorization vectors and superseded protected-head rejection at each physical boundary. These test the gate, not real native journeys.
- Schema authority follow-up: 23 serving checks and 12 real PostgreSQL checks passed on the selectively preserved PR1445 early guard and extended catalog. Unexpected view/function/type/sequence/relation cases fail; exact owned-lock termination recovers to valid receipt.
- PostgreSQL role probe against the pinned 16.9 image passed repeated convergence, runtime DML, CREATE/ALTER/DROP/schema denial and preservation of representative existing rows. Only the probe-owned disposable container was removed.
- Infrastructure static checks passed after updating obsolete port assertions. Pinned Caddy adaptation passed; product `/api` paths are preserved and HTTP redirects use configured public origins. No live 80/443 deployment change has occurred yet.
- Integrated Server manifest/controller validation passed 28 executions, including unsafe ports/paths and canonical browser completion. Documentation, 252 acceptance mappings, 157 corpus entries and 12 spec packages passed.
- Project2 was read before changes. Existing Status and Delivery stage fields were retained; #1592/#1470/#1481 reflect In Progress. No HIL or Main transition was made.

## Outstanding release evidence

The protected candidate remains the prior Dogfood commit until normal reviewed integration and promotion. New shared Files/Chat controls, schema catalog/role conformance and dual-simulator runner work are being integrated. Both iOS applications have not yet executed the mandatory concurrent journey on this source. Chat, Calendar, Files, recovery, authorization and accessibility results must be reported independently from the actual run. No new physical installation, launch, human acceptance or release promotion is claimed.

Keep #1470 and #1481 open. HIL entry requires successful mandatory automated integration, deployment and physical iPhone preparation for the same unchanged candidate; the existing single-simulator evidence cannot satisfy #1592.
