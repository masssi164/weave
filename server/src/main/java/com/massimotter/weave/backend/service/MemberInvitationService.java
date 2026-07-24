package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditEvent;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.audit.AuditRedactionLevel;
import com.massimotter.weave.backend.config.IdentityInvitationProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.identity.invitation.KeycloakIdentityAdminClient;
import com.massimotter.weave.backend.identity.invitation.KeycloakIdentityAdminClient.ProviderInvitation;
import com.massimotter.weave.backend.identity.invitation.ProvisioningIntent;
import com.massimotter.weave.backend.identity.invitation.ProvisioningIntentRepository;
import com.massimotter.weave.backend.identity.invitation.ProvisioningIntentStatus;
import com.massimotter.weave.backend.model.identity.MemberInvitationRequest;
import com.massimotter.weave.backend.model.identity.MemberInvitationResponse;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class MemberInvitationService {
    private final ProvisioningIntentRepository intents;
    private final KeycloakIdentityAdminClient keycloak;
    private final IdentityInvitationProperties properties;
    private final AuditEventPublisher audit;
    private final Clock clock;

    @Autowired
    public MemberInvitationService(ProvisioningIntentRepository intents, KeycloakIdentityAdminClient keycloak,
            IdentityInvitationProperties properties, AuditEventPublisher audit) {
        this(intents, keycloak, properties, audit, Clock.systemUTC());
    }
    MemberInvitationService(ProvisioningIntentRepository intents, KeycloakIdentityAdminClient keycloak,
            IdentityInvitationProperties properties, AuditEventPublisher audit, Clock clock) {
        this.intents=intents; this.keycloak=keycloak; this.properties=properties; this.audit=audit; this.clock=clock;
    }

    public MemberInvitationResponse create(String organizationId, MemberInvitationRequest request, String idempotencyKey, Jwt jwt) {
        OrganizationIdentityContext actor = OrganizationIdentityContextFactory.fromJwt(jwt);
        String email = normalizeEmail(request.email());
        List<String> groups = canonicalHumanGroups(request.role(), request.organizationGroups());
        List<ProvisioningIntent> existing = intents.findPendingByEmail(actor.organizationId(), organizationId, email);
        if (!existing.isEmpty()) throw new ApiErrorException(HttpStatus.CONFLICT, "member-invitation-already-pending",
                "A pending provisioning intent already exists for this organization address.", Map.of());
        Instant now=clock.instant();
        ProvisioningIntent pending = new ProvisioningIntent(UUID.randomUUID(), actor.organizationId(), organizationId,
                email, sha256(email), request.role(), groups, null, jwt.getIssuer().toString(),
                actor.subject(), idempotencyKey, ProvisioningIntentStatus.PENDING, null, null,
                now.plus(properties.defaultLifetime()), now, now);
        intents.save(pending);
        try {
            ProviderInvitation provider = keycloak.issue(organizationId, email, blankToNull(request.displayName()));
            ProvisioningIntent linked = intents.save(pending.withProviderInvitation(provider.providerInvitationId(), clock.instant()));
            publish(AuditAction.MEMBER_INVITATION_CREATED, linked, actor.subject());
            return MemberInvitationResponse.from(provider, linked);
        } catch (RuntimeException exception) {
            intents.save(pending.failed("provider_issue_failed", clock.instant()));
            throw new ApiErrorException(HttpStatus.BAD_GATEWAY, "member-invitation-provider-unavailable",
                    "Keycloak could not create the organization invitation.", Map.of());
        }
    }

    public List<MemberInvitationResponse> list(String organizationId, Jwt jwt) {
        OrganizationIdentityContextFactory.fromJwt(jwt);
        return keycloak.list(organizationId).stream().map(provider -> intents.findByProviderInvitationId(provider.providerInvitationId())
                .map(intent -> MemberInvitationResponse.from(provider, expire(intent)))
                .orElseGet(() -> MemberInvitationResponse.withoutProvisioning(provider, organizationId))).toList();
    }

    public MemberInvitationResponse resend(String organizationId, String providerId, String idempotencyKey, Jwt jwt) {
        ProvisioningIntent intent=requireIntent(organizationId, providerId, jwt);
        ProviderInvitation provider=keycloak.resend(organizationId, providerId);
        publish(AuditAction.MEMBER_INVITATION_RESENT, intent, jwt.getSubject());
        return MemberInvitationResponse.from(provider, intent);
    }

    public void revoke(String organizationId, String providerId, String idempotencyKey, Jwt jwt) {
        ProvisioningIntent intent=requireIntent(organizationId, providerId, jwt);
        keycloak.revoke(organizationId, providerId);
        intents.save(intent.expired(clock.instant()));
        publish(AuditAction.MEMBER_INVITATION_REVOKED, intent, jwt.getSubject());
    }

    /** Provider-neutral first-login reconciliation; never used to authorize the current request. */
    public void reconcileAuthenticated(Jwt jwt) {
        OrganizationIdentityContext actor=OrganizationIdentityContextFactory.fromJwt(jwt);
        String email=jwt.getClaimAsString("email");
        if (email == null || !Boolean.TRUE.equals(jwt.getClaims().get("email_verified"))) return;
        String organizationId = keycloak.configuredOrganizationId();
        List<ProvisioningIntent> matches=intents.findPendingByEmail(actor.organizationId(), organizationId, normalizeEmail(email));
        if (matches.size() != 1 || !keycloak.isOrganizationMember(organizationId, actor.subject())) return;
        apply(matches.getFirst(), actor.subject());
    }

    private void apply(ProvisioningIntent intent, String subject) {
        if (intent.status() == ProvisioningIntentStatus.APPLIED) return;
        if (intent.expiresAt().isBefore(clock.instant())) { intents.save(intent.expired(clock.instant())); return; }
        try {
            keycloak.applyRoleAndGroups(subject, intent.requestedRole(), intent.organizationGroups());
            ProvisioningIntent applied=intents.save(intent.applied(subject, clock.instant()));
            publish(AuditAction.MEMBER_INVITATION_ACCEPTED, applied, subject);
        } catch (RuntimeException exception) {
            intents.save(intent.failed("keycloak_provisioning_failed", clock.instant()));
            throw exception;
        }
    }
    private ProvisioningIntent requireIntent(String organizationId, String providerId, Jwt jwt) {
        OrganizationIdentityContext actor=OrganizationIdentityContextFactory.fromJwt(jwt);
        ProvisioningIntent intent=intents.findByProviderInvitationId(providerId).orElseThrow(() -> notFound());
        if (!intent.tenantId().equals(actor.organizationId()) || !intent.organizationId().equals(organizationId)) throw notFound();
        return intent;
    }
    private ApiErrorException notFound() { return new ApiErrorException(HttpStatus.NOT_FOUND, "member-invitation-not-found", "The invitation does not exist.", Map.of()); }
    private ProvisioningIntent expire(ProvisioningIntent intent) {
        return intent.status()==ProvisioningIntentStatus.PENDING && intent.expiresAt().isBefore(clock.instant())
                ? intents.save(intent.expired(clock.instant())) : intent;
    }
    private void publish(AuditAction action, ProvisioningIntent intent, String actor) {
        audit.publish(new AuditEvent(intent.tenantId(), intent.organizationId(), actor, "identity-provisioning-intent",
                action, clock.instant(), intent.auditCorrelation(), AuditRedactionLevel.SUPPORT_SAFE,
                Map.of("providerInvitationId", intent.providerInvitationId()==null?"pending":intent.providerInvitationId(),
                        "emailSha256", intent.invitedEmailSha256(), "role", intent.requestedRole(),
                        "provisioningStatus", intent.status().name().toLowerCase())));
    }
    private List<String> canonicalHumanGroups(String role, List<String> requestedGroups) {
        String expected = switch (role) {
            case "owner" -> "/weave/owners";
            case "admin" -> "/weave/admins";
            case "member" -> "/weave/members";
            case "guest" -> "/weave/guests";
            default -> throw invalidGroups();
        };
        if (requestedGroups == null || requestedGroups.isEmpty()) return List.of(expected);
        if (requestedGroups.size() != 1 || !expected.equals(requestedGroups.getFirst())) throw invalidGroups();
        return List.of(expected);
    }
    private ApiErrorException invalidGroups() {
        return new ApiErrorException(HttpStatus.BAD_REQUEST, "member-invitation-groups-invalid",
                "The requested organization groups do not match the selected member role.", Map.of());
    }
    private String normalizeEmail(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    private String blankToNull(String value) { return value==null||value.isBlank()?null:value.trim(); }
    private String sha256(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 is unavailable", e); } }
}
