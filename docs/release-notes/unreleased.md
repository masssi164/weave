# Unreleased

Use this page for release-affecting changes that have merged but are not included in a tagged release yet.

## Added

- Context-driven workflow primitives now have a provider-neutral, linear-first preview contract with explicit context references, blocker/evidence metadata, sample workflows, and dry-run-only governed agent participation.
- Contextual meetings now have a fail-closed architecture contract preserving LiveKit as the active meetings provider contract while documenting encryption boundaries, consent defaults, and accessible join requirements before media controls are enabled.
- Sprint 8/Sprint 9 acceptance now includes mapped product-readiness waterfall evidence for domain registry review, Keycloak dry-run, provider apply blocking, portability reports, Calls/LiveKit readiness, Weaver approvals, member opt-in, and support-safe release blockers.

## Changed

- Provider registry and release evidence now distinguish `contract_only`, `configured_readiness`, `live_adapter_read`, `live_adapter_write`, `migration_apply_ready`, and `release_ready` providers so contract-only seams cannot appear generally available to members.

## Fixed

- Nothing yet.

## Security

- Product-readiness evidence now records provider-switching, OpenClaw runtime isolation, Weaver tool approval, RBAC, redaction, scan, and support-bundle expectations as explicit release blockers.

## Accessibility

- Sprint 9 release readiness now treats admin setup, provider switching/report review, Calls/LiveKit states, Weaver approvals, and member capability states as release-blocking accessibility flows.

## Migration/Operator Notes

- Nothing yet.

## Known Issues

- Nothing yet.
