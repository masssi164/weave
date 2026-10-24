# Implementation brief: effective organization policy readback

Status: partial implementation removes false policy-save success and exposes current role grants read-only; durable policy editing remains open in #1716.
Issue: [#1716](https://github.com/masssi164/weave/issues/1716).

## Source and purpose

Implementation baseline: `e5d4f9736bc20d24ce3e80393be199c54e48b2c7`.
Corpus pin: `7129075b7bf3f9f3763ec71a907395b007604c6f`.
Target lane: `dev`.

The ordinary organization setup must produce an effective state that the member client can use.
At the inspected baseline, the Admin whitelist PATCH published an audit event and returned a fixed
map without changing the policy read by ordinary User operations. The UI nevertheless announced
a successful save and flattened different profiles into one write payload.

This brief records implementation evidence and proposed work. It does not redefine product
policy or claim that organization/domain setup is complete.

## Implemented immediate correction

The draft preserves Admin authorization, request validation and last-admin protection, then rejects
a valid whitelist edit with `409 capability-whitelist-read-only`. It records no policy-update event
and clearly states that no policy changed. The generated Admin contract documents that rejection.
The Admin UI displays only the current actor's role/entitlement grants, distinguishes unavailable
from empty readback, and explains that domain availability and Space/resource rights are separate.
It provides no unsupported policy editor or Save action. Other administration workflows remain
available under their existing authority.

This is an honesty and conformance repair, not durable organization policy editing. Issue #1716
remains open for the accepted writable setting, persistence, concurrency and enforcement work below.

## Governing boundary

Use the pinned `platform/identity-security/spec.md`, `domains/admin-health-ops/spec.md`,
`domains/spaces/spec.md` and October consolidation profile. Weave Server owns current
organization/domain/resource authorization; both the member client and Admin Console use
Weave-owned interfaces. Provider endpoints and credentials stay behind the server.

Organization membership, domain availability, role/profile permissions and durable Space
membership are distinct authorities. An organization Space is not a policy profile. The current
release has no durable parent/child Space membership inheritance. Domain configuration must not
silently enroll all members, expose private resources, replace roles or change provider bindings.

## Owning implementation

- `server/src/main/java/com/massimotter/weave/backend/service/AdminControlPlaneService.java`:
  `whitelist`, `updateWhitelist` and `enforceLastAdminGuard`.
- `server/src/main/java/com/massimotter/weave/backend/service/WorkspaceCapabilityService.java`:
  effective policy, member projection and operation admission.
- `admin-console/src/api.ts`: `normalizeWhitelist` and the read-only role-capability projection.
- `admin-console/src/App.tsx`: the accessible read-only capability list.
- Existing generated Admin transport models and the current identity/persistence boundaries.

## Remaining durable-editing implementation order

1. Identify the exact writable setting and authority: organization domain availability and a
   named role/profile are not interchangeable. Record the accepted mapping explicitly; if that
   requires new product meaning, open a focused corpus change before implementing it.
2. Preserve the implemented truthful rejection until supported mutation is real. An audit record
   or request echo cannot establish a policy change.
3. Save the supported named, organization-scoped setting durably using the existing architecture,
   with explicit concurrency and last-admin recovery semantics.
4. Feed the same committed setting into both member capability readback and operation
   authorization. Keep provider readiness and resource/Space grants independently enforced.
5. Have the Admin UI edit only that target and confirm authoritative readback before success.

Do not add a general policy engine, implicit grants, nested Spaces or another environment.

## Acceptance and proof

Immediate regression evidence covers unchanged readback/no mutation audit, authorized and denied
requests, exact error-only OpenAPI responses, actor-only role grants and localized read-only UI.

Use issue #1716's remaining checklist for durable editing. Required proof is Admin write -> authoritative readback
-> ordinary member allow/deny, plus restart, wrong-organization, stale-write, unknown-capability
and recovery boundaries. Assert that the previous fixed-map/no-op implementation fails the
regression. Reuse current server, Admin UI and generated-client test runners.

Exact-source verification and the existing single Mac mini qualification remain required.
Mocks, compilation, an audit event and a draft PR do not establish a usable deployed setting.
No live configuration, owner state, provider binding or credential recovery is authorized by
this brief.

## Related repairs

[#1715](https://github.com/masssi164/weave/issues/1715) independently enforces the existing
configured domain-enabled flags at User operation admission. It does not implement whitelist
persistence. [#1714](https://github.com/masssi164/weave/issues/1714) carries explicit Chat Space
selection. #1666 retains actual default-Space/member preparation. Keep these boundaries visible
instead of treating one green check as proof of the whole setup.

