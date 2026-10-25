package com.massimotter.weave.backend.files.api;

import jakarta.validation.constraints.Size;

public record FileSetupCredentialRequest(
        @Size(max = 128) String label,
        @Size(max = 32) String clientType) {
}
