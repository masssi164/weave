package com.massimotter.weave.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.config.IdentityInvitationProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.identity.invitation.KeycloakIdentityAdminClient;
import com.massimotter.weave.backend.identity.invitation.KeycloakIdentityAdminClient.ProviderInvitation;
import com.massimotter.weave.backend.identity.invitation.ProvisioningIntent;
import com.massimotter.weave.backend.identity.invitation.ProvisioningIntentRepository;
import com.massimotter.weave.backend.model.identity.MemberInvitationRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.jwt.Jwt;

class MemberInvitationServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-22T10:00:00Z");

    private ProvisioningIntentRepository intents;
    private KeycloakIdentityAdminClient keycloak;
    private AuditEventPublisher audit;
    private MemberInvitationService service;

    @BeforeEach
    void setUp() {
        intents = mock(ProvisioningIntentRepository.class);
        keycloak = mock(KeycloakIdentityAdminClient.class);
        audit = mock(AuditEventPublisher.class);
        service = new MemberInvitationService(
                intents,
                keycloak,
                new IdentityInvitationProperties(),
                audit,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void derivesTheExactCanonicalHumanGroupFromTheRequestedRole() {
        when(intents.findPendingByEmail("weave-dogfood", "org-1", "member@example.invalid"))
                .thenReturn(List.of());
        when(intents.save(any(ProvisioningIntent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(keycloak.issue("org-1", "member@example.invalid", "Member Example"))
                .thenReturn(new ProviderInvitation(
                        "invitation-1",
                        "member@example.invalid",
                        "Member Example",
                        "pending",
                        NOW.plusSeconds(3600),
                        NOW));

        var response = service.create(
                "org-1",
                new MemberInvitationRequest(
                        "Member@Example.invalid",
                        "Member Example",
                        "member",
                        List.of()),
                "invite-once",
                adminJwt());

        assertThat(response.organizationGroups()).containsExactly("/weave/members");
        ArgumentCaptor<ProvisioningIntent> saved = ArgumentCaptor.forClass(ProvisioningIntent.class);
        verify(intents, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        assertThat(saved.getAllValues()).allSatisfy(intent ->
                assertThat(intent.organizationGroups()).containsExactly("/weave/members"));
    }

    @Test
    void rejectsRoleGroupEscalationBeforeCreatingProviderOrWorkState() {
        assertThatThrownBy(() -> service.create(
                "org-1",
                new MemberInvitationRequest(
                        "member@example.invalid",
                        null,
                        "member",
                        List.of("/weave/owners")),
                "invite-escalation",
                adminJwt()))
                .isInstanceOfSatisfying(ApiErrorException.class, error -> {
                    assertThat(error.status().value()).isEqualTo(400);
                    assertThat(error.code()).isEqualTo("member-invitation-groups-invalid");
                    assertThat(error.getMessage()).doesNotContain("/weave/owners");
                });

        verifyNoInteractions(intents, keycloak, audit);
    }

    private Jwt adminJwt() {
        return Jwt.withTokenValue("admin-token")
                .header("alg", "none")
                .subject("admin-1")
                .issuer("https://auth.example.invalid/realms/weave")
                .claim("weave_tenant", "weave-dogfood")
                .claim("resource_access", Map.of("weave-app", Map.of("roles", List.of("admin"))))
                .build();
    }
}
