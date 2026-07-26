package com.massimotter.weave.backend.identity.invitation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Temporary work state. It is never a membership or request-authorization source. */
public record ProvisioningIntent(
        UUID intentId,
        String tenantId,
        String organizationId,
        String invitedEmail,
        String invitedEmailSha256,
        String requestedRole,
        List<String> requestedCapabilities,
        String providerInvitationId,
        String invitedByIssuer,
        String invitedBySubject,
        String auditCorrelation,
        ProvisioningIntentStatus status,
        String appliedSubject,
        String failureCode,
        Instant expiresAt,
        Instant createdAt,
        Instant updatedAt) {

    public ProvisioningIntent {
        requestedCapabilities =
                requestedCapabilities == null ? List.of() : List.copyOf(requestedCapabilities);
    }

    public ProvisioningIntent withProviderInvitation(String providerId, Instant now) {
        return new ProvisioningIntent(intentId, tenantId, organizationId, invitedEmail, invitedEmailSha256, requestedRole,
                requestedCapabilities, providerId, invitedByIssuer, invitedBySubject, auditCorrelation,
                status, appliedSubject, failureCode, expiresAt, createdAt, now);
    }

    public ProvisioningIntent applied(String subject, Instant now) {
        return transition(ProvisioningIntentStatus.APPLIED, subject, null, now);
    }

    public ProvisioningIntent failed(String code, Instant now) {
        return transition(ProvisioningIntentStatus.FAILED, null, code, now);
    }

    public ProvisioningIntent expired(Instant now) {
        return transition(ProvisioningIntentStatus.EXPIRED, null, null, now);
    }

    private ProvisioningIntent transition(ProvisioningIntentStatus next, String subject, String failure, Instant now) {
        return new ProvisioningIntent(intentId, tenantId, organizationId, invitedEmail, invitedEmailSha256, requestedRole,
                requestedCapabilities, providerInvitationId, invitedByIssuer, invitedBySubject, auditCorrelation,
                next, subject, failure, expiresAt, createdAt, now);
    }
}
