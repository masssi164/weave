# MCP/domain-tool action registry

Tool actions own operation semantics. Domains and adapters do not read, write, send, delete, migrate, switch providers, or approve by themselves.

| Domain | Tool action | Action kind | Risk | ApprovalReceipt requirement | Audit/evidence | Support-safe payload | Adapter binding |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Identity/admin | identity.lookupActor | read | low | not required by default policy | actor lookup audit ref | actor id, org id, result state | keycloak-oidc |
| Identity/admin | identity.changeRole | write | high | required unless pre-approved admin policy covers exact scope | role-change evidence id | actor id, target id, role diff summary | keycloak-oidc |
| Chat | chat.listThreads | read | low | not required by default policy | query audit ref | conversation ids and state only | matrix-synapse or selected chat adapter |
| Chat | chat.sendMessage | external_send/write | high | required for Weaver-initiated sends unless explicit governed policy covers exact channel | message action evidence id | redacted body summary, refs, recipients | matrix-synapse or selected chat adapter |
| Chat | chat.switchProvider | provider_switch/migration | high | required | switch plan, dry-run/apply evidence | provider refs, lossy-field summary | matrix-synapse, zulip-candidate |
| Files/documents | files.readMetadata | read | low | not required by default policy | file metadata audit ref | file id, owner, classification, not raw content | nextcloud-files |
| Files/documents | files.proposeUpdate | write | high | required for commit-write; draft-only may be policy-scoped | diff evidence id | file ref and diff summary | nextcloud-files |
| Files/documents | files.delete | delete | high | required | deletion/tombstone evidence | file refs and rollback hint | nextcloud-files |
| Calendar | calendar.listEvents | read | medium | policy-scoped; required for sensitive calendars | calendar query audit ref | event ids/time windows/redacted summaries | radicale-caldav |
| Calendar | calendar.createEvent | write/external_send | high | required unless explicit scheduling policy covers exact scope | event-create evidence id | invitee refs, time, redacted subject | radicale-caldav |
| Contacts/people | people.lookup | read | medium | policy-scoped | lookup audit ref | contact ids and minimal attributes | radicale-carddav |
| Tasks/boards | tasks.createOrUpdate | write | medium | required for Weaver-initiated changes unless project policy covers exact board | task diff evidence id | board/task refs and diff summary | openproject-candidate |
| Calls/meetings | meetings.createRoom | write/external_send | high | required when inviting participants or changing external access | meeting-room evidence id | room ref, participants, expiry | livekit-candidate |
| Search/index | search.query | read | medium | policy-scoped by domain and actor | search audit ref | query class, result refs, no raw hidden payloads | internal-derived-index |
| Audit/evidence | evidence.record | write | medium | governed service policy; human approval only for sensitive export | evidence id and checksum | redacted evidence metadata | weave-audit-log |
| Admin/control-room | admin.previewPolicy | read | medium | not required for preview; sensitive scopes need admin policy | preview audit ref | policy ids and predicted states | weave-admin-console |
| Admin/control-room | admin.applyPolicy | write | high | required | policy-apply evidence id | policy diff, scope, expiry | weave-admin-console |
| Admin/control-room | admin.exportSupportBundle | external_send/read | high | required for external sharing | bundle manifest and redaction evidence | manifest, hashes, no secrets/raw payloads | weave-admin-console |
| Weaver runtime | weaver.startRun | write | high | required when runtime can trigger tool actions; otherwise disabled by policy | run evidence id | org/user/channel/run refs, no prompts/secrets | openclaw-runtime-candidate |
| Weaver runtime | weaver.invokeTool | delegated_action | inherits target action | inherits target action exactly | target action evidence id | target action support-safe payload | target tool adapter binding |
