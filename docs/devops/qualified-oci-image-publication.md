# Private digest-bound ARM64 runtime candidates

Issue [#1731](https://github.com/masssi164/weave/issues/1731) owns hosted candidate image
transport. The pinned corpus `contracts/build-artifacts.md`, ADR 0022 and
`steering/devops-conformance.md` govern artifact identity, release selection and qualification.

## Hosted producer and platform identity

The automatic composition contains `runtime-linux-arm64`, `ios-simulator` and `ios-device`.
The runtime producer runs on GitHub's native `ubuntu-24.04-arm` host and builds the selected
Server, MCP, downstream Keycloak and runtime-state images for `linux/arm64`. The selected
Server domains remain Files, Calendar and Chat for release; experimental domain selections
have distinct input identities. The runtime flavor is explicitly `jvm`; ARM64 does not imply
a GraalVM native executable.

`runtime-linux-amd64` remains a separate manual workflow-dispatch variant on
`ubuntu-24.04`. Its component identity, input digest, target and registry receipt are separate.
Automatic Dogfood consumption and release evidence reject that variant. Runtime builders
require matching host and target architectures and never use emulation as a delivery fallback.
Local full-test-app builds retain their explicit manual AMD64 target.

Each platform selects its exact pinned Keycloak manifest from the same verified upstream
index. Both stock services JARs retain the reviewed integrity pin and downstream policy patch.
The runtime-state cache tag includes the architecture and verifies the actual inspected image
before reuse. The Server's Rust JNI stage compiles for the selected container architecture;
the matching OpenDAL native classifier is selected before JVM packaging.

## Registry transport and current qualification

The trusted hosted build job publishes its already built image IDs to the repository-owned
private packages `weave-ci-backend`, `weave-ci-mcp`, `weave-ci-keycloak` and
`weave-ci-runtime-state`. Run-specific candidate tags include architecture and domain/flavor
identity. They grant no release status. Before publication every image must have the requested
OS and architecture. No image is rebuilt for registry transport.

The authenticated artifact carries `images.json` with schema `weave.runtime-images/v2`:
original producer commit, specification-lock digest, platform, normalized domain selection,
JVM flavor, exact image configuration IDs and immutable GHCR manifest references. The hosted
manifest and complete candidate composition continue to bind those bytes to their original
trusted producer. Changed specifications cannot reuse stale embedded specification labels.

The Mac downloads and validates the component and composition, pulls only the recorded
manifest digests, and checks image ID, platform, registry digest and provenance. Server image
labels must also match the selected domains and runtime flavor. Missing images or credentials,
wrong architectures and stale provenance fail closed. There is no mutable-tag or TAR fallback
and no compilation on the integration/deployment runner.

Private registry credentials exist only in a mode-0600 file inside a run-private directory.
Setup preserves the already-selected local Unix Docker daemon; credentials never enter
Compose, uploaded evidence, persistent Docker configuration or the macOS keychain. Cleanup
runs after success or failure. Private package visibility and repository Actions access must
be verified when the first hosted publication runs; this change does not alter package settings.

Only verified images and their exact registry references enter the bounded storage ledger.
It retains the current set and its predecessor, preserves every container reference and
operator tag, and removes only retired IDs or digest references already owned by that ledger.
Migration from the old ledger never adopts unrelated registry references.

Fresh full Compose and concurrent two-ordinary-member iOS collaboration still qualify every
new candidate composition. Persistent deployment and physical preparation consume that exact
qualified composition, followed by distinct human acceptance. A successful image push,
deterministic check or Dogfood merge alone proves none of those observations.
