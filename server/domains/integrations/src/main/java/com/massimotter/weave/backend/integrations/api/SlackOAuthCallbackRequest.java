package com.massimotter.weave.backend.integrations.api;

import jakarta.validation.constraints.Size;

public record SlackOAuthCallbackRequest(
        @Size(max = 2048) String code,
        @Size(max = 2048) String state) {
}
