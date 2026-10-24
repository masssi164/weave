# Proposed distribution of verified Weave OCI images

Status: **design and verification plan, not an enabled publisher**. Issue: [#1731](https://github.com/masssi164/weave/issues/1731).

## Actual source state

Base: `dev@97ee14b55d127416aea1e3fbe50681f4d79b4da2`, tree `c381f8795348f1ab93d03003cd7aefc8aa44b342`, corpus pin `f049c0b1879414eb529a3df83a18224407f1a436`.

The hosted Linux `runtime-linux-amd64` producer **already builds** Backend, MCP, Keycloak, and Runtime State images. It uploads `images.tar` plus `images.json` inside a verified content-addressed GitHub Actions component artifact. Exact-candidate E2E and Dogfood check the source/spec/component manifest and `docker load` those images on the runner. This is not equivalent to publishing the images in GHCR.

## Proposed additional distribution boundary

- Keep hosted **build once → immutable component artifact → verify/reuse** intact, including original producer identity and hash checks.
- After a successfully qualified exact Dogfood candidate, a separate, least-privilege stage downloads/verifies that candidate's trusted runtime artifact **without rebuilding** and pushes selected backend/MCP image contents to `ghcr.io/masssi164/...`.
- Use the workflow-scoped `GITHUB_TOKEN` with `packages:write` only for publication, subject to effective package access; do not introduce long-lived PAT credentials.
- Retain both the original local Docker image ID and the new **registry manifest digest**. Those are different identities and must be independently verified and bound to the qualified candidate, source commit/tree, toolchain input digest and corpus pin.
- Consumers use digests; no mutable `latest` or unqualified release tag. Initial registry packages retain the default private visibility pending a deliberate distribution decision.
- Preserve Dogfood's current TAR-based consumption until a digest-pull alternative passes equivalent trust checks **and** demonstrates a worthwhile measured latency/storage improvement.
- A failed qualification, tampered/expired producer artifact, wrong architecture, failed push or contradictory manifest **fails closed**; a retry is idempotent and does not rebuild.
- A checked-in executable validation fixture covers trust, digest mapping, and failure modes before any trigger capable of writing registry packages is enabled.

## Exclusions

No arbitrary-PR publication, production release, Main promotion, public package visibility, ARM64/multi-arch addition, GraalVM migration, new Secrets, or weakening of two-member Chat/Files/Calendar qualification. The physical iPhone/HIL boundary is independent.

The authoritative delivery contract remains the pinned corpus `steering/devops-conformance.md` and ADR 0022. If GHCR becomes a required delivery dependency rather than an optional distribution output, update that contract explicitly first.
