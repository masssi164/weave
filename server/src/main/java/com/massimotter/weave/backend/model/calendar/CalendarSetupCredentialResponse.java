package com.massimotter.weave.backend.model.calendar;

import java.time.OffsetDateTime;
import java.util.List;

public record CalendarSetupCredentialResponse(
        String credentialId,
        String state,
        String username,
        String clientType,
        String label,
        OffsetDateTime issuedAt,
        OffsetDateTime expiresAt,
        OffsetDateTime revokedAt,
        boolean secretMaterialReturned,
        boolean profilePasswordEligible,
        List<String> revocationActions) {
}
