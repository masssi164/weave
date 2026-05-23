# Audit/Consent internal seam

Status: internal backend seam; no public connector writes; no public connector SDK.

## Why this exists

The current implementation sequence is:

1. Context Graph contract seam.
2. Context ReBAC/AuthZ seam.
3. Connector/OpenProject read-sync seam.
4. Audit/Consent seam.

This seam is the minimum backend-owned safety layer needed before any future connector or assistant write can be promoted. It does **not** enable provider writes, public connector packages, team-room agent actions, or a Consent Center UI.

## Audit event envelope

`AuditEvent` is append-only and tenant-scoped. Required fields:

- `tenant_id`
- `context_id?`
- `actor_ref`
- `source_ref`
- `action`
- `occurred_at`
- `idempotency_key`
- `redaction_level`
- `payload`

Supported internal actions now:

- `connector.write.attempted`
- `assistant.write.attempted`
- `consent.granted`
- `consent.revoked`

`context_id` must already be a Weave Context ID. Provider bindings such as `provider_binding:openproject:...` must be resolved before audit emission, matching the ReBAC rule that raw provider references cannot bypass tenant/context authorization.

## Redaction rule

Audit payloads are support-safe by construction. The event constructor redacts keys or values that look like:

- bearer tokens or API tokens;
- passwords, cookies, secrets, and Authorization headers;
- raw provider errors or stack traces.

Provider errors must be mapped to support-safe codes before they are useful in product diagnostics. Raw provider response bodies must not become audit payloads.

## Connector/assistant write envelope

`ConnectorWriteAuditEnvelope` defines the shape future connector/assistant write paths must emit. The generated event explicitly carries `write_enabled=false` so this seam cannot be mistaken for live provider write promotion.

If a write path requires audit and no `AuditEventPublisher` is configured, `AuditWriteGate.publishRequired` fails closed with `AuditRequiredException`.

## Consent events

`ConsentAuditEvents.granted` and `ConsentAuditEvents.revoked` represent grant/revocation events for future Connector Consent work. They are intentionally event-only here; no Consent Center UI, storage policy, or delegated credential runtime is introduced by this slice.

## Contract files and tests

- `src/main/resources/contracts/audit-consent.schema.json`
- `src/main/java/com/massimotter/weave/backend/audit/*`
- `src/test/java/com/massimotter/weave/backend/audit/*`

Run:

```bash
./gradlew test --tests 'com.massimotter.weave.backend.audit.*'
```
