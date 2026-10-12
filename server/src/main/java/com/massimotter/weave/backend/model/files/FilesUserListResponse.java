package com.massimotter.weave.backend.model.files;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record FilesUserListResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String parentFileId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> allowedActions,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<FilesUserItemResponse> items) {
    public FilesUserListResponse {
        allowedActions = List.copyOf(allowedActions);
        items = List.copyOf(items);
    }
}
