package com.massimotter.weave.backend.audit;

public enum AuditAction {
    CONNECTOR_WRITE_ATTEMPTED("connector.write.attempted"),
    ASSISTANT_WRITE_ATTEMPTED("assistant.write.attempted"),
    CHAT_MESSAGE_SENT("chat.message.sent"),
    CHAT_ENCRYPTION_ENABLED("chat.encryption.enabled"),
    CHAT_PROVIDER_REPLACEMENT_DRY_RUN("chat.provider_replacement.dry_run"),
    DECISION_LEDGER_RECORD_CREATED("decision_ledger.record.created"),
    MEETING_CAPSULE_CREATED("meeting_capsule.created"),
    WEAVER_SCOUT_SUMMARY_REQUESTED("weaver_scout.summary.requested"),
    BOARD_TASK_CREATED("board.task.created"),
    BOARD_TASK_MOVED("board.task.moved"),
    BOARD_TASK_COMPLETED("board.task.completed"),
    FILES_WEBDAV_WRITE_ATTEMPTED("files.webdav_write.attempted"),
    FILES_WEBDAV_WRITE_COMPLETED("files.webdav_write.completed"),
    FILES_WEBDAV_WRITE_BLOCKED("files.webdav_write.blocked"),
    FILES_DEVICE_CREDENTIAL_ISSUED("files.device_credential.issued"),
    FILES_DEVICE_CREDENTIAL_REVOKED("files.device_credential.revoked"),
    CONSENT_GRANTED("consent.granted"),
    CONSENT_REVOKED("consent.revoked"),
    EFFECTIVE_POLICY_SIMULATED("effective_policy.simulated"),
    IDENTITY_REALM_APPLY_GUARDED("identity.realm.apply.guarded"),
    ADMIN_POLICY_UPDATED("admin.policy.updated"),
    PROVIDER_READINESS_TESTED("provider.readiness.tested"),
    PROVIDER_REPLACEMENT_DRY_RUN("provider.replacement.dry_run"),
    CHAT_MIGRATION_PREFLIGHTED("chat.migration.preflighted"),
    WEAVER_RUNTIME_PROFILE_GENERATED("weaver.runtime_profile.generated"),
    WEAVER_RUNTIME_PROFILE_REVOKED("weaver.runtime_profile.revoked"),
    WEAVER_RUNTIME_PROFILE_ROLLED_BACK("weaver.runtime_profile.rolled_back"),
    WEAVER_RUNTIME_RECONCILED("weaver.runtime.reconciled"),
    WEAVER_RUNTIME_RELOAD_DECIDED("weaver.runtime.reload_decided"),
    WEAVER_MODEL_DECISION_RECORDED("weaver.model.decision_recorded"),
    WEAVER_CHANNEL_DECISION_RECORDED("weaver.channel.decision_recorded"),
    WEAVER_MCP_DECISION_RECORDED("weaver.mcp.decision_recorded"),
    WEAVER_TOOL_INVOCATION_RECORDED("weaver.tool.invocation_recorded"),
    TASK_CREATED("task.created"),
    TASK_MOVED("task.moved"),
    TASK_COMPLETED("task.completed"),
    TASK_STATUS_UPDATED("task.status_updated"),
    TASK_DECISION_LINKED("task.decision_linked");

    private final String wireName;

    AuditAction(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
