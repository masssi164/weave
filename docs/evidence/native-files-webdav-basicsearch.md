# Native Files WebDAV basicsearch evidence

Status: incremental candidate evidence for the bounded RFC 5323 `DAV:basicsearch` portion of
issue #1326. This does not close the complete WebDAV, change-sync, portability, or production
qualification wave.

## Proven boundary

- The protocol parser accepts only the contracted XML media types and 65,536-byte body ceiling,
  disables external resolution and DTD/XInclude/schema access, validates the closed grammar,
  properties, operators, expression budget, scopes, ordering, and result limit before provider
  access, and emits the contracted DAV conditions.
- Authorization precedes a single canonical metadata enumeration. The native JPA adapter applies
  organization, Space, lifecycle, scope depth, canonical path/FileId order, and the 1,001-row
  proof ceiling in persistence; no file content is read.
- The evaluator implements RFC three-valued Boolean logic, exact unsigned numeric and UTF-8 byte
  comparisons, `%`/`_`/backslash LIKE semantics, deterministic requested ordering, always-ascending
  path/FileId tie breakers, and exact canonical-ID incomplete/duplicate fail-closed behavior.
- The WebDAV projection advertises `SEARCH` and `DASL: <DAV:basicsearch>` only for a selected
  adapter with a fresh operation-specific qualification; blocked or expired records fail before
  enumeration without coupling metadata SEARCH to content readiness. Successful responses are
  `207 application/xml; charset=utf-8`, no-store,
  contain requested 200/404 property statuses plus canonical IDs, and append an arbiter 507 row
  whenever the scan or result ceiling makes the response partial.
- The MCP Files client emits standard `DAV:limit`, escapes user text into an RFC LIKE literal,
  uses an exact canonical-ID equality lookup bounded to two results, and rejects a 507 arbiter
  rather than consuming partial rows.

## Executable evidence

```text
./gradlew :weave-files-core:test :weave-files-core:check
./gradlew :server:test --tests 'com.massimotter.weave.backend.controller.protocol.FilesWebDavSearchParserTest'
./gradlew :server:test --tests 'com.massimotter.weave.backend.service.files.WebDavBasicSearchEvaluatorTest'
./gradlew :server:test --tests 'com.massimotter.weave.backend.service.FilesFacadeServiceTest'
./gradlew :server:test --tests 'com.massimotter.weave.backend.controller.FilesWebDavControllerTest'
./gradlew :server:test --tests 'com.massimotter.weave.backend.controller.FilesWebDavRealSocketStreamingTest'
./gradlew :server:test --tests 'com.massimotter.weave.backend.service.files.WeaveNativeFilesAdapterTest'
./gradlew :server:postgresJpaTest --tests 'com.massimotter.weave.backend.files.adapter.JpaFilesAuthorityRepositoryPostgresTest'
./gradlew :weave-mcp-server:test --tests 'com.massimotter.weave.mcp.FilesWebDavClientTest'
./gradlew specCorpusConformance
python3 tools/files_webdav_facade_acceptance_check.py
```

The real-socket test proves OPTIONS/DASL and SEARCH request/response behavior over an actual HTTP
socket, including selected-property 404 and truncation 507 projection. It deliberately replaces
the facade with a test seam. The canonical and PostgreSQL tests independently prove bounded
metadata enumeration, but an authenticated two-process integrated run against PostgreSQL and the
actual native BlobStore remains required before this capability is protected production truth.

## Remaining

- protected exact-head CI after the stacked specs and implementation bases merge;
- authenticated two-instance SEARCH against the same committed PostgreSQL snapshot;
- complete RFC 6578 sync-collection projection and WebDAV `If` state-token integration;
- canonical Files rights, import/export, delta reconciliation, provider portability, and restored
  real-WebDAV mutation proof.

Before merge, the PR must record its exact commit SHA and successful protected-check URLs. This
page cannot self-reference the commit that contains it.
