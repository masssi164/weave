package com.massimotter.weave.backend.audit;

public enum AuditAction {
    CONNECTOR_WRITE_ATTEMPTED("connector.write.attempted"),
    ASSISTANT_WRITE_ATTEMPTED("assistant.write.attempted"),
    CHAT_MESSAGE_SENT("chat.message.sent"),
    CHAT_PROVIDER_REPLACEMENT_DRY_RUN("chat.provider_replacement.dry_run"),
    BOARD_TASK_CREATED("board.task.created"),
    BOARD_TASK_MOVED("board.task.moved"),
    BOARD_TASK_COMPLETED("board.task.completed"),
    CONSENT_GRANTED("consent.granted"),
    CONSENT_REVOKED("consent.revoked"),
    WEAVER_RUNTIME_PROFILE_GENERATED("weaver.runtime_profile.generated"),
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
