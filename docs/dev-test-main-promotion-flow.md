# Feature → Dev → Dogfood E2E → HIL → Main

Status: active development policy.

Weave is still in active development with one human developer. `dev` and `dogfood` optimize for short, repeatable feedback. The current #1470 delivery also promotes a demonstrated dogfood tree to protected `main` without publishing production.

## Branches

- `dev` is the normal integration branch. Feature branches return here through ordinary PR CI.
- `dogfood` is the protected exact automated candidate and resettable LAN branch. Its promotion PR runs deterministic checks; mandatory integrated suites run on the protected Dogfood commit before deployment/HIL readiness.
- `main` is release-capable source truth after a protected promotion. Production publication remains a separate explicit decision. A future production-hardening ADR must define backup, migration, immutable release-image, approval, and rollback controls before an externally supported production launch.

## Deterministic Dev and exact qualification

Apply pinned `steering/devops-conformance.md` and ADR 0022. Ordinary Dev PRs run compilation, formatting/static analysis, relevant unit/module and architecture/security checks, specification/acceptance conformance, code-first export freshness, independently selected consumer drift, and relevant static infrastructure/docs checks. Complete dependency inputs govern impact selection; unknown impact runs the stronger gate.

The protected Dogfood commit must pass Full Compose E2E plus independent native macOS and Apple Silicon iOS Simulator integration against that same candidate. Each records the exact source/tree/spec identity and cleanup outcome. Infrastructure/preflight failure and product assertion failure both block readiness, with distinct diagnostics. macOS proof cannot substitute for iOS proof.

The workflow does not consume a Candidate Cut, deployment manifest, prebuilt release image, Fresh Start plan, or persistent dogfood state. Smoke checks remain diagnostics rather than integrated behavior proof. Deterministic gates may use complete Gradle inputs/outputs; live and human evidence stays non-cacheable.

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

`dogfoodUp` is idempotent and may be run repeatedly. `dogfoodReset` deliberately deletes only the three Compose-managed dogfood session volumes and starts a clean development realm:

- PostgreSQL;
- native Files blobs;
- Mailpit messages.

The local CA and leaf certificates live outside Docker in the private operator-owned host TLS directory outside Docker. Reset must never remove or rotate them. Reviewed secrets and the reviewed environment file are also outside the reset boundary. Caddy runtime data, Keycloak container data, and optional provider state are not default persistent dogfood volumes.

Native Files, Calendar, and Chat are the default product providers. MinIO, Matrix, Nextcloud, ARC, Weaver, and other provider labs are explicit profiles and are not required for a normal `devUp` or `dogfoodUp`. The full isolated E2E profile may still enable the dependencies required by the journey it tests.

OpenTofu is not part of the active `dev` or `dogfood` lifecycle. Production infrastructure as code can be reconsidered when a real production target exists.

## Dogfood deployment

The `deploy-dogfood` job in `.github/workflows/live-stack-e2e.yml` runs only after all mandatory exact-SHA Compose/macOS/iOS qualification succeeds on a `dogfood` push. It verifies protected ancestry and runs `./gradlew dogfoodUp` on `weave-live-mac-mini`. Keeping E2E and deployment in one workflow avoids a second default-branch workflow-dispatch dependency.

There is no environment approval, destructive token, candidate-image resolver, or backup rehearsal in routine development dogfood.

## Prepare the physical iPhone

After all mandatory dogfood automated qualification is green, manually run `Prepare Human Test` with the exact dogfood SHA. It:

1. verifies all mandatory exact successful qualification and deployment evidence;
2. starts dogfood twice and checks unchanged TLS fingerprints;
3. optionally creates the first-owner invitation in private Mailpit;
4. builds the exact SHA with the stable development identity;
5. installs over `com.massimotter.weave` on the paired iPhone over WLAN and launches it.

The stable Apple team and bundle preserve Developer App trust and app storage. Deinstallation is not part of this workflow. The local CA should need full trust on the iPhone only once.

The invitation activation link remains only at `https://mail.weave.test:44443`. The human tester performs activation and reports the real outcomes for sign-in/session, navigation, Chat, Files, Calendar, Weaver/MCP when available, accessibility, revoke/regrant, and identity continuity. Human outcomes are `passed`, `failed`, or `not-available`; absent mandatory surfaces block promotion.

## Promotion discipline during development

Before merging `dev` to `dogfood`:

- normal PR CI is green;
- acceptance/scenario mappings and review are complete;
- the promotion tree matches the intended `dev` tree;
- no unrelated release-hardening work is mixed into the promotion.

Before making a future production release, write and accept the production-hardening ADR and add only the controls that the chosen deployment target actually needs.

For the current #1470 mainline delivery, promote the exact deployed and human-tested `dogfood` tree to `main` by PR. The protected Main Promotion Gate checks qualified Dogfood ancestry and proposed tree/spec equality, the exact dogfood-head Compose/macOS/iOS, deployment and physical preparation evidence, and the separately reported owner human result described in `docs/gitflow-pr-workflow.md`. A green disposable E2E alone is insufficient for this promotion. The merge itself does not publish a production release.

An unchanged candidate reuses qualification for Main. A changed candidate source/tree invalidates its evidence; newer Dev commits alone do not. No equivalent integration reruns solely because the candidate advances to Main.
