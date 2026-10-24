# Implementation brief: effective organization policy readback

Status: diagnosed conformance gap; writable-policy implementation is not delivered by this planning draft.
Issue: [#1716](https://github.com/masssi164/weave/issues/1716).

## Source and purpose

Implementation baseline: `e5d4f9736bc20d24ce3e80393be199c54e48b2c7`.
Corpus pin: `7129075b7bf3f9f3763ec71a907395b007604c6f`.
Target lane: `dev`.

The ordinary organization setup must produce an effective state that the member client can use.
The current Admin whitelist PATCH publishes an audit event and returns a fixed map; it does not
change the policy read by ordinary User operations. The UI nevertheless announces a successful
save and flattens different profiles into one write payload.

This brief records implementation evidence and proposed work. It does not redefine product
policy or claim that organization/domain setup is complete.

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
- `admin-console/src/api.ts`: `normalizeWhitelist` and `updateWhitelistPolicy`.
- `admin-console/src/App.tsx`: `savePolicy` and the editable whitelist field.
- Existing generated Admin transport models and the current identity/persistence boundaries.

## Bounded implementation order

1. Identify the exact writable setting and authority: organization domain availability and a
   named role/profile are not interchangeable. Record the accepted mapping explicitly; if that
   requires new product meaning, open a focused corpus change before implementing it.
2. Remove false success. Unsupported writes must return an explicit error or be represented as
   read-only; an audit record or request echo cannot establish a policy change.
3. Save the supported named, organization-scoped setting durably using the existing architecture,
   with explicit concurrency and last-admin recovery semantics.
4. Feed the same committed setting into both member capability readback and operation
   authorization. Keep provider readiness and resource/Space grants independently enforced.
5. Have the Admin UI edit only that target and confirm authoritative readback before success.

Do not add a general policy engine, implicit grants, nested Spaces or another environment.

## Acceptance and proof

Use issue #1716's checklist. Required behavioral proof is Admin write -> authoritative readback
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
