package com.massimotter.weave.backend.office.discovery;

import com.massimotter.weave.backend.admin.domain.SuiteDomainDeclaration;
import com.massimotter.weave.backend.admin.domain.SuiteDomainDefinition;
import com.massimotter.weave.backend.admin.domain.AdminCapabilityDeclaration;
import com.massimotter.weave.backend.admin.domain.AdminCapabilityDeclaration.ProfileGrant;
import java.util.List;

/** Existing office capability vocabulary for shared Admin simulation and profile presentation. */
public final class OfficeAdminMetadata {
    private OfficeAdminMetadata() {}

    public static AdminCapabilityDeclaration capabilities() {
        return new AdminCapabilityDeclaration("office", 60,
                List.of("documents.view", "documents.edit"), List.of("documents.view", "documents.edit"), List.of(), List.of("documents.view"),
                List.of());
    }
    public static SuiteDomainDeclaration suite() {
        return new SuiteDomainDeclaration("office", 0, 1,
                new SuiteDomainDefinition(
                        "files-docs", "Files and documents", List.of("documents-collaboration"), List.of("documents.view"), List.of("documents.edit", "documents.comment", "documents.collaborate"), List.of(
                        "document",
                        "editor_session",
                        "editor_provider",
                        "coauthoring_state",
                        "version_ref",
                        "space_ref",
                        "chat_attachment_ref",
                        "task_attachment_ref")),
                "backend-owned file/document facade with guarded editor sessions", List.of(
                        "Export manifests required before provider replacement",
                        "Document editing remains guarded until WOPI session evidence is fresh"), "Select/test files and documents providers, then attach support-safe export/delete evidence.");
    }
}
