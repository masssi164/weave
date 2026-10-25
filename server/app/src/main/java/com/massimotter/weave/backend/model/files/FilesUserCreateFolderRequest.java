package com.massimotter.weave.backend.model.files;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FilesUserCreateFolderRequest(
        @NotBlank @Size(max = 255) String parentFileId,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 255) String spaceRef) {
    public FilesUserCreateFolderRequest(String parentFileId, String name) {
        this(parentFileId, name, null);
    }
}
