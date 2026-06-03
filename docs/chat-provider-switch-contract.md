# Chat provider switch contract

Status: Sprint 23 contract evidence for issue #629.

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

## Evidence gate

Run the Sprint 23 contract gate from the repository root:

```bash
./gradlew chatProviderSwitchCheck --console=plain
```

Expected output:

```text
chat-provider-switch-check: ok objects=12 positive=accept negative=reject providerRefs=support_safe
```

For release evidence, `chatProviderSwitchCheck` is also included in `releaseEvidenceCheck`.
