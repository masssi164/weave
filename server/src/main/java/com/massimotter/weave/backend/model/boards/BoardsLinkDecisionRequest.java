package com.massimotter.weave.backend.model.boards;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BoardsLinkDecisionRequest(
        @NotBlank @Size(max = 160) String decisionRef) {
}
