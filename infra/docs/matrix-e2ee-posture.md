# Matrix E2EE posture and recovery gate

Matrix E2EE is active Weave scope, but the local/dev stack must not claim end-to-end encryption until the encrypted-room, device-verification, and key-backup/recovery gates are implemented and validated.

## Current operator posture

- `WEAVE_CHAT_E2EE=active-architecture-gated` documents that the architecture must remain compatible with Matrix E2EE.
- Backend platform status must continue to expose `matrix.e2eeEnabled=false` until encrypted rooms and recovery UX are validated.
- The default workspace space plus `announcements`, `general`, and `help` rooms are intentionally unencrypted in the current single-host operator path.
- Matrix federation remains disabled by default for MVP.

`operator-check.sh` and `smoke-test.sh` now verify this honest posture by querying default room state with the private Matrix provisioner token:

- `m.room.encryption` must be absent from the default workspace rooms while `chatE2ee=false`.
- the provisioner-account `/room_keys/version` response is collected as a diagnostic only; a personal/provisioner key backup does not prove global Weave E2EE recovery readiness.
- room-encryption failures are reported as gate mismatches, not as secrets or raw token output.

## Server-readable boundary

When encrypted rooms are later promoted, Matrix message bodies are not backend/support-readable. Support diagnostics may use only metadata that is safe for operators to see, such as:

- homeserver reachability and client API versions;
- public discovery and MAS/OIDC metadata;
- stable aliases and room IDs for default rooms;
- membership/provisioning status and non-secret power-level state;
- room encryption on/off status;
- timestamps, HTTP status codes, and redacted service logs.

Support bundles are not backups and must not include Matrix access tokens, recovery keys, private keys, cookies, or generated bootstrap secrets.

## Promotion requirements

Before changing `matrix.e2eeEnabled` or enabling encrypted default rooms, land validation for:

1. encrypted-room creation/join for selected default workspace/team/channel rooms;
2. device verification and multi-device behavior;
3. key backup/recovery for real user/device flows, lost-device handling, and documented recovery limits;
4. accessibility-reviewed verification/recovery flows;
5. admin docs explaining what is E2EE, what metadata remains visible, and what is not recoverable;
6. bot/assistant/connector participation policy with consent, audit, Matrix device trust, and client identity behavior.

Bot, assistant, and connector participation in encrypted rooms remains fail-closed until those gates are complete.

## Sprint 12 Chat portability and E2EE product boundaries

Weave controls Chat product copy, admin readiness, support-safe diagnostics, and provider adapter contracts. Matrix device trust, room keys, recovery keys, E2EE ciphertext, and provider legal-hold behavior remain provider/device/key dependent.

### Export and migration states

Chat export/import evidence uses stable product states only:

- `metadata_only`: Weave can account for rooms, membership, timestamps, and attachment refs, but not plaintext history.
- `archive_import_only`: content is preserved as an archive artifact and is not replayed into the target provider.
- `blocked_e2ee_keys_missing`: apply is blocked because required keys or recovery evidence are unavailable.
- `legal_hold_provider_only`: legal hold depends on provider/legal architecture and is not a Weave backend claim.

Weave must not claim backend encrypted-room search, plaintext export, assistant access to encrypted rooms, or legal hold unless a future key/legal architecture explicitly provides it.

### Support diagnostics redaction

Support bundles and release evidence redact tokens, cookies, access/refresh material, recovery keys, room keys, private keys, plaintext message bodies, attachments, raw homeserver bodies, and credential-bearing URLs. Diagnostics may include support-safe counts, state names, hashes, audit refs, and readiness IDs.

### Accessibility protocol

Device verification, key backup/recovery, lost-device recovery, cannot-decrypt states, and blocked export states must be text-first, keyboard reachable, screenreader announced, and free of raw provider internals.
