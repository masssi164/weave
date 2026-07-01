package com.massimotter.weave.backend.model.files;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;

@Schema(description = "Product file or folder metadata returned by the Weave files facade.")
public record FileItemResponse(
        @Schema(description = "Stable backend facade identifier for this item.", example = "files:/Team/readme.md",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String id,
        @Schema(description = "Display name of the file or folder.", example = "readme.md",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String name,
        @Schema(description = "Product-relative path of the item.", example = "/Team/readme.md",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String path,
        @Schema(description = "Item type.", example = "file", allowableValues = {"file", "folder"},
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String type,
        @Schema(description = "MIME type for files when known.", example = "text/markdown")
        String mimeType,
        @Schema(description = "Size in bytes for files when known.", example = "4096")
        Long size,
        @Schema(description = "Last modified timestamp when provided by the backing store.")
        OffsetDateTime modifiedAt,
        @Schema(description = "Whether the item can be downloaded through the product facade.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        boolean downloadable) {
}
