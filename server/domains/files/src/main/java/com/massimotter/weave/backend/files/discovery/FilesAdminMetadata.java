package com.massimotter.weave.backend.files.discovery;

import com.massimotter.weave.backend.admin.domain.SuiteDomainDeclaration;
import com.massimotter.weave.backend.admin.domain.SuiteDomainDefinition;
import com.massimotter.weave.backend.admin.domain.AdminCapabilityDeclaration;
import com.massimotter.weave.backend.admin.domain.AdminCapabilityDeclaration.ProfileGrant;
import java.util.List;

/** Existing files capability vocabulary for shared Admin simulation and profile presentation. */
public final class FilesAdminMetadata {
    private FilesAdminMetadata() {}

    public static AdminCapabilityDeclaration capabilities() {
        return new AdminCapabilityDeclaration("files", 20,
                List.of("files.read", "files.upload"), List.of("files.read", "files.upload"), List.of(), List.of("files.read", "files.upload"),
                List.of(new ProfileGrant("workspace-admin", 20, List.of("files.read", "files.upload")), new ProfileGrant("member-default", 20, List.of("files.read", "files.upload"))));
    }
    public static SuiteDomainDeclaration suite() {
        return new SuiteDomainDeclaration("files", 0, 0,
                new SuiteDomainDefinition(
                        "files-docs", "Files and documents", List.of("files"), List.of("files.read"), List.of("files.upload", "files.delete"), List.of(
                        "drive",
                        "folder",
                        "file",
                        "file_version",
                        "blob_ref",
                        "permission",
                        "share_link",
                        "lock",
                        "trash_entry",
                        "checksum")),
                "backend-owned file/document facade with guarded editor sessions", List.of(
                        "Export manifests required before provider replacement",
                        "Document editing remains guarded until WOPI session evidence is fresh"), "Select/test files and documents providers, then attach support-safe export/delete evidence.");
    }
}
