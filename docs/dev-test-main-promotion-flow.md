# Feature → Dev → Dogfood E2E → HIL → Main

Status: active development policy.

Weave is still in active development with one human developer. `dev` and `dogfood` optimize for short, repeatable feedback. The current #1470 delivery also promotes a demonstrated dogfood tree to protected `main` without publishing production.

## Branches

- `dev` is the normal integration branch. Feature branches return here through ordinary PR CI.
- `dogfood` is the protected exact automated candidate and resettable LAN branch. Its promotion PR runs deterministic checks; mandatory integrated suites run on the protected Dogfood commit before deployment/HIL readiness.
- `main` is release-capable source truth after a protected promotion. Production publication remains a separate explicit decision. A future production-hardening ADR must define backup, migration, immutable release-image, approval, and rollback controls before an externally supported production launch.

## Deterministic Dev and exact qualification

Apply pinned `steering/devops-conformance.md` and ADR 0022. Ordinary Dev PRs run compilation, formatting/static analysis, relevant unit/module and architecture/security checks, specification/acceptance conformance, code-first export freshness, independently selected consumer drift, and relevant static infrastructure/docs checks. Complete dependency inputs govern impact selection; unknown impact runs the stronger gate.

The protected Dogfood candidate must pass Full Compose E2E with two concurrent independent real Apple Silicon iOS Simulator apps. Both product actors are ordinary members of the prepared organization and shared Space, using the same versioned scenario as integration and Dogfood recipes. Current role and Space grants are read back before collaboration. Setup/admin probes are separate; full onboarding and MCP acceptance remain separately selectable. Native macOS desktop testing is manual-only.

Hosted builders provide the runtime, integration driver and iOS artifacts. Complete component fingerprints permit reuse with original producer identity; the generated composition verifies their compatibility with the current candidate. The Mac runner consumes those artifacts for native execution and deployment, without a hidden compilation fallback in the protected path. Every new candidate still needs fresh live collaboration and exact cleanup. Infrastructure/preflight and product assertion failures both block readiness with distinct diagnostics. A Candidate Cut, manually assembled release manifest or Fresh Start plan is unnecessary; automated composition verification remains mandatory. Live and human evidence stays non-cacheable.

## Direct Compose lifecycle

The supported local commands are:

```sh
./gradlew devUp
./gradlew devRun
./gradlew devDown

./gradlew dogfoodUp
./gradlew dogfoodDown
./gradlew dogfoodReset
```

`dogfoodUp` consumes the verified hosted runtime composition and is idempotent. `dogfoodReset` uses the same verified inputs, deliberately deletes only the three Compose-managed dogfood session volumes and starts a clean development realm:

- PostgreSQL;
- native Files blobs;
- Mailpit messages.

The local CA and leaf certificates live outside Docker in the private operator-owned host TLS directory outside Docker. Reset must never remove or rotate them. Reviewed secrets and the reviewed environment file are also outside the reset boundary. Caddy runtime data, Keycloak container data, and optional provider state are not default persistent dogfood volumes.

Native Files, Calendar, and Chat are the default product providers. MinIO, Matrix, Nextcloud, ARC, Weaver, and other provider labs are explicit profiles and are not required for a normal `devUp` or `dogfoodUp`. The full isolated E2E profile may still enable the dependencies required by the journey it tests.

OpenTofu is not part of the active `dev` or `dogfood` lifecycle. Production infrastructure as code can be reconsidered when a real production target exists.

## Dogfood deployment

The `deploy-dogfood` job in `.github/workflows/live-stack-e2e.yml` runs only after mandatory exact-candidate Compose and two-member iOS qualification succeeds on a `dogfood` push. It verifies protected ancestry, consumes the qualified runtime artifacts and runs `./gradlew dogfoodUp` on `weave-live-mac-mini`. Prepared ordinary members exercise real authenticated Chat, Files and Calendar against that deployment. Keeping qualification and deployment in one workflow avoids a second default-branch workflow-dispatch dependency.

There is no environment approval, destructive token, candidate-image resolver, or backup rehearsal in routine development dogfood.

## Prepare the physical iPhone

After all mandatory dogfood automated qualification is green, manually run `Prepare Human Test` with the exact dogfood SHA. It:

1. verifies all mandatory exact successful qualification and deployment evidence;
2. starts dogfood twice and checks unchanged TLS fingerprints;
3. optionally creates the first-owner invitation in private Mailpit;
4. verifies the activated owner's default Space and current access;
5. signs the qualified hosted device artifact with the stable development identity;
6. installs over `com.massimotter.weave` on the paired iPhone and observes ordinary startup.

The stable Apple team and bundle preserve Developer App trust and app storage. Deinstallation is not part of this workflow. The local CA should need full trust on the iPhone only once.

First-owner activation is separate setup; invitation links remain private at the reviewed `WEAVE_MAILPIT_URL`. Routine preparation preserves the owner app/session and accepts either the retained workspace or normal sign-in. Its `weave.hil-preparation/v4` receipt binds the actual successful qualification attempt, composition and installed binary, and leaves `ownerHIL: pending`. Personal sign-in is the expected next human action when needed. The tester then reports actual sign-in/session, navigation, Chat, Files, Calendar, accessibility, revoke/regrant and identity continuity results, including the actual owner/admin/member role. Human outcomes are `passed`, `failed` or `not-available`; absent mandatory surfaces block Main promotion. Optional Weaver/MCP and broad admin journeys retain separate acceptance.

## Promotion discipline during development

Before merging `dev` to `dogfood`:

- normal PR CI is green;
- acceptance/scenario mappings and review are complete;
- the promotion tree matches the intended `dev` tree;
- no unrelated release-hardening work is mixed into the promotion.

Before making a future production release, write and accept the production-hardening ADR and add only the controls that the chosen deployment target actually needs.

For the current #1470 mainline delivery, promote the exact deployed and human-tested `dogfood` tree to `main` by PR. The protected Main Promotion Gate checks qualified Dogfood ancestry and proposed tree/spec equality, the candidate-bound composition and two-member iOS collaboration, deployment and physical preparation, and the separately reported `Weave human test v2` result described in `docs/gitflow-pr-workflow.md`. A green disposable E2E alone is insufficient for this promotion. The merge itself does not publish a production release.

An unchanged candidate reuses qualification for Main. A changed candidate source/tree invalidates its evidence; newer Dev commits alone do not. No equivalent integration reruns solely because the candidate advances to Main.
