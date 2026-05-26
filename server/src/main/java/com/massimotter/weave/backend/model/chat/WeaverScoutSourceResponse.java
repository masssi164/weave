package com.massimotter.weave.backend.model.chat;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Citable allowed source used by a read-only Weaver scout response.")
public record WeaverScoutSourceResponse(
        String kind,
        String ref,
        String label,
        String excerpt) {
}
