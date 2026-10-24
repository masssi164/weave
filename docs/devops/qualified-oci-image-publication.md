# Private digest-bound OCI candidates for hosted CI and Dogfood

Status: **implementation in PR #1733, not active until merge and CI verification**. Issue: [#1731](https://github.com/masssi164/weave/issues/1731).

## Actual source state

Base: `dev@97ee14b55d127416aea1e3fbe50681f4d79b4da2`, tree `c381f8795348f1ab93d03003cd7aefc8aa44b342`, corpus pin `f049c0b1879414eb529a3df83a18224407f1a436`.

The hosted Linux `runtime-linux-amd64` producer builds Backend, MCP, Keycloak, and Runtime State images. Previously it carried a large `images.tar` through GitHub Actions artifacts and loaded it into Docker on the Mac mini. This change replaces **only** that Docker tar with an `images.json` receipt containing the exact image configuration IDs and immutable GHCR manifest digests. The pre-existing hosted component manifest, verified inputs, producer identity, composition and qualification evidence remain authoritative.

## Boundaries, in order

1. Hosted build on `dev`/`dogfood` builds each changed component once. Runtime builds publish four **private, unqualified** images as run-specific `candidate-<sha>-<run>-a<attempt>` tags to `ghcr.io/masssi164/weave-ci-{backend,mcp,keycloak,runtime-state}`. No public or production tag exists. `packages:write` and an ephemeral `GITHUB_TOKEN` are scoped to the hosted build job.
2. Each image is pushed **by existing Docker image ID**, with no second build. `images.json` records `weave.runtime-images/v2`, producer source SHA, specification lockfile digest, `linux/amd64`, image ID and the registry's immutable `@sha256:` manifest digest. The artifact producer cannot finish if publication fails.
3. Mac mini E2E downloads the current verified component and composition receipts from GitHub Actions; then supplies GHCR credentials through an explicit run-private Docker auth file under `RUNNER_TEMP`, pulls **by manifest digest**, and verifies the inspected image ID, platform, repo digest, and Backend/MCP labels against the artifact. The exact image IDs and verified candidate registry references enter the bounded storage ledger. Its v2 migration never claims ownership of legacy registry references. Retired images with only owned candidate references are removed without force; operator tags, other registry references and all running/stopped container references remain protected.
4. The two ordinary-user Chat/Files/Calendar qualification tests **still must pass**. A successful registry push alone grants no Dogfood qualification or human-test entitlement. Dogfood deployment, later HIL setup, and owner bootstrap pull the *same qualified component* by its recorded digest, without building.
5. Per-run Docker auth is cleaned up even after failure; no credentials reach Compose, uploaded evidence, persistent user Docker config, checkout or the macOS keychain. Staging the explicit auth file avoids `docker login`'s automatic credential-helper discovery. Setup resolves the already-selected local Unix Docker socket and pins it in the job environment, so a private auth config cannot switch pulls to a different daemon. Default, Docker Desktop and Colima Unix-socket contexts are supported; remote SSH/TCP/TLS contexts fail closed rather than silently falling back. The source must include the pinned specification lockfile in the runtime input closure, so changed specs cannot reuse images with stale spec labels.

The component remains reusable when inputs are unchanged, even across different candidate commits. The receipt is bound to the **original producer source SHA**, not falsely relabeled as the consumer candidate. If a registry package is removed, credentials are missing, any image is not the recorded platform/ID, or the producer/composition differs, the consumer **fails closed**. No silent TAR fallback occurs. Recover by re-publishing the verified original image or intentionally rebuilding a new candidate artifact; do not weaken identity checks.

Rollout prerequisite: GitHub Actions token must have write access to the repository's initially private GHCR packages, and the Mac mini jobs must have corresponding `packages:read`. Packages must remain private. Verify GHCR package-to-repository action access after the first publish. Capture timing, transferred bytes, Mac disk space and E2E outcomes before calling this a velocity improvement.

## Exclusions

No arbitrary-PR publication, production release, Main promotion, public package visibility, ARM64/multi-arch addition, GraalVM migration, new Secrets, or weakening of two-member Chat/Files/Calendar qualification. The physical iPhone/HIL boundary is independent.

The authoritative delivery contract remains the pinned corpus `steering/devops-conformance.md` and ADR 0022. Updating that accepted contract for registry-required image consumption is an explicit follow-up; before its acceptance this PR must not be merged into a protected delivery branch as a purported accepted-spec implementation.
