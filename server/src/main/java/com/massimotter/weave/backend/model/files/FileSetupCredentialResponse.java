package com.massimotter.weave.backend.model.files;

import java.time.OffsetDateTime;
import java.util.List;

public record FileSetupCredentialResponse(
        String credentialId,
        String state,
        String principalRef,
        String clientType,
        String label,
        OffsetDateTime issuedAt,
        OffsetDateTime expiresAt,
        OffsetDateTime revokedAt,
        boolean secretMaterialReturned,
        String webDavBasePath,
        List<String> revocationActions) {

    public FileSetupCredentialResponse {
        revocationActions = revocationActions == null ? List.of() : List.copyOf(revocationActions);
    }
}
