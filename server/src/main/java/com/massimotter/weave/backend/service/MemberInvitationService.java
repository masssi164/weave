package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditEvent;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.audit.AuditRedactionLevel;
import com.massimotter.weave.backend.config.IdentityInvitationProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.identity.invitation.KeycloakIdentityAdminClient;
import com.massimotter.weave.backend.identity.invitation.MemberInvitation;
import com.massimotter.weave.backend.identity.invitation.MemberInvitationRepository;
import com.massimotter.weave.backend.identity.invitation.MemberInvitationStatus;
import com.massimotter.weave.backend.model.identity.MemberInvitationRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class MemberInvitationService {
    private final MemberInvitationRepository repository;
    private final KeycloakIdentityAdminClient keycloak;
    private final IdentityInvitationProperties properties;
    private final AuditEventPublisher audit;
    private final Clock clock;

    @Autowired
    public MemberInvitationService(MemberInvitationRepository repository, KeycloakIdentityAdminClient keycloak,
            IdentityInvitationProperties properties, AuditEventPublisher audit) {
        this(repository, keycloak, properties, audit, Clock.systemUTC());
    }

    MemberInvitationService(MemberInvitationRepository repository, KeycloakIdentityAdminClient keycloak,
            IdentityInvitationProperties properties, AuditEventPublisher audit, Clock clock) {
        this.repository = repository;
        this.keycloak = keycloak;
        this.properties = properties;
        this.audit = audit;
        this.clock = clock;
    }

    public MemberInvitation create(String organizationId, MemberInvitationRequest request, String idempotencyKey, Jwt jwt) {
        OrganizationIdentityContext identity = OrganizationIdentityContextFactory.fromJwt(jwt);
        if (!"member".equals(request.role()) || !request.workspaceIds().isEmpty()) {
            throw new ApiErrorException(HttpStatus.UNPROCESSABLE_ENTITY, "member-invitation-provisioning-unsupported",
                    "Only the default member access profile is currently provisioned safely.", Map.of());
        }
        String tenantId = identity.organizationId();
        String email = normalizeEmail(request.email());
        repository.findPendingByEmail(tenantId, organizationId, email).ifPresent(existing -> {
            throw new ApiErrorException(HttpStatus.CONFLICT, "member-invitation-already-pending",
                    "A pending invitation already exists for this organization address.",
                    Map.of("invitationId", existing.invitationId().toString()));
        });
        Instant now = clock.instant();
        Instant expiresAt = request.expiresAt() == null ? now.plus(properties.defaultLifetime()) : request.expiresAt();
        if (!expiresAt.isAfter(now)) {
            throw new ApiErrorException(HttpStatus.BAD_REQUEST, "member-invitation-expiry-invalid",
                    "Invitation expiry must be in the future.", Map.of());
        }
        MemberInvitation pending = new MemberInvitation(UUID.randomUUID(), tenantId, organizationId, email,
                blankToNull(request.displayName()), request.role(), request.workspaceIds(), MemberInvitationStatus.PENDING,
                null, identity.subject(), expiresAt, now, now);
        repository.save(pending);
        try {
            KeycloakIdentityAdminClient.ProviderInvitation issued = keycloak.issue(pending);
            MemberInvitation sent = repository.save(pending.withProviderInvitation(issued.providerInvitationId(), clock.instant()));
            publish(AuditAction.MEMBER_INVITATION_CREATED, sent, identity.subject(), idempotencyKey);
            return sent;
        } catch (RuntimeException exception) {
            repository.save(pending.withStatus(MemberInvitationStatus.DELIVERY_FAILED, clock.instant()));
            throw new ApiErrorException(HttpStatus.BAD_GATEWAY, "member-invitation-provider-unavailable",
                    "The identity provider could not send the invitation.", Map.of());
        }
    }

    public List<MemberInvitation> list(String organizationId, Jwt jwt) {
        OrganizationIdentityContext identity = OrganizationIdentityContextFactory.fromJwt(jwt);
        Instant now = clock.instant();
        return repository.findByOrganization(identity.organizationId(), organizationId).stream()
                .map(invitation -> expireIfNeeded(invitation, now))
                .toList();
    }

    public MemberInvitation resend(String organizationId, UUID invitationId, String idempotencyKey, Jwt jwt) {
        MemberInvitation invitation = requireForOrganization(organizationId, invitationId, jwt);
        if (invitation.status() == MemberInvitationStatus.ACCEPTED || invitation.status() == MemberInvitationStatus.REVOKED) {
            throw invalidLifecycle(invitation);
        }
        KeycloakIdentityAdminClient.ProviderInvitation issued = keycloak.resend(invitation);
        Instant now = clock.instant();
        MemberInvitation resent = new MemberInvitation(invitation.invitationId(), invitation.tenantId(),
                invitation.organizationId(), invitation.invitedEmail(), invitation.displayName(), invitation.requestedRole(),
                invitation.workspaceIds(), MemberInvitationStatus.SENT, issued.providerInvitationId(),
                invitation.invitedBySubject(), now.plus(properties.defaultLifetime()), invitation.createdAt(), now);
        repository.save(resent);
        publish(AuditAction.MEMBER_INVITATION_RESENT, resent, jwt.getSubject(), idempotencyKey);
        return resent;
    }

    public MemberInvitation revoke(String organizationId, UUID invitationId, String idempotencyKey, Jwt jwt) {
        MemberInvitation invitation = requireForOrganization(organizationId, invitationId, jwt);
        if (invitation.status() == MemberInvitationStatus.ACCEPTED || invitation.status() == MemberInvitationStatus.REVOKED) {
            throw invalidLifecycle(invitation);
        }
        keycloak.revoke(invitation);
        MemberInvitation revoked = repository.save(invitation.withStatus(MemberInvitationStatus.REVOKED, clock.instant()));
        publish(AuditAction.MEMBER_INVITATION_REVOKED, revoked, jwt.getSubject(), idempotencyKey);
        return revoked;
    }

    public MemberInvitationStatus reconcileAuthenticated(Jwt jwt) {
        OrganizationIdentityContext identity = OrganizationIdentityContextFactory.fromJwt(jwt);
        String email = jwt.getClaimAsString("email");
        if (email == null || !Boolean.TRUE.equals(jwt.getClaims().get("email_verified"))) {
            return MemberInvitationStatus.PENDING;
        }
        return repository.findPendingByEmail(identity.organizationId(), properties.keycloak().organizationAlias(), normalizeEmail(email))
                .map(invitation -> activateIfAccepted(invitation, identity, email))
                .orElse(MemberInvitationStatus.ACCEPTED);
    }

    private MemberInvitationStatus activateIfAccepted(MemberInvitation invitation, OrganizationIdentityContext identity, String email) {
        if (invitation.expiresAt().isBefore(clock.instant())) {
            repository.save(invitation.withStatus(MemberInvitationStatus.EXPIRED, clock.instant()));
            return MemberInvitationStatus.EXPIRED;
        }
        if (!keycloak.isAcceptedMember(invitation, identity.subject(), email)) {
            return invitation.status();
        }
        Instant now = clock.instant();
        MemberInvitation accepted = invitation.withStatus(MemberInvitationStatus.ACCEPTED, now);
        repository.markApplied(accepted);
        publish(AuditAction.MEMBER_INVITATION_ACCEPTED, accepted, identity.subject(), "activation:" + invitation.invitationId());
        return MemberInvitationStatus.ACCEPTED;
    }

    private MemberInvitation requireForOrganization(String organizationId, UUID invitationId, Jwt jwt) {
        OrganizationIdentityContext identity = OrganizationIdentityContextFactory.fromJwt(jwt);
        MemberInvitation invitation = repository.findById(invitationId)
                .orElseThrow(() -> new ApiErrorException(HttpStatus.NOT_FOUND, "member-invitation-not-found",
                        "The invitation does not exist.", Map.of()));
        if (!invitation.tenantId().equals(identity.organizationId()) || !invitation.organizationId().equals(organizationId)) {
            throw new ApiErrorException(HttpStatus.NOT_FOUND, "member-invitation-not-found",
                    "The invitation does not exist.", Map.of());
        }
        return invitation;
    }

    private MemberInvitation expireIfNeeded(MemberInvitation invitation, Instant now) {
        if ((invitation.status() == MemberInvitationStatus.PENDING || invitation.status() == MemberInvitationStatus.SENT)
                && invitation.expiresAt().isBefore(now)) {
            return repository.save(invitation.withStatus(MemberInvitationStatus.EXPIRED, now));
        }
        return invitation;
    }

    private ApiErrorException invalidLifecycle(MemberInvitation invitation) {
        return new ApiErrorException(HttpStatus.CONFLICT, "member-invitation-state-invalid",
                "The invitation cannot be changed in its current state.", Map.of("status", invitation.status().name().toLowerCase()));
    }

    private void publish(AuditAction action, MemberInvitation invitation, String actor, String idempotencyKey) {
        audit.publish(new AuditEvent(invitation.tenantId(), invitation.organizationId(), actor, "identity-invitation",
                action, clock.instant(), idempotencyKey, AuditRedactionLevel.SUPPORT_SAFE,
                Map.of("invitationId", invitation.invitationId().toString(), "emailSha256", sha256(invitation.invitedEmail()),
                        "role", invitation.requestedRole(), "status", invitation.status().name().toLowerCase())));
    }

    private String normalizeEmail(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
