# Matrix Chat Sprint 15 dry-run policy

Sprint 15 turns the Sprint 14 Matrix Chat proof fixtures into backend-owned dry-run and Admin Console evidence. It does **not** enable Matrix Chat migration apply, production cutover, lossless migration, legal-compliance guarantees, or E2EE history migration.

## Scope and source of truth

- Matrix/Synapse remains the current real Chat provider path for dogfood v0.1.
- The Weave backend owns provider-switch dry-run evidence and support-safe audit refs.
- The Admin Console displays backend evidence only. It must not mint local migration evidence or bypass backend apply gates.
- Member-facing copy uses provider-neutral states only: `available`, `disabled_by_policy`, `not_configured`, `degraded`, `unavailable`, `coming_later`, and `unsupported`.

## Power-level and permission-impact policy

Matrix power levels are not a lossless Weave RBAC contract.

| Source data | Sprint 15 classification | Admin consequence | Apply rule |
| --- | --- | --- | --- |
| Room membership and normal roles that map to known Weave roles | `portable` with review | Preserve as canonical conversation membership where identity mapping exists. | Still dry-run only in Sprint 15. |
| Non-default power-level thresholds, event-specific permissions, or room-admin overrides | `manual_review` / `lossy` | Admin sees permission-impact copy and count, never raw Matrix internals. | Blocks apply until explicit future policy accepts the mapping. |
| Unknown custom power-level events or contradictory room policy | `unsupported` | Admin sees blocker and next action. | Blocks apply. |

Member copy must describe capability impact, not Matrix power-level details. For example: "Some chat permissions need admin review before a future migration." Do not expose provider IDs, homeserver names, raw events, or room internals.

## Media copy, reference retention, and rollback policy

Matrix media evidence is support-safe and classified before any future apply path:

| Media condition | Classification | Retention decision |
| --- | --- | --- |
| Media copied into Weave-controlled storage with content hash and archive ref | `portable` after future evidence | Requires checksum evidence, malware/size policy, and restore smoke proof. |
| Media retained as source archive with support-safe reference | `archive_only` | Current Sprint 15 default for unresolved media durability. |
| Media referenced through provider-owned retention only | `manual_review` | Admin must accept retention caveat; apply remains blocked. |
| Missing, expired, or credential-bearing media URL | `unsupported` | Block apply and redact the raw URL, including any `mxc://` value. |

Rollback requires retained source export/archive refs and restore-smoke evidence. Sprint 15 rollback copy must state that rollback cannot recreate unsupported encrypted history or exact Matrix power-level parity.

## E2EE client-export strategy boundary

Server-side migration cannot decrypt encrypted Matrix history. Sprint 15 classifies encrypted-room history as `unsupported` or `coming_later` and keeps apply/cutover blocked.

A future client-side export strategy must define, test, and document:

- explicit user/admin consent for decrypted export;
- device trust and cross-signing requirements;
- key backup/recovery and lost-device behavior;
- redaction of keys, access tokens, device IDs, homeserver URLs, `mxc://` values, and raw provider errors;
- accessible verification/recovery UX for keyboard and screen-reader users;
- support-safe evidence refs proving exported plaintext was handled under approved retention and deletion policy.

Until that exists, release and customer wording may say "dry-run/consequence evidence" only.

## Operator dry-run runbook

1. Confirm a backend admin/operator session with `admin.provider.configure` and support-safe audit sink readiness.
2. Provide only SecretRefs and counts/classes to the dry-run path. Never paste access tokens, homeserver URLs, raw Matrix room IDs, `mxc://` values, or downstream error bodies into requests, issues, PRs, or support bundles.
3. Run the backend dry-run/preflight path for Chat provider replacement and capture only returned `dryRunId`, audit refs, object counts, hashes/refs, lossy/unsupported/manual-review counts, rollback/archive refs, and next action.
4. Review Admin Console consequence preview: current binding, target adapter, readiness, blockers, member-impact copy, rollback limits, and audit refs.
5. Keep apply/cutover blocked when evidence is missing, stale, unsafe, manually unresolved, includes encrypted history blockers, unresolved power-level/media policy, or lacks a separate post-Sprint-15 feature gate.
6. Store support-safe evidence with release artifacts and link it from the Sprint closure report.

## Accessibility and AT evidence template

Before customer-facing promotion, execute or explicitly waive this manual AT pass:

- Keyboard traversal reaches provider category, target adapter, dry-run trigger, consequence preview, rollback limits, audit refs, and disabled apply control.
- Screen reader announces blocked/enabled state as text, including `coming_later` / `unsupported`; no meaning is color-only.
- Admin consequence preview and member-impact copy are distinguishable.
- Copy contains no raw provider diagnostics, tokens, endpoints, `mxc://`, or homeserver details.
- Evidence link: Sprint 15 may use this template as pending manual evidence; future promotion requires executed AT notes.
