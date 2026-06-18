# Sprint 32 Beta path evidence (#835)

Status: deterministic offline evidence for the Sprint 32 Admin + User + Weaver Beta path. This file does not claim production release, unrestricted Weaver availability, or completed credentialed live-stack proof.

## Coherent story

1. Admin prepares and validates organization/provider/category readiness before member go-live. Readiness stays category-first for identity, chat, files, calendar, boards/tasks, meetings, admin health, and Weaver.
2. Admin enables Weaver only for an eligible user/group scope through policy. Weaver remains disabled by default, and approved capabilities are user-rights plus organization-whitelisted capabilities.
3. User opens the Client in workspace context and uses Weaver from that workspace rather than from raw provider setup.
4. Weaver follows the governed runtime/tool path: runtime profile from organization policy, tool registry boundary, runtime receipt for approval-required write-like actions, and support-safe audit refs.
5. Result language is canonical and support-safe: `available`, `disabled_by_policy`, `not_configured`, `degraded`, `unavailable`, or `coming_later`; provider-specific detail appears only as sanitized support references.
6. Adapter-continuity dry-run proves preserved/lossy/blocked/rollback/member-impact accounting and does not make an unqualified no-data-loss claim.
7. Audit/evidence is inspectable without secrets or raw provider payloads.

## Evidence map

| Acceptance slice | Marker | Evidence |
| --- | --- | --- |
| Admin prepares/validates readiness | `BETA_ADMIN_READINESS_RESULT` | `release/provider-lab/weaver-runtime/sprint-32-beta-path-evidence.fixture.json` story step `admin_readiness` |
| Admin enables Weaver for eligible scope | `BETA_WEAVER_ENABLEMENT_RESULT` | same fixture story step `admin_weaver_enablement` |
| User uses Weaver in workspace context | `BETA_MEMBER_WORKSPACE_RESULT` | same fixture story step `member_workspace_weaver_use`; supporting client flow from PR #841 |
| Governed Weaver runtime/tool path | `BETA_GOVERNED_RUNTIME_RESULT` | same fixture story step `governed_runtime_tool_path`; `docs/evidence/weaver-approval-runtime-boundary-issue-833.md`; `release/provider-lab/weaver-runtime/sprint-32-weaver-mcp-tool-execution.fixture.json` |
| Adapter-continuity dry-run | `BETA_ADAPTER_CONTINUITY_RESULT` | same fixture story step `adapter_continuity_dry_run`; `docs/evidence/sprint-32-adapter-continuity-dry-run.md` |
| Support-safe audit/evidence | `BETA_AUDIT_EVIDENCE_RESULT` | same fixture story step `audit_evidence_inspection` |

## Live-stack gate

The PR-safe artifact marks live runtime execution as `blocked_environment_unavailable` because no credentialed live stack was available in this implementation session. Local DNS is not treated as the blocker. Before RC promotion, the same scenario must be rerun against the intended runtime/container topology and attached as sanitized live-stack evidence.

## Support-safety boundary

No secrets, raw provider payloads, raw Weaver prompts, endpoint URLs, room IDs, event IDs, usernames, or member content are recorded. Evidence uses support-safe references and deterministic fixture assertions only.

## Accessibility smoke

The deterministic smoke covers critical state copy and keyboard/screen-reader-friendly state boundaries for:

- Admin readiness and Weaver enablement states.
- Member workspace Weaver helper states.

This is supporting smoke evidence only; it is not a replacement for release-owner manual assistive-technology signoff where that gate applies.

## Local verification

```sh
python3 e2e/tests/sprint32_beta_path_check.py
./gradlew acceptanceContract
```
