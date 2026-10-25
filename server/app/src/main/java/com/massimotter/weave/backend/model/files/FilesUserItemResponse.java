package com.massimotter.weave.backend.model.files;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/** Member projection; provider object references and DAV URLs stay private. */
public record FilesUserItemResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String fileId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String parentFileId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Display-only product path; never use as an identity.") String displayPath,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"file", "folder"}) String kind,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long size,
        String mediaType,
        Instant modifiedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Opaque resource revision; not a provider version.") String revision,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Actions currently authorized at the Weave member/resource boundary and supported by the active provider. A later provider state change may still reject an operation.")
        List<String> allowedActions) {
    public FilesUserItemResponse { allowedActions = List.copyOf(allowedActions); }
}
