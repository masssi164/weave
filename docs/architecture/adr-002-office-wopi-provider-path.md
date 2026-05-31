# ADR-002: First Office/WOPI provider path

## Status

Accepted for Sprint 12 contract; implementation remains gated by spike evidence.

## Decision

Weave will pursue **Nextcloud-hosted WOPI with Collabora/CODE first** for the first Documents/Office provider path. ONLYOFFICE remains an evaluated alternative. Microsoft 365/Graph is a future adapter/non-goal for the v0.1 dogfood release line.

## Security model

- The backend owns document sessions and issues only short-lived grants/JWTs scoped to one document, actor, and permission set.
- WOPI callbacks must verify proof material, document identity, grant freshness, lock state, and permission sync before mutation.
- Lock, refresh-lock, unlock, and save flows are audit-linked and support-safe.
- Credential-bearing URLs, raw WOPI bearer material, cookies, provider payloads, and callback bodies are forbidden in member UI, logs, support bundles, PR evidence, and release notes.

## Product states

Documents expose stable states only: `available`, `not_configured`, `unavailable`, `manual_review_required`, and `guarded`. Current v0.1 member paths must keep editing unavailable unless live Collabora/CODE evidence proves readiness. Admin readiness may show operator remediation without exposing secrets.

## Operations and accessibility

The first path requires operator evidence for Collabora/CODE reachability, TLS, callback routing, proof verification, lock behavior, backup/restore ordering, and rollback. Accessibility evidence must cover document launch, unavailable state, keyboard focus return, screenreader status copy, and error recovery.

## Consequences

- Office is not claimed as ready before the spike proves live editing, lock correctness, support-safe diagnostics, and accessibility.
- The acceptance contract may verify honest document states now; editing scenarios stay future/guarded.
