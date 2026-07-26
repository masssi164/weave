package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditEvent;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.audit.AuditRedactionLevel;
import com.massimotter.weave.backend.config.IdentityInvitationProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.identity.invitation.KeycloakIdentityAdminClient;
import com.massimotter.weave.backend.identity.IdentityOpaqueReferenceCodec;
import com.massimotter.weave.backend.identity.invitation.KeycloakIdentityAdminClient.ProviderInvitation;
import com.massimotter.weave.backend.identity.invitation.ProvisioningIntent;
import com.massimotter.weave.backend.identity.invitation.ProvisioningIntentRepository;
import com.massimotter.weave.backend.identity.invitation.ProvisioningIntentStatus;
import com.massimotter.weave.backend.model.identity.BootstrapOwnerInvitationRequest;
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
  private static final String BOOTSTRAP_ISSUER = "urn:weave:identity-bootstrap";
  private static final String BOOTSTRAP_SUBJECT = "bootstrap-owner-invitation";

  private final ProvisioningIntentRepository intents;
  private final KeycloakIdentityAdminClient keycloak;
  private final IdentityInvitationProperties properties;
  private final IdentityOpaqueReferenceCodec references;
  private final AuditEventPublisher audit;
  private final Clock clock;

  @Autowired
  public MemberInvitationService(
      ProvisioningIntentRepository intents,
      KeycloakIdentityAdminClient keycloak,
      IdentityInvitationProperties properties,
      IdentityOpaqueReferenceCodec references,
      AuditEventPublisher audit) {
    this(intents, keycloak, properties, references, audit, Clock.systemUTC());
  }

  MemberInvitationService(
      ProvisioningIntentRepository intents,
      KeycloakIdentityAdminClient keycloak,
      IdentityInvitationProperties properties,
      IdentityOpaqueReferenceCodec references,
      AuditEventPublisher audit,
      Clock clock) {
    this.intents = intents;
    this.keycloak = keycloak;
    this.properties = properties;
    this.references = references;
    this.audit = audit;
    this.clock = clock;
  }

  public MemberInvitationResponse create(
      String organizationId,
      MemberInvitationRequest request,
      String idempotencyKey,
      Jwt jwt) {
    OrganizationIdentityContext actor = OrganizationIdentityContextFactory.fromJwt(jwt);
    if (!actor.organizationId().equals(organizationId)) {
      throw notFound();
    }
    return create(
        actor.organizationId(),
        keycloak.configuredOrganizationId(),
        request,
        idempotencyKey,
        jwt.getIssuer().toString(),
        actor.subject());
  }

  /**
   * Creates the first owner invitation only while the target realm has no human identities.
   *
   * <p>The deployment contract exposes this method through one bootstrap-enabled server instance.
   * Synchronization closes concurrent calls in that instance; provider and persistence correlation
   * then fail closed on every ambiguous retry.
   */
  public synchronized MemberInvitationResponse bootstrapOwner(
      BootstrapOwnerInvitationRequest request, String idempotencyKey) {
    if (keycloak.hasHumanUsers()) {
      throw bootstrapConflict();
    }

    String organizationId = keycloak.configuredOrganizationId();
    String tenantId = properties.bootstrapOwner().tenantId();
    if (tenantId.isBlank()) {
      throw new IllegalStateException("Owner bootstrap tenant is not configured");
    }

    String email = normalizeEmail(request.email());
    List<ProvisioningIntent> pending =
        intents.findPendingByEmail(tenantId, organizationId, email);
    if (pending.size() > 1) {
      throw bootstrapConflict();
    }
    if (pending.size() == 1) {
      return existingBootstrapInvitation(organizationId, email, pending.getFirst());
    }
    if (!keycloak.invitationsForEmail(organizationId, email).isEmpty()) {
      throw bootstrapConflict();
    }

    return create(
        tenantId,
        organizationId,
        new MemberInvitationRequest(email, request.displayName(), "owner", List.of()),
        idempotencyKey,
        BOOTSTRAP_ISSUER,
        BOOTSTRAP_SUBJECT);
  }

  public List<MemberInvitationResponse> list(String organizationId, Jwt jwt) {
    OrganizationIdentityContext actor = OrganizationIdentityContextFactory.fromJwt(jwt);
    if (!actor.organizationId().equals(organizationId)) {
      throw notFound();
    }
    String keycloakOrganizationId = keycloak.configuredOrganizationId();
    return keycloak.list(keycloakOrganizationId).stream()
        .map(
            provider ->
                intents
                    .findByProviderInvitationId(provider.providerInvitationId())
                    .map(intent -> response(provider, expire(intent)))
                    .orElseGet(
                        () ->
                            MemberInvitationResponse.withoutProvisioning(
                                references.invitation(
                                    keycloak.configuredOrganizationId(),
                                    provider.providerInvitationId()),
                                provider,
                                organizationId)))
        .toList();
  }

  public MemberInvitationResponse resend(
      String organizationId, String invitationHandle, String idempotencyKey, Jwt jwt) {
    String providerId = resolveProviderInvitationId(organizationId, invitationHandle, jwt);
    ProvisioningIntent intent = requireIntent(organizationId, providerId, jwt);
    ProviderInvitation provider =
        keycloak.resend(keycloak.configuredOrganizationId(), providerId);
    publish(AuditAction.MEMBER_INVITATION_RESENT, intent, jwt.getSubject());
    return response(provider, intent);
  }

  public void revoke(
      String organizationId, String invitationHandle, String idempotencyKey, Jwt jwt) {
    String providerId = resolveProviderInvitationId(organizationId, invitationHandle, jwt);
    ProvisioningIntent intent = requireIntent(organizationId, providerId, jwt);
    keycloak.revoke(keycloak.configuredOrganizationId(), providerId);
    intents.save(intent.expired(clock.instant()));
    publish(AuditAction.MEMBER_INVITATION_REVOKED, intent, jwt.getSubject());
  }

  /** First-login fallback for a missed Keycloak event; never used to authorize the request. */
  public void reconcileAuthenticated(Jwt jwt) {
    OrganizationIdentityContext actor = OrganizationIdentityContextFactory.fromJwt(jwt);
    String email = jwt.getClaimAsString("email");
    if (email == null || !Boolean.TRUE.equals(jwt.getClaims().get("email_verified"))) {
      return;
    }

    String organizationId = keycloak.configuredOrganizationId();
    List<ProvisioningIntent> matches =
        intents.findPendingByEmail(
            actor.organizationId(), organizationId, normalizeEmail(email));
    if (matches.size() != 1
        || !keycloak.isOrganizationMember(organizationId, actor.subject())) {
      return;
    }
    apply(matches.getFirst(), actor.subject());
  }

  public void applyMembershipEvent(String organizationId, String subject, String emailHash) {
    List<ProvisioningIntent> matches =
        intents.findPendingByEmailHash(organizationId, emailHash.toLowerCase(Locale.ROOT));
    if (matches.size() != 1) {
      if (matches.isEmpty()) {
        return;
      }
      throw new IllegalStateException("Provisioning intent correlation is ambiguous");
    }
    apply(matches.getFirst(), subject);
  }

  public boolean recordMembershipEventOnce(String eventId, Instant occurredAt) {
    return intents.recordEventOnce(eventId, occurredAt);
  }

  private MemberInvitationResponse existingBootstrapInvitation(
      String organizationId, String email, ProvisioningIntent intent) {
    if (!"owner".equals(intent.requestedRole())
        || !intent.requestedCapabilities().isEmpty()
        || intent.providerInvitationId() == null) {
      throw bootstrapConflict();
    }

    List<ProviderInvitation> providerMatches =
        keycloak.invitationsForEmail(organizationId, email).stream()
            .filter(
                invitation ->
                    intent.providerInvitationId().equals(invitation.providerInvitationId()))
            .toList();
    if (providerMatches.size() != 1) {
      throw bootstrapConflict();
    }
    return response(providerMatches.getFirst(), expire(intent));
  }

  private MemberInvitationResponse create(
      String tenantId,
      String organizationId,
      MemberInvitationRequest request,
      String idempotencyKey,
      String actorIssuer,
      String actorSubject) {
    try {
      keycloak.validateCapabilities(request.capabilities());
    } catch (IllegalArgumentException invalidCapability) {
      throw new ApiErrorException(
          HttpStatus.BAD_REQUEST,
          "member-invitation-capability-unsupported",
          "The invitation contains an unsupported product capability.",
          Map.of());
    }

    String email = normalizeEmail(request.email());
    List<ProvisioningIntent> existing =
        intents.findPendingByEmail(tenantId, organizationId, email);
    if (!existing.isEmpty()) {
      throw new ApiErrorException(
          HttpStatus.CONFLICT,
          "member-invitation-already-pending",
          "A pending provisioning intent already exists for this organization address.",
          Map.of());
    }

    Instant now = clock.instant();
    ProvisioningIntent pending =
        new ProvisioningIntent(
            UUID.randomUUID(),
            tenantId,
            organizationId,
            email,
            sha256(email),
            request.role(),
            request.capabilities(),
            null,
            actorIssuer,
            actorSubject,
            idempotencyKey,
            ProvisioningIntentStatus.PENDING,
            null,
            null,
            now.plus(properties.defaultLifetime()),
            now,
            now);
    intents.save(pending);

    try {
      ProviderInvitation provider =
          keycloak.issue(organizationId, email, blankToNull(request.displayName()));
      ProvisioningIntent linked =
          intents.save(
              pending.withProviderInvitation(
                  provider.providerInvitationId(), clock.instant()));
      publish(AuditAction.MEMBER_INVITATION_CREATED, linked, actorSubject);
      return response(provider, linked);
    } catch (RuntimeException providerFailure) {
      intents.save(pending.failed("provider_issue_failed", clock.instant()));
      throw new ApiErrorException(
          HttpStatus.BAD_GATEWAY,
          "member-invitation-provider-unavailable",
          "Keycloak could not create the organization invitation.",
          Map.of());
    }
  }

  private void apply(ProvisioningIntent intent, String subject) {
    if (intent.status() == ProvisioningIntentStatus.APPLIED) {
      return;
    }
    if (intent.expiresAt().isBefore(clock.instant())) {
      intents.save(intent.expired(clock.instant()));
      return;
    }
    try {
      keycloak.applyRoleAndCapabilities(
          subject, intent.requestedRole(), intent.requestedCapabilities());
      ProvisioningIntent applied = intents.save(intent.applied(subject, clock.instant()));
      publish(AuditAction.MEMBER_INVITATION_ACCEPTED, applied, subject);
    } catch (RuntimeException providerFailure) {
      intents.save(intent.failed("keycloak_provisioning_failed", clock.instant()));
      throw providerFailure;
    }
  }

  private ProvisioningIntent requireIntent(
      String organizationId, String providerId, Jwt jwt) {
    OrganizationIdentityContext actor = OrganizationIdentityContextFactory.fromJwt(jwt);
    ProvisioningIntent intent =
        intents.findByProviderInvitationId(providerId).orElseThrow(this::notFound);
    if (!intent.tenantId().equals(actor.organizationId())
        || !intent.tenantId().equals(organizationId)
        || !intent.organizationId().equals(keycloak.configuredOrganizationId())) {
      throw notFound();
    }
    return intent;
  }

  private String resolveProviderInvitationId(
      String organizationId, String invitationHandle, Jwt jwt) {
    OrganizationIdentityContext actor = OrganizationIdentityContextFactory.fromJwt(jwt);
    if (!organizationId.equals(actor.organizationId())) {
      throw notFound();
    }
    String keycloakOrganizationId = keycloak.configuredOrganizationId();
    List<ProviderInvitation> matches =
        keycloak.list(keycloakOrganizationId).stream()
            .filter(
                invitation ->
                    references
                        .invitation(keycloakOrganizationId, invitation.providerInvitationId())
                        .equals(invitationHandle))
            .toList();
    if (matches.size() != 1) {
      throw notFound();
    }
    return matches.getFirst().providerInvitationId();
  }

  private MemberInvitationResponse response(
      ProviderInvitation invitation, ProvisioningIntent intent) {
    return MemberInvitationResponse.from(
        references.invitation(
            keycloak.configuredOrganizationId(), invitation.providerInvitationId()),
        invitation,
        intent);
  }

  private ProvisioningIntent expire(ProvisioningIntent intent) {
    return intent.status() == ProvisioningIntentStatus.PENDING
            && intent.expiresAt().isBefore(clock.instant())
        ? intents.save(intent.expired(clock.instant()))
        : intent;
  }

  private void publish(AuditAction action, ProvisioningIntent intent, String actor) {
    audit.publish(
        new AuditEvent(
            intent.tenantId(),
            intent.organizationId(),
            actor,
            "identity-provisioning-intent",
            action,
            clock.instant(),
            intent.auditCorrelation(),
            AuditRedactionLevel.SUPPORT_SAFE,
            Map.of(
                "invitationHandle",
                intent.providerInvitationId() == null
                    ? "pending"
                    : references.invitation(
                        intent.organizationId(), intent.providerInvitationId()),
                "emailSha256",
                intent.invitedEmailSha256(),
                "role",
                intent.requestedRole(),
                "provisioningStatus",
                intent.status().name().toLowerCase(Locale.ROOT))));
  }

  private ApiErrorException bootstrapConflict() {
    return new ApiErrorException(
        HttpStatus.CONFLICT,
        "owner-bootstrap-not-empty",
        "The protected owner bootstrap operation requires one empty human realm and one "
            + "unambiguous pending invitation.",
        Map.of());
  }

  private ApiErrorException notFound() {
    return new ApiErrorException(
        HttpStatus.NOT_FOUND,
        "member-invitation-not-found",
        "The invitation does not exist.",
        Map.of());
  }

  private String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 is unavailable", unavailable);
    }
  }
}
