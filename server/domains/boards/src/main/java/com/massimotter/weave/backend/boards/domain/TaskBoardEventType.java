package com.massimotter.weave.backend.boards.domain;

public enum TaskBoardEventType {
    TASK_CREATED("task.created"),
    TASK_UPDATED("task.updated"),
    TASK_COMPLETED("task.completed"),
    TASK_ARCHIVED("task.archived"),
    TASK_MOVED("task.moved"),
    ASSIGNMENT_CHANGED("assignment.changed"),
    LABEL_CHANGED("label.changed"),
    PRIORITY_CHANGED("priority.changed"),
    DUE_DATE_CHANGED("due_date.changed"),
    COMMENT_ADDED("comment.added"),
    ATTACHMENT_CHANGED("attachment.changed"),
    SYNC_CONFLICT_DETECTED("sync.conflict_detected");

    private final String contractName;

    TaskBoardEventType(String contractName) {
        this.contractName = contractName;
    }

    public String contractName() {
        return contractName;
    }
}
