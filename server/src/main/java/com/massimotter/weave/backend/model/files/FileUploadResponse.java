package com.massimotter.weave.backend.model.files;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Uploaded file metadata returned after a successful files facade upload.")
public record FileUploadResponse(FileItemResponse item) {
}
