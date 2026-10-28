package com.massimotter.weave.backend.boards.discovery;

import com.massimotter.weave.backend.admin.domain.SuiteDomainDeclaration;
import com.massimotter.weave.backend.admin.domain.SuiteDomainDefinition;
import com.massimotter.weave.backend.admin.domain.AdminCapabilityDeclaration;
import com.massimotter.weave.backend.admin.domain.AdminCapabilityDeclaration.ProfileGrant;
import java.util.List;

/** Existing boards capability vocabulary for shared Admin simulation and profile presentation. */
public final class BoardsAdminMetadata {
    private BoardsAdminMetadata() {}

    public static AdminCapabilityDeclaration capabilities() {
        return new AdminCapabilityDeclaration("boards", 40,
                List.of("boards.read", "boards.update_task"), List.of("boards.read", "boards.update_task"), List.of(), List.of("boards.read"),
                List.of(new ProfileGrant("workspace-admin", 40, List.of("boards.read", "boards.update_task")), new ProfileGrant("member-default", 40, List.of("boards.read"))));
    }
    public static SuiteDomainDeclaration suite() {
        return new SuiteDomainDeclaration("boards", 1, 0,
                new SuiteDomainDefinition(
                        "boards-tasks", "Boards and tasks", List.of("boards-tasks"), List.of("boards.read"), List.of("boards.update_task", "boards.sync_workspace"), List.of(
                        "board",
                        "list",
                        "task",
                        "status",
                        "assignee",
                        "watcher",
                        "comment",
                        "attachment_ref",
                        "dependency",
                        "label",
                        "custom_field",
                        "estimate",
                        "priority",
                        "milestone",
                        "sprint",
                        "workflow_rule",
                        "decision_link",
                        "space_ref",
                        "chat_ref",
                        "file_ref")),
                "local workspace writes; provider sync/write promotion gated by contract evidence", List.of(
                        "Provider-write apply is refused until read-sync, conflict, and rollback evidence pass",
                        "Keyboard create/move/complete flows are required before member promotion"), "Verify board/task workspace contract, accessible workflows, conflict states, and audit events.");
    }
}
