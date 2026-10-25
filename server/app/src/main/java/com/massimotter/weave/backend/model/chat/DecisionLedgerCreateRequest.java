package com.massimotter.weave.backend.model.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "Explicit member action to create a channel Decision Ledger record.")
public record DecisionLedgerCreateRequest(
        @NotBlank
        @Size(max = 160)
        @Schema(description = "Decision title in member vocabulary.", example = "Use governed channel workspace tabs for Sprint 4")
        String title,
        @Pattern(regexp = "proposed|accepted|superseded|rejected")
        @Schema(description = "Lifecycle state. Defaults to proposed.", example = "proposed")
        String status,
        @Size(max = 12)
        List<@Size(max = 120) String> risks,
        @Size(max = 12)
        List<@Size(max = 120) String> openQuestions,
        @Size(max = 12)
        List<@Size(max = 160) String> followUpRefs,
        @Size(min = 1, max = 8)
        List<@Valid DecisionLedgerReferenceRequest> references) {

    public DecisionLedgerCreateRequest {
        status = status == null || status.isBlank() ? "proposed" : status.trim().toLowerCase();
        risks = risks == null ? List.of() : List.copyOf(risks);
        openQuestions = openQuestions == null ? List.of() : List.copyOf(openQuestions);
        followUpRefs = followUpRefs == null ? List.of() : List.copyOf(followUpRefs);
        references = references == null ? List.of() : List.copyOf(references);
    }
}
