package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditEvent;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.audit.AuditRedactionLevel;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.identity.IdentityAdminOperationStore;
import com.massimotter.weave.backend.identity.IdentityOpaqueReferenceCodec;
import com.massimotter.weave.backend.identity.invitation.KeycloakIdentityAdminClient;
import com.massimotter.weave.backend.identity.invitation.KeycloakIdentityAdminClient.ProviderMember;
import com.massimotter.weave.backend.model.identity.MemberLifecycleOperationResponse;
import com.massimotter.weave.backend.model.identity.OrganizationMemberPageResponse;
import com.massimotter.weave.backend.model.identity.OrganizationMemberResponse;
import com.massimotter.weave.backend.model.identity.OrganizationMemberUpdateRequest;
import com.massimotter.weave.backend.model.identity.WeaverEntitlementUpdateRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class OrganizationMemberAdministrationService {
  private static final String AGENT_RUNTIME_CAPABILITY = "agent-runtime.entitled";

  private final KeycloakIdentityAdminClient keycloak;
  private final IdentityOpaqueReferenceCodec references;
  private final IdentityAdminOperationStore operations;
  private final AuditEventPublisher audit;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  @Autowired
  public OrganizationMemberAdministrationService(
      KeycloakIdentityAdminClient keycloak,
      IdentityOpaqueReferenceCodec references,
      IdentityAdminOperationStore operations,
      AuditEventPublisher audit,
      ObjectMapper objectMapper) {
    this(keycloak, references, operations, audit, objectMapper, Clock.systemUTC());
  }

  OrganizationMemberAdministrationService(
      KeycloakIdentityAdminClient keycloak,
      IdentityOpaqueReferenceCodec references,
      IdentityAdminOperationStore operations,
      AuditEventPublisher audit,
      ObjectMapper objectMapper,
      Clock clock) {
    this.keycloak = keycloak;
    this.references = references;
    this.operations = operations;
    this.audit = audit;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  public OrganizationMemberPageResponse list(
      String organizationId, String cursor, int requestedSize, Jwt jwt) {
    requireOrganization(organizationId, jwt);
    String keycloakOrganizationId = keycloak.configuredOrganizationId();
    List<ProviderMember> members = keycloak.members(keycloakOrganizationId);
    int size = Math.max(1, Math.min(requestedSize, 100));
    int start = 0;
    if (cursor != null && !cursor.isBlank()) {
      start =
          java.util.stream.IntStream.range(0, members.size())
              .filter(
                  index ->
                      references
                          .cursor(organizationId, members.get(index).subject())
                          .equals(cursor))
              .findFirst()
              .orElseThrow(this::invalidCursor)
              + 1;
    }
    int end = Math.min(start + size, members.size());
    List<OrganizationMemberResponse> page =
        members.subList(start, end).stream()
            .map(member -> response(organizationId, member))
            .toList();
    String next =
        end < members.size() && end > start
            ? references.cursor(organizationId, members.get(end - 1).subject())
            : null;
    return new OrganizationMemberPageResponse(page, next);
  }

  public OrganizationMemberResponse get(
      String organizationId, String memberHandle, Jwt jwt) {
    requireOrganization(organizationId, jwt);
    String keycloakOrganizationId = keycloak.configuredOrganizationId();
    return response(
        organizationId,
        resolveMember(keycloakOrganizationId, organizationId, memberHandle));
  }

  public synchronized OrganizationMemberResponse update(
      String organizationId,
      String memberHandle,
      OrganizationMemberUpdateRequest request,
      String expectedVersion,
      String idempotencyKey,
      Jwt jwt) {
    OrganizationIdentityContext actor = requireOrganization(organizationId, jwt);
    String keycloakOrganizationId = keycloak.configuredOrganizationId();
    ProviderMember current =
        resolveMember(keycloakOrganizationId, organizationId, memberHandle);
    requireVersion(organizationId, current, expectedVersion);
    requireLastOwnerSafe(keycloakOrganizationId, current, request.role(), request.enabled());
    String requestHash =
        sha256(
            "update\u001f"
                + memberHandle
                + '\u001f'
                + request.role()
                + '\u001f'
                + request.enabled()
                + '\u001f'
                + normalizeVersion(expectedVersion));
    var replay =
        operations.claim(
            organizationId, idempotencyKey, "member-access-update", requestHash);
    if (replay.isPresent()) {
      return read(replay.get(), OrganizationMemberResponse.class);
    }

    ProviderMember updated =
        keycloak.updateMember(
            keycloakOrganizationId,
            current.subject(),
            request.role(),
            request.enabled());
    OrganizationMemberResponse response = response(organizationId, updated);
    publish(
        actor,
        memberHandle,
        AuditAction.MEMBER_ACCESS_UPDATED,
        idempotencyKey,
        Map.of(
            "role", response.role(),
            "enabled", response.enabled()));
    operations.complete(organizationId, idempotencyKey, write(response));
    return response;
  }

  public synchronized OrganizationMemberResponse updateWeaverEntitlement(
      String organizationId,
      String memberHandle,
      WeaverEntitlementUpdateRequest request,
      String expectedVersion,
      String idempotencyKey,
      Jwt jwt) {
    OrganizationIdentityContext actor = requireOrganization(organizationId, jwt);
    String keycloakOrganizationId = keycloak.configuredOrganizationId();
    ProviderMember current =
        resolveMember(keycloakOrganizationId, organizationId, memberHandle);
    requireVersion(organizationId, current, expectedVersion);
    String requestHash =
        sha256(
            "weaver-entitlement\u001f"
                + memberHandle
                + '\u001f'
                + request.entitled()
                + '\u001f'
                + normalizeVersion(expectedVersion));
    var replay =
        operations.claim(
            organizationId, idempotencyKey, "weaver-entitlement-update", requestHash);
    if (replay.isPresent()) {
      return read(replay.get(), OrganizationMemberResponse.class);
    }

    ProviderMember updated =
        keycloak.setWeaverEntitlement(
            keycloakOrganizationId, current.subject(), request.entitled());
    OrganizationMemberResponse response = response(organizationId, updated);
    publish(
        actor,
        memberHandle,
        AuditAction.MEMBER_ACCESS_UPDATED,
        idempotencyKey,
        Map.of(
            "capability", AGENT_RUNTIME_CAPABILITY,
            "organizationGroupPath", "/capabilities/weaver",
            "entitled", request.entitled()));
    operations.complete(organizationId, idempotencyKey, write(response));
    return response;
  }

  public synchronized MemberLifecycleOperationResponse revokeSessions(
      String organizationId,
      String memberHandle,
      String expectedVersion,
      String idempotencyKey,
      Jwt jwt) {
    OrganizationIdentityContext actor = requireOrganization(organizationId, jwt);
    String keycloakOrganizationId = keycloak.configuredOrganizationId();
    ProviderMember current =
        resolveMember(keycloakOrganizationId, organizationId, memberHandle);
    requireVersion(organizationId, current, expectedVersion);
    String requestHash =
        sha256(
            "session-revoke\u001f"
                + memberHandle
                + '\u001f'
                + normalizeVersion(expectedVersion));
    var replay =
        operations.claim(
            organizationId, idempotencyKey, "member-session-revocation", requestHash);
    if (replay.isPresent()) {
      return read(replay.get(), MemberLifecycleOperationResponse.class);
    }
    keycloak.revokeSessions(keycloakOrganizationId, current.subject());
    MemberLifecycleOperationResponse response =
        new MemberLifecycleOperationResponse(memberHandle, "sessions-revoked");
    publish(
        actor,
        memberHandle,
        AuditAction.MEMBER_SESSIONS_REVOKED,
        idempotencyKey,
        Map.of("outcome", response.outcome()));
    operations.complete(organizationId, idempotencyKey, write(response));
    return response;
  }

  public synchronized MemberLifecycleOperationResponse offboard(
      String organizationId,
      String memberHandle,
      String expectedVersion,
      String idempotencyKey,
      Jwt jwt) {
    OrganizationIdentityContext actor = requireOrganization(organizationId, jwt);
    String keycloakOrganizationId = keycloak.configuredOrganizationId();
    ProviderMember current =
        resolveMember(keycloakOrganizationId, organizationId, memberHandle);
    requireVersion(organizationId, current, expectedVersion);
    requireLastOwnerSafe(keycloakOrganizationId, current, "guest", false);
    String requestHash =
        sha256(
            "offboard\u001f" + memberHandle + '\u001f' + normalizeVersion(expectedVersion));
    var replay =
        operations.claim(organizationId, idempotencyKey, "member-offboarding", requestHash);
    if (replay.isPresent()) {
      return read(replay.get(), MemberLifecycleOperationResponse.class);
    }
    keycloak.offboard(keycloakOrganizationId, current.subject());
    MemberLifecycleOperationResponse response =
        new MemberLifecycleOperationResponse(memberHandle, "offboarded-and-disabled");
    publish(
        actor,
        memberHandle,
        AuditAction.MEMBER_OFFBOARDED,
        idempotencyKey,
        Map.of("outcome", response.outcome(), "hardDelete", false));
    operations.complete(organizationId, idempotencyKey, write(response));
    return response;
  }

  private OrganizationIdentityContext requireOrganization(String organizationId, Jwt jwt) {
    OrganizationIdentityContext actor = OrganizationIdentityContextFactory.fromJwt(jwt);
    if (organizationId == null
        || organizationId.isBlank()
        || !organizationId.equals(actor.organizationId())) {
      throw new ApiErrorException(
          HttpStatus.FORBIDDEN,
          "organization-context-mismatch",
          "The requested organization does not match the authenticated organization.",
          Map.of());
    }
    return actor;
  }

  private ProviderMember resolveMember(
      String keycloakOrganizationId, String organizationId, String memberHandle) {
    List<ProviderMember> matches =
        keycloak.members(keycloakOrganizationId).stream()
            .filter(
                member ->
                    references.member(organizationId, member.subject()).equals(memberHandle))
            .toList();
    if (matches.size() != 1) {
      throw new ApiErrorException(
          HttpStatus.NOT_FOUND,
          "organization-member-not-found",
          "The organization member is unavailable.",
          Map.of());
    }
    return matches.getFirst();
  }

  private OrganizationMemberResponse response(
      String organizationId, ProviderMember member) {
    String role = member.roles().size() == 1 ? member.roles().getFirst() : "invalid";
    return new OrganizationMemberResponse(
        references.member(organizationId, member.subject()),
        member.email(),
        member.displayName(),
        role,
        member.capabilities(),
        member.enabled(),
        version(member));
  }

  private void requireVersion(
      String organizationId, ProviderMember member, String expectedVersion) {
    if (!version(member).equals(normalizeVersion(expectedVersion))) {
      throw new ApiErrorException(
          HttpStatus.PRECONDITION_FAILED,
          "organization-member-version-mismatch",
          "The member changed after it was read. Refresh before retrying.",
          Map.of(
              "memberHandle", references.member(organizationId, member.subject())));
    }
  }

  private void requireLastOwnerSafe(
      String organizationId, ProviderMember target, String requestedRole, boolean enabled) {
    if (!target.roles().contains("owner")
        || ("owner".equals(requestedRole) && enabled)) {
      return;
    }
    long enabledOwners =
        keycloak.members(organizationId).stream()
            .filter(ProviderMember::enabled)
            .filter(member -> member.roles().contains("owner"))
            .count();
    if (enabledOwners <= 1) {
      throw new ApiErrorException(
          HttpStatus.CONFLICT,
          "last-owner-protected",
          "The last enabled organization owner cannot be removed or suspended.",
          Map.of());
    }
  }

  private String version(ProviderMember member) {
    return "v1_" + sha256(
            String.join(
                "\u001f",
                member.subject(),
                member.email(),
                member.displayName(),
                member.roles().toString(),
                member.capabilities().toString(),
                Boolean.toString(member.enabled())))
        .substring(0, 24);
  }

  private String normalizeVersion(String value) {
    if (value == null || value.isBlank()) {
      throw new ApiErrorException(
          HttpStatus.PRECONDITION_REQUIRED,
          "organization-member-version-required",
          "If-Match is required for member lifecycle changes.",
          Map.of());
    }
    String normalized = value.trim();
    if (normalized.startsWith("\"") && normalized.endsWith("\"") && normalized.length() > 1) {
      return normalized.substring(1, normalized.length() - 1);
    }
    return normalized;
  }

  private ApiErrorException invalidCursor() {
    return new ApiErrorException(
        HttpStatus.BAD_REQUEST,
        "organization-member-cursor-invalid",
        "The member cursor is invalid or stale.",
        Map.of());
  }

  private void publish(
      OrganizationIdentityContext actor,
      String memberHandle,
      AuditAction action,
      String idempotencyKey,
      Map<String, Object> payload) {
    Map<String, Object> supportSafePayload = new LinkedHashMap<>();
    supportSafePayload.put("memberHandle", memberHandle);
    supportSafePayload.putAll(payload);
    audit.publish(
        new AuditEvent(
            actor.organizationId(),
            null,
            actor.primaryIdentityKey(),
            "admin-console",
            action,
            clock.instant(),
            idempotencyKey,
            AuditRedactionLevel.SUPPORT_SAFE,
            Map.copyOf(supportSafePayload)));
  }

  private String write(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Identity operation response cannot be persisted", exception);
    }
  }

  private <T> T read(String value, Class<T> type) {
    try {
      return objectMapper.readValue(value, type);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Identity operation response is invalid", exception);
    }
  }

  private String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }
}
