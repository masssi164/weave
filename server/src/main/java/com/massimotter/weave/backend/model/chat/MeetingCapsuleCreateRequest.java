package com.massimotter.weave.backend.model.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "Explicit member action to create a channel Meeting Capsule.")
public record MeetingCapsuleCreateRequest(
        @NotBlank
        @Size(max = 160)
        String title,
        @Size(min = 1, max = 12)
        List<@Size(max = 160) String> agendaItems,
        @Size(max = 12)
        List<@Size(max = 160) String> followUpRefs) {

    public MeetingCapsuleCreateRequest {
        agendaItems = agendaItems == null ? List.of() : List.copyOf(agendaItems);
        followUpRefs = followUpRefs == null ? List.of() : List.copyOf(followUpRefs);
    }
}
