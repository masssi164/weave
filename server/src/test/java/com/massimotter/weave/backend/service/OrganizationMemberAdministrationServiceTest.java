package com.massimotter.weave.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.massimotter.weave.backend.audit.InMemoryAuditEventPublisher;
import com.massimotter.weave.backend.config.IdentityInvitationProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.identity.IdentityAdminOperationStore;
import com.massimotter.weave.backend.identity.IdentityOpaqueReferenceCodec;
import com.massimotter.weave.backend.identity.invitation.KeycloakIdentityAdminClient;
import com.massimotter.weave.backend.identity.invitation.KeycloakIdentityAdminClient.ProviderMember;
import com.massimotter.weave.backend.model.identity.OrganizationMemberResponse;
import com.massimotter.weave.backend.model.identity.OrganizationMemberUpdateRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.oauth2.jwt.Jwt;
import tools.jackson.databind.json.JsonMapper;

class OrganizationMemberAdministrationServiceTest {

  private static final String ORGANIZATION_ID = "acme";
  private static final String KEYCLOAK_ORGANIZATION_ID = "kc-org-42";

  @TempDir Path tempDirectory;

  private KeycloakIdentityAdminClient keycloak;
  private IdentityAdminOperationStore operations;
  private IdentityOpaqueReferenceCodec references;
  private InMemoryAuditEventPublisher audit;
  private OrganizationMemberAdministrationService service;

