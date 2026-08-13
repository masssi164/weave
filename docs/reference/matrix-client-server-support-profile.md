# Matrix Client-Server support profile

This profile describes the permanent northbound Matrix facade. It does not describe a southbound Matrix provider and does not make Synapse or MAS canonical Chat authority.

## Protocol boundary

Server Matrix wire parsing/projection is owned by `rust/matrix-protocol` through the closed JNI operation set. Canonical Chat authorization, persistence, provider selection, idempotency and sync remain Java/Weave responsibilities. Client cryptography is owned only by `rust/matrix-client`.

## Endpoint profile

| Surface | Status | Persistence / rule |
| --- | --- | --- |
| `/versions` | Supported | Ruma/JNI projection. |
| `/account/whoami` | Supported | OIDC-authenticated identity/device projection. |
| `/sync` | Guarded until final concurrency evidence | Composite canonical Chat + Matrix routing high-waters; token must not expose provider cursors. |
| joined rooms / members / state | Supported profile | Projected from canonical Chat. |
| room send with txnId | Guarded until exact idempotency evidence | Key must bind tenant, user, authenticated device/client instance, method, normalized endpoint, txnId and provider-binding revision where applicable. |
| redaction | Supported profile | Canonical mutation; no provider payload leakage. |
| read receipt | Supported profile | Durable receipt state. |
| typing | Supported ephemeral profile | May be lost across restart; never described as durable. |
| account data | Supported | Per tenant/user/type relational state. |
| keys upload/query/claim | Supported server routing profile | Public key metadata only; one-time-key claim must be atomic. |
| device signing / signatures | Supported server routing profile | No private client key material. |
| sendToDevice | Guarded until multi-instance ordering evidence | Per-device durable queue ordered by logical Matrix revision. |
| room-key backup | Supported opaque server storage profile | Ciphertext/public auth metadata only. |
| device revocation | Supported profile | OIDC/device binding must fail closed after revocation. |

## Encryption invariant

The server never owns user private identity keys, Olm/Megolm private session state or plaintext derived by decrypting encrypted room events. Encrypted room payloads and to-device envelopes are opaque server data except for protocol metadata required for routing and validation.

## Provider invariant

The Matrix facade is permanent. Selecting `weave-native`, Synapse-backed or another future `ChatProviderPort` implementation changes only the southbound provider. It does not change the member Matrix URL or turn the Matrix facade on/off.

## Closure marker

Rows marked Guarded may be promoted only by committed PostgreSQL/concurrency/idempotency evidence. Final closure must remove this marker text for capabilities actually qualified and must not hide unresolved behavior behind compatibility code.
