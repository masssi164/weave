package com.massimotter.weave.backend.model.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;

public record KeycloakMembershipEvent(
        int schemaVersion,
        @NotBlank String eventId,
        Instant occurredAt,
        @NotBlank String realmId,
        @NotBlank String organizationId,
        @NotBlank String userSubject,
        @NotBlank String eventType,
        @NotBlank @Pattern(regexp = "[a-fA-F0-9]{64}") String invitedEmailHash) {}
