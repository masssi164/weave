package com.massimotter.weave.backend.files.api;

import java.util.List;

public record FileSetupCredentialListResponse(List<FileSetupCredentialResponse> credentials) {

    public FileSetupCredentialListResponse {
        credentials = credentials == null ? List.of() : List.copyOf(credentials);
    }
}
