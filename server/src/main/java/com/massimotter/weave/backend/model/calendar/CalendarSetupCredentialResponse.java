package com.massimotter.weave.backend.model.calendar;

import java.time.OffsetDateTime;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Weave-issued, revocable CalDAV client credential. Secret material is returned only at creation.")
public record CalendarSetupCredentialResponse(
        String credentialId,
        String state,
        String username,
        String principalRef,
        String clientType,
        String label,
        OffsetDateTime issuedAt,
        OffsetDateTime expiresAt,
        OffsetDateTime revokedAt,
        boolean secretMaterialReturned,
        @Schema(description = "One-time CalDAV secret. Present only in the create response.", nullable = true)
        String secret,
        boolean profilePasswordEligible,
        List<String> revocationActions) {
}
