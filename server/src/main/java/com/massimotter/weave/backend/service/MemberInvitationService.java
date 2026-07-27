package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditEvent;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.audit.AuditRedactionLevel;
import com.massimotter.weave.backend.config.IdentityInvitationProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.identity.invitation.KeycloakIdentityAdminClient;
import com.massimotter.weave.backend.identity.IdentityOpaqueReferenceCodec;
import com.massimotter.weave.backend.identity.invitation.KeycloakIdentityAdminClient.KeycloakAdminException;
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
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class MemberInvitationService {
  private static final Logger LOGGER = LoggerFactory.getLogger(MemberInvitationService.class);
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
        new MemberInvitationRequest(email, request.displayName(), "owner"),
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
  public boolean reconcileAuthenticated(Jwt jwt) {
    OrganizationIdentityContext actor = OrganizationIdentityContextFactory.fromJwt(jwt);
    String email = jwt.getClaimAsString("email");
    if (email == null || !Boolean.TRUE.equals(jwt.getClaims().get("email_verified"))) {
      return false;
    }

    String organizationId = keycloak.configuredOrganizationId();
    List<ProvisioningIntent> matches =
        intents.findPendingByEmail(
            actor.organizationId(), organizationId, normalizeEmail(email));
    if (matches.size() != 1
        || !keycloak.isOrganizationMember(organizationId, actor.subject())) {
      return false;
    }
    apply(matches.getFirst(), actor.subject());
    return true;
  }

  private MemberInvitationResponse existingBootstrapInvitation(
      String organizationId, String email, ProvisioningIntent intent) {
    if (!"owner".equals(intent.requestedRole())) {
      throw bootstrapConflict();
    }
    return correlateExistingInvitation(organizationId, email, intent)
        .orElseThrow(this::bootstrapConflict);
  }

  private MemberInvitationResponse create(
      String tenantId,
      String organizationId,
      MemberInvitationRequest request,
      String idempotencyKey,
      String actorIssuer,
      String actorSubject) {
    String email = normalizeEmail(request.email());
    List<ProvisioningIntent> existing =
        intents.findPendingByEmail(tenantId, organizationId, email);
    if (!existing.isEmpty()) {
      if (existing.size() == 1
          && sameRequest(
              existing.getFirst(),
              request,
              idempotencyKey,
              actorIssuer,
              actorSubject)) {
        Optional<MemberInvitationResponse> correlated =
            correlateExistingInvitation(organizationId, email, existing.getFirst());
        if (correlated.isPresent()) {
          return correlated.get();
        }
      }
      throw new ApiErrorException(
          HttpStatus.CONFLICT,
          "member-invitation-already-pending",
          "A pending provisioning intent already exists for this organization address.",
          Map.of());
    }

    references.requireReady();
    Instant now = clock.instant();
    ProvisioningIntent pending =
        new ProvisioningIntent(
            UUID.randomUUID(),
            tenantId,
            organizationId,
            email,
            sha256(email),
            request.role(),
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

    ProviderInvitation provider;
    try {
      provider = keycloak.issue(organizationId, email, blankToNull(request.displayName()));
    } catch (RuntimeException providerFailure) {
      ProviderFailureReference failureReference = providerFailureReference(providerFailure);
      LOGGER.warn(
          "WEAVE_IDENTITY_INVITATION_PROVIDER_FAILURE category={} operation={} status={} failureType={}",
          failureReference.category(),
          failureReference.operation(),
          failureReference.status(),
          providerFailure.getClass().getSimpleName());
      intents.save(pending.failed("provider_issue_failed", clock.instant()));
      throw new ApiErrorException(
          HttpStatus.BAD_GATEWAY,
          "member-invitation-provider-unavailable",
          "Keycloak could not create the organization invitation.",
          Map.of());
    }
    ProvisioningIntent linked =
        intents.save(
            pending.withProviderInvitation(provider.providerInvitationId(), clock.instant()));
    publish(AuditAction.MEMBER_INVITATION_CREATED, linked, actorSubject);
    return response(provider, linked);
  }

  private Optional<MemberInvitationResponse> correlateExistingInvitation(
      String organizationId, String email, ProvisioningIntent intent) {
    List<ProviderInvitation> providerMatches =
        keycloak.invitationsForEmail(organizationId, email).stream()
            .filter(
                invitation ->
                    intent.providerInvitationId() == null
                        || intent
                            .providerInvitationId()
                            .equals(invitation.providerInvitationId()))
            .toList();
    if (providerMatches.size() != 1) {
      return Optional.empty();
    }
    ProviderInvitation provider = providerMatches.getFirst();
    ProvisioningIntent linked =
        intent.providerInvitationId() == null
            ? intents.save(
                intent.withProviderInvitation(
                    provider.providerInvitationId(), clock.instant()))
            : intent;
    return Optional.of(response(provider, expire(linked)));
  }

  private static boolean sameRequest(
      ProvisioningIntent intent,
      MemberInvitationRequest request,
      String idempotencyKey,
      String actorIssuer,
      String actorSubject) {
    return intent.requestedRole().equals(request.role())
        && intent.auditCorrelation().equals(idempotencyKey)
        && intent.invitedByIssuer().equals(actorIssuer)
        && intent.invitedBySubject().equals(actorSubject);
  }

  private static ProviderFailureReference providerFailureReference(RuntimeException failure) {
    if (failure instanceof KeycloakAdminException provider) {
      return new ProviderFailureReference(
          "provider-http", provider.operation(), provider.status());
    }
    if (failure instanceof IllegalStateException
        && "Keycloak invitation result was ambiguous".equals(failure.getMessage())) {
      return new ProviderFailureReference("provider-correlation", "invitation-inventory", 0);
    }
    if (failure instanceof IllegalStateException) {
      String message = failure.getMessage();
      if ("Keycloak returned an invalid invitation projection".equals(message)) {
        return new ProviderFailureReference(
            "provider-invitation-shape", "invitation-inventory", 0);
      }
      if ("Keycloak returned an invalid collection response".equals(message)) {
        return new ProviderFailureReference(
            "provider-collection-shape", "invitation-inventory", 0);
      }
      if ("Keycloak returned an invalid response".equals(message)) {
        return new ProviderFailureReference("provider-json-shape", "invitation-inventory", 0);
      }
    }
    for (Throwable current = failure; current != null; current = current.getCause()) {
      String type = current.getClass().getName();
      if (type.startsWith("org.springframework.security.oauth2.")) {
        return new ProviderFailureReference("oauth2-client", "invitation-create", 0);
      }
      if (type.startsWith("org.springframework.web.client.")
          || type.startsWith("java.net.")) {
        return new ProviderFailureReference("provider-transport", "invitation-create", 0);
      }
    }
    return new ProviderFailureReference("provider-projection", "invitation-inventory", 0);
  }

  private record ProviderFailureReference(String category, String operation, int status) {}

  private void apply(ProvisioningIntent intent, String subject) {
    if (intent.status() == ProvisioningIntentStatus.APPLIED) {
      return;
    }
    if (intent.expiresAt().isBefore(clock.instant())) {
      intents.save(intent.expired(clock.instant()));
      return;
    }
    try {
      keycloak.applyRole(subject, intent.requestedRole());
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
