package com.massimotter.weave.backend.model.calls;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "Create a Weave-owned call control-plane record.")
public record CallCreateRequest(
        @Schema(description = "Context/Space reference for the call.", example = "workspace-default")
        @Size(max = 128)
        String spaceId,
        @Schema(description = "Call title shown in Weave surfaces.", example = "Planning call")
        @Size(max = 255)
        String title,
        @Schema(description = "Support-safe linked calendar refs.")
        List<@Size(max = 256) String> linkedCalendarRefs,
        @Schema(description = "Support-safe linked chat refs.")
        List<@Size(max = 256) String> linkedChatRefs,
        @Schema(description = "Support-safe linked file refs.")
        List<@Size(max = 256) String> linkedFileRefs,
        @Schema(description = "Support-safe linked decision refs.")
        List<@Size(max = 256) String> linkedDecisionRefs) {
}