  @BeforeEach
  void setUp() throws Exception {
    Path secret = tempDirectory.resolve("identity-reference.secret");
    Files.writeString(secret, "0123456789abcdef0123456789abcdef");
    IdentityInvitationProperties properties = new IdentityInvitationProperties();
    properties.keycloak().setOrganizationId(KEYCLOAK_ORGANIZATION_ID);
    properties.keycloak().setReferenceHmacSecretFile(secret.toString());
    references = new IdentityOpaqueReferenceCodec(properties);
    keycloak = org.mockito.Mockito.mock(KeycloakIdentityAdminClient.class);
    operations = org.mockito.Mockito.mock(IdentityAdminOperationStore.class);
    audit = new InMemoryAuditEventPublisher();
    when(keycloak.configuredOrganizationId()).thenReturn(KEYCLOAK_ORGANIZATION_ID);
    service =
        new OrganizationMemberAdministrationService(
            keycloak,
            references,
            operations,
            audit,
            JsonMapper.builder().findAndAddModules().build(),
            Clock.fixed(Instant.parse("2026-07-26T12:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void listUsesCanonicalOrganizationBoundOpaqueHandlesAndNeverReturnsSubjects() {
    when(keycloak.members(KEYCLOAK_ORGANIZATION_ID))
        .thenReturn(
            List.of(
                member("subject-alice", "alice@example.test", "Alice", "member", true),
                member("subject-owner", "owner@example.test", "Owner", "owner", true)));

    var page = service.list(ORGANIZATION_ID, null, 1, jwt("admin"));

    assertThat(page.items()).hasSize(1);
    assertThat(page.items().getFirst().memberHandle())
        .isEqualTo(references.member(ORGANIZATION_ID, "subject-alice"))
        .doesNotContain("subject-alice", KEYCLOAK_ORGANIZATION_ID);
    assertThat(page.nextCursor())
        .isEqualTo(references.cursor(ORGANIZATION_ID, "subject-alice"))
        .doesNotContain("subject-alice", KEYCLOAK_ORGANIZATION_ID);
    assertThat(page.toString()).doesNotContain("subject-alice", "kc-org-42");
  }

  @Test
  void lastEnabledOwnerCannotBeDemotedOrDisabled() {
    ProviderMember owner =
        member("subject-owner", "owner@example.test", "Owner", "owner", true);
    when(keycloak.members(KEYCLOAK_ORGANIZATION_ID)).thenReturn(List.of(owner));
    OrganizationMemberResponse current =
        service.get(
            ORGANIZATION_ID,
            references.member(ORGANIZATION_ID, owner.subject()),
            jwt("admin"));

    assertThatThrownBy(
            () ->
                service.update(
                    ORGANIZATION_ID,
                    current.memberHandle(),
                    new OrganizationMemberUpdateRequest("member", false, true),
                    current.version(),
                    "idempotency-owner-demotion-0001",
                    jwt("admin")))
        .isInstanceOfSatisfying(
            ApiErrorException.class,
            exception -> {
              assertThat(exception.status().value()).isEqualTo(409);
              assertThat(exception.code()).isEqualTo("last-owner-protected");
            });

    verify(keycloak, never())
        .updateMember(anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyBoolean());
  }

  @Test
  void accessUpdateIsIdempotentAuditedAndUsesOnlyCanonicalCapabilities() {
    ProviderMember target =
        member("subject-member", "member@example.test", "Member", "member", true);
    ProviderMember owner =
        member("subject-owner", "owner@example.test", "Owner", "owner", true);
    ProviderMember updated =
        new ProviderMember(
            target.subject(),
            target.email(),
            target.displayName(),
            List.of("admin"),
            List.of("agent-runtime.entitled"),
            true);
    when(keycloak.members(KEYCLOAK_ORGANIZATION_ID)).thenReturn(List.of(target, owner));
    when(keycloak.updateMember(
            KEYCLOAK_ORGANIZATION_ID,
            target.subject(),
            "admin",
            List.of("agent-runtime.entitled"),
            true))
        .thenReturn(updated);
    when(operations.claim(
            org.mockito.ArgumentMatchers.eq(ORGANIZATION_ID),
            org.mockito.ArgumentMatchers.eq("idempotency-access-update-0001"),
            org.mockito.ArgumentMatchers.eq("member-access-update"),
            org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(Optional.empty());
    OrganizationMemberResponse current =
        service.get(
            ORGANIZATION_ID,
            references.member(ORGANIZATION_ID, target.subject()),
            jwt("owner"));

    OrganizationMemberResponse response =
        service.update(
            ORGANIZATION_ID,
            current.memberHandle(),
            new OrganizationMemberUpdateRequest("admin", true, true),
            '"' + current.version() + '"',
            "idempotency-access-update-0001",
            jwt("owner"));

    assertThat(response.role()).isEqualTo("admin");
    assertThat(response.capabilities()).containsExactly("agent-runtime.entitled");
    assertThat(audit.events()).singleElement().satisfies(
        event -> {
          assertThat(event.action().wireName()).isEqualTo("identity.member_access.updated");
          assertThat(event.payload())
              .containsEntry("memberHandle", current.memberHandle())
              .containsEntry("agentRuntimeEntitled", true);
          assertThat(event.toString()).doesNotContain(target.subject(), target.email());
        });
    verify(operations)
        .complete(
            org.mockito.ArgumentMatchers.eq(ORGANIZATION_ID),
            org.mockito.ArgumentMatchers.eq("idempotency-access-update-0001"),
            org.mockito.ArgumentMatchers.contains("\"role\":\"admin\""));
  }

  @Test
  void canonicalOrganizationMismatchFailsBeforeCallingKeycloak() {
    assertThatThrownBy(() -> service.list("other-org", null, 50, jwt("admin")))
        .isInstanceOfSatisfying(
            ApiErrorException.class,
            exception -> {
              assertThat(exception.status().value()).isEqualTo(403);
              assertThat(exception.code()).isEqualTo("organization-context-mismatch");
            });

    verify(keycloak, never()).members(anyString());
  }

  private ProviderMember member(
      String subject, String email, String displayName, String role, boolean enabled) {
    return new ProviderMember(
        subject, email, displayName, List.of(role), List.of(), enabled);
  }

  private Jwt jwt(String role) {
    return Jwt.withTokenValue("test-token")
        .header("alg", "none")
        .issuer("https://auth.example.test/realms/weave")
        .subject(role + "-subject")
        .claim("weave_tenant", ORGANIZATION_ID)
        .claim("resource_access", Map.of("weave-app", Map.of("roles", List.of(role))))
        .build();
  }
}
