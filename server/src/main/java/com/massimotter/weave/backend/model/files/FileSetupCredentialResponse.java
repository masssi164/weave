package com.massimotter.weave.backend.model.files;

import java.time.OffsetDateTime;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Weave-issued, revocable WebDAV client credential. Secret material is returned only at creation.")
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
        String username,
        @Schema(description = "One-time WebDAV secret. Present only in the create response.", nullable = true)
        String secret,
        String webDavBasePath,
        List<String> revocationActions) {

    public FileSetupCredentialResponse {
        revocationActions = revocationActions == null ? List.of() : List.copyOf(revocationActions);
    }
}
