# Dev → Dogfood development loop

Status: active development policy.

Weave is still in active development with one human developer. `dev` and `dogfood` therefore optimize for short, repeatable feedback rather than production rollout ceremony.

## Branches

- `dev` is the normal integration branch. Feature branches return here through ordinary PR CI.
- `dogfood` is the current LAN test branch. A normal `dev` → `dogfood` PR runs the same complete isolated E2E flow, and a successful push starts the reviewed local Compose stack.
- `main` and production release automation are outside this development loop. A future production-hardening ADR must define backup, migration, immutable release-image, approval, and rollback controls before those controls become active again.

## One automated gate

`Full Compose E2E` is the only automated prerequisite for human testing. It runs on PRs and pushes for both `dev` and `dogfood` and executes the authoritative isolated `./gradlew testApp` product flow. That flow proves availability and integrated behavior, then removes its run-owned resources.

The workflow does not consume a Candidate Cut, deployment manifest, prebuilt release image, Fresh Start plan, or persistent dogfood state. Smoke checks remain useful diagnostics but are not substitutes for this E2E result.

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

The local CA and leaf certificates live outside Docker at `/Users/flotterotter/.weave/dogfood/generated/tls`. Reset must never remove or rotate them. Reviewed secrets and the reviewed environment file are also outside the reset boundary. Caddy runtime data, Keycloak container data, and optional provider state are not default persistent dogfood volumes.

Native Files, Calendar, and Chat are the default product providers. MinIO, Matrix, Nextcloud, ARC, Weaver, and other provider labs are explicit profiles and are not required for a normal `devUp` or `dogfoodUp`. The full isolated E2E profile may still enable the dependencies required by the journey it tests.

OpenTofu is not part of the active `dev` or `dogfood` lifecycle. Production infrastructure as code can be reconsidered when a real production target exists.

## Dogfood deployment

The `deploy-dogfood` job in `.github/workflows/live-stack-e2e.yml` runs only after its exact-SHA `Full Compose E2E` job succeeds on a `dogfood` push. It verifies protected ancestry and runs `./gradlew dogfoodUp` on `weave-live-mac-mini`. Keeping E2E and deployment in one workflow avoids a second default-branch workflow-dispatch dependency.

There is no environment approval, destructive token, candidate-image resolver, or backup rehearsal in routine development dogfood.

## Prepare the physical iPhone

After dogfood E2E is green, manually run `Prepare Human Test` with the exact dogfood SHA. It:

1. verifies the exact successful E2E run;
2. starts dogfood twice and checks unchanged TLS fingerprints;
3. optionally creates the first-owner invitation in private Mailpit;
4. builds the exact SHA with the stable development identity;
5. installs over `com.massimotter.weave` on the paired iPhone over WLAN and launches it.

The stable Apple team and bundle preserve Developer App trust and app storage. Deinstallation is not part of this workflow. The local CA should need full trust on the iPhone only once.

The invitation activation link remains only at `https://mail.weave.test:44443`. The human tester performs activation and reports the real outcomes for sign-in/session, navigation, Chat, Files, Calendar, Weaver/MCP when available, accessibility, revoke/regrant, and identity continuity. A missing surface is `blocked`, not passed.

## Promotion discipline during development

Before merging `dev` to `dogfood`:

- normal PR CI is green;
- `Full Compose E2E` is green on the latest PR head;
- the promotion tree matches the intended `dev` tree;
- no unrelated release-hardening work is mixed into the promotion.

Before making a future production release, write and accept the production-hardening ADR and add only the controls that the chosen deployment target actually needs.
