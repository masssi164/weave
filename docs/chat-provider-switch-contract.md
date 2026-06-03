# Chat provider switch contract

Status: Sprint 23 contract evidence for issues #627, #628, #629, and #630.

This page defines the CI-safe contract base for the Sprint 23 Chat Provider Switch. It starts from the merged Sprint 22 provider lab and its green entry scoreboard:

- `release/provider-lab/sprint-23-entry-scoreboard.json`
- `release/provider-lab/manifests/matrix-synapse.json`
- `release/provider-lab/manifests/zulip.json`
- `fixtures/provider-lab/chat-fixture.json`
- `./gradlew providerLabCheck`

## Claim boundary

Sprint 23 may claim only named, evidence-backed Matrix/Synapse and Zulip chat-switch behavior. Issue #629 does not prove lossless migration, production apply, production rollback, provider interchangeability, E2EE history migration, customer-ready status, or release readiness.

## Canonical object coverage

`release/provider-lab/chat-switch/canonical-object-coverage.json` records the required Chat canonical objects:

- `WeaveSpace`
- `WeaveConversation`
- `WeaveMessage`
- `WeaveThread`
- `WeaveReaction`
- `WeaveAttachment`
- `WeaveMembership`
- `WeaveHistoryPolicy`
- `ProviderRef`
- `MigrationReceipt`
- `RollbackReceipt`
- `LossyFieldReport`

The coverage artifact links each object to repo/spec evidence and names Matrix/Synapse and Zulip fixture fields that later Sprint 23 migration evidence must account for.

## Lossy-field enforcement

`release/provider-lab/chat-switch/matrix-zulip-lossy-field-report.fixture.json` is the positive fixture: every changed or dropped field has a matching `LossyFieldReport` entry.

`release/provider-lab/chat-switch/matrix-zulip-silent-drop-negative.fixture.json` is the negative fixture: it intentionally drops reaction metadata without a matching `LossyFieldReport` entry and must be rejected.

This keeps the Sprint 23 portability promise at no-unaccounted data loss. It does not promise no data loss.

## ProviderRef redaction

Provider references are support-safe diagnostics only. They may include:

- `providerId`
- `providerObjectType`
- `opaqueId`
- `contentHash`
- `historyStatus`
- `redacted`

They must not include raw provider URLs, homeserver URLs, API URLs, access tokens, refresh tokens, passwords, secrets, raw payloads, message bodies, or attachment contents.

## Matrix/Synapse to Zulip fixture proof

`release/provider-lab/chat-switch/matrix-zulip-migration-proof.fixture.json` records the CI-safe Sprint 23 provider-lab evidence for issue #627:

- Matrix/Synapse starts active and Zulip becomes active after the fixture dry-run/apply path.
- Weave Chat domain IDs stay stable across the switch.
- `MigrationReceipt`, `LossyFieldReport`, audit refs, attachment validation, and UI validation transcript are linked.
- History statuses remain explicit: `preserved`, `partially_preserved`, `archive_only`, `unsupported`, `unexportable`, `conflict`, and `metadata_only`.

This is fixture evidence. It is not production mutation or a lossless/full-history claim.

## Zulip to Matrix/Synapse rollback honesty

`release/provider-lab/chat-switch/zulip-matrix-rollback-proof.fixture.json` records the CI-safe Sprint 23 rollback-honesty evidence for issue #628:

- Zulip starts active and Matrix/Synapse becomes active again after rollback.
- Weave Chat navigation and domain IDs remain stable.
- `RollbackReceipt`, conflict report, UI validation transcript, and audit refs are linked.
- New or conflicting target-side data is classified as `conflict`, `partially_preserved`, `metadata_only`, or `unsupported`; it is not hidden.

This keeps rollback wording honest and keeps production rollback blocked until production-class evidence exists.

## Claim gate and scoreboard

`release/provider-lab/chat-switch/sprint-23-claim-gate.fixture.json` records the Sprint 23 claim scoreboard for issue #630. It accepts only the scoped Matrix/Synapse to Zulip provider-lab fixture claim when `MigrationReceipt`, `LossyFieldReport`, `RollbackReceipt`, and UI validation evidence are present. It rejects generic provider-interchangeability, production rollback, full-history preservation, lossless migration, customer-ready, and release-ready wording.

The Sprint 23 chat reality level is capped at `migration_apply_ready`; `releaseReady` remains `blocked`.

## Evidence gate

Run the Sprint 23 contract gate from the repository root:

```bash
./gradlew chatProviderSwitchCheck --console=plain
```

Expected output:

```text
chat-provider-switch-check: ok objects=12 positive=accept negative=reject migration=matrix-to-zulip rollback=zulip-to-matrix claims=scoped providerRefs=support_safe
```

For release evidence, `chatProviderSwitchCheck` is also included in `releaseEvidenceCheck`.
