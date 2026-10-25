package com.massimotter.weave.backend.model.files;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "Files facade folder listing response.")
public record FileListResponse(
        @Schema(description = "Listed product-relative folder path.", example = "/",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String path,
        @Schema(description = "Files and folders directly under the listed path.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        List<FileItemResponse> items,
        @Schema(description = "Quota snapshot when the backing store exposes one.")
        FileQuotaResponse quota) {
}
