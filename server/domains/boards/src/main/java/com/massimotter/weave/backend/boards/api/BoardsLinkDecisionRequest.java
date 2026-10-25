package com.massimotter.weave.backend.boards.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BoardsLinkDecisionRequest(
        @NotBlank @Size(max = 160) String decisionRef) {
}
