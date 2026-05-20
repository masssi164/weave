# Matrix E2EE posture and recovery gate

Matrix E2EE is active Weave scope, but the local/dev stack must not claim end-to-end encryption until the encrypted-room, device-verification, and key-backup/recovery gates are implemented and validated.

## Current operator posture

- `WEAVE_CHAT_E2EE=active-architecture-gated` documents that the architecture must remain compatible with Matrix E2EE.
- Backend platform config must continue to expose `features.chatE2ee=false` until encrypted rooms and recovery UX are validated.
- The default workspace space plus `announcements`, `general`, and `help` rooms are intentionally unencrypted in the current operator baseline.
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

Before changing `features.chatE2ee` or enabling encrypted default rooms, land validation for:

1. encrypted-room creation/join for selected default workspace/team/channel rooms;
2. device verification and multi-device behavior;
3. key backup/recovery for real user/device flows, lost-device handling, and documented recovery limits;
4. accessibility-reviewed verification/recovery flows;
5. admin docs explaining what is E2EE, what metadata remains visible, and what is not recoverable;
6. bot/assistant/connector participation policy with consent, audit, Matrix device trust, and client identity behavior.

Bot, assistant, and connector participation in encrypted rooms remains fail-closed until those gates are complete.
