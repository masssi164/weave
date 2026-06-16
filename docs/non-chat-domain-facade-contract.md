# Non-chat domain facade contract

Sprint 32 issue #788 defines the shared implementation contract for provider-neutral Files, Calendar, and Boards facades. Chat remains the strongest existing example, but non-chat domains must not be forced into one leaky mega-abstraction.

## Required boundary

Each canonical non-chat domain facade must:

1. Evaluate workspace capability and policy before any provider access.
2. Enforce Space/context authorization at the product boundary.
3. Emit audited write/delete decisions with actor, context, domain, operation, result, and support-safe diagnostics.
4. Return canonical object IDs and provenance/mapping references instead of provider-native IDs as the public product contract.
5. Use support-safe failure states such as `not_configured`, `degraded`, `policy_blocked`, `unavailable`, and `provider_failure` without exposing secrets, raw provider payloads, or internal endpoints.
6. Keep provider adapters behind server/domain seams; product callers and Weaver tools consume canonical domain APIs only.
7. Provide dry-run or preview hooks for replacement/mapping work before any destructive apply/cutover path exists.

## Domain-specific semantics stay explicit

The shared facade is a contract, not a generic data model. Files, Calendar, and Boards keep their own nouns, invariants, and tests:

- Files: drives, folders, files, versions, checksums, share/link policy, document sessions, and attachment refs.
- Calendar: calendars, events, recurrence, attendees, resources, reminders, meeting join grants, artifacts, and consent/retention refs.
- Boards: boards, lists, tasks, statuses, assignees, dependencies, labels, estimates, workflow rules, and decision/file/chat refs.

## Child implementation order

1. #789 Files: prove audited write/delete and provenance/mapping for file/folder operations.
2. #790 Calendar: prove event/meeting authorization, audit, recurrence-safe failures, and mapping hooks.
3. #791 Boards: prove board/task status/write parity and lossy mapping/conflict reporting.

## Review and evidence gates

- Architecture reviews the boundary and rejects over-generic abstraction.
- Server/Domain reviews child domain semantics and adapter isolation.
- Security reviews audit fields and support-safe errors.
- QA/Evidence owns contract tests and release evidence wording.
- DevOps/Ops reviews support bundle and deploy/readiness implications.
- Docs and Marketing/Product Messaging review claim hygiene: release wording may claim facade/contract parity only, not production provider replacement, until live replacement evidence exists.

Small PRs are required. A child PR should either satisfy a complete vertical slice for one domain operation family or explicitly name the unsupported remainder in tests/docs.
