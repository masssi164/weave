package com.massimotter.weave.backend.model.files;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Uploaded file metadata returned after a successful files facade upload.")
public record FileUploadResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        FileItemResponse item) {
}
