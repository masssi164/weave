package com.massimotter.weave.backend.model.files;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Files quota snapshot when available from the backing store.")
public record FileQuotaResponse(
        @Schema(description = "Used bytes when known.", example = "1048576")
        Long usedBytes,
        @Schema(description = "Total available bytes when known.", example = "10737418240")
        Long totalBytes) {
}
