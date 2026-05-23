package com.massimotter.weave.backend.model.files;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Create a folder through the product files facade.")
public record CreateFolderRequest(
        @Schema(description = "Parent product-relative path.", example = "/Team")
        @NotBlank
        @Size(max = 1024)
        @Pattern(regexp = "/.*", message = "must start with /")
        String parentPath,
        @Schema(description = "Folder name to create.", example = "Design")
        @NotBlank
        @Size(max = 255)
        String name) {
}
