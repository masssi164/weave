package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.InMemoryAuditEventPublisher;
import com.massimotter.weave.backend.config.WeaveSecurityProperties;
import com.massimotter.weave.backend.config.WorkspaceCapabilityProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.model.admin.CapabilityWhitelistUpdateRequest;
import com.massimotter.weave.backend.provider.InMemoryProviderSelectionRepository;
import com.massimotter.weave.backend.provider.ProviderRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class AdminControlPlaneServiceTest {

    @Test
    void providerAndPolicyMutationsRequireEffectiveOwnerOrAdminPolicyServerSide() {
        InMemoryAuditEventPublisher auditPublisher = new InMemoryAuditEventPublisher();
        AdminControlPlaneService service = new AdminControlPlaneService(
                mock(ProviderRegistry.class),
                workspaceCapabilityService(),
                new InMemoryProviderSelectionRepository(),
                new InMemoryOrganizationBootstrapRepository(),
                auditPublisher,
                Clock.fixed(Instant.parse("2026-05-27T01:03:39Z"), ZoneOffset.UTC));
        CapabilityWhitelistUpdateRequest request = new CapabilityWhitelistUpdateRequest(
                "workspace-admin",
                List.of("admin.policy.edit", "admin.provider.configure"),
                "maintain recovery admin policy");

        assertThatThrownBy(() -> service.updateWhitelist(request, jwt("operator")))
                .isInstanceOfSatisfying(ApiErrorException.class, exception -> {
                    assertThat(exception.status().value()).isEqualTo(403);
                    assertThat(exception.code()).isEqualTo("capability-policy-blocked");
                    assertThat(exception.details()).containsEntry("requiredCapability", "admin.policy.edit");
                    assertThat(exception.details()).containsEntry("diagnosticsRedacted", true);
                });
        assertThatThrownBy(() -> service.updateWhitelist(request, jwt("member")))
                .isInstanceOfSatisfying(ApiErrorException.class, exception -> {
                    assertThat(exception.status().value()).isEqualTo(403);
                    assertThat(exception.code()).isEqualTo("capability-policy-blocked");
                });

        var response = service.updateWhitelist(request, jwt("admin"));

        assertThat(response.denyByDefault()).isTrue();
        assertThat(auditPublisher.events())
                .extracting(event -> event.action())
                .containsExactly(AuditAction.ADMIN_POLICY_UPDATED);
        assertThat(auditPublisher.events().get(0).payload())
                .containsEntry("profileKey", "workspace-admin")
                .containsEntry("denyByDefault", true);
    }

    private WorkspaceCapabilityService workspaceCapabilityService() {
        OAuth2ResourceServerProperties properties = new OAuth2ResourceServerProperties();
        properties.getJwt().setIssuerUri("https://auth.weave.local/realms/weave");
        return new WorkspaceCapabilityService(
                properties,
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new WorkspaceCapabilityProperties(null, null, null, null, null, null));
    }

    private Jwt jwt(String role) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(role + "-123")
                .issuer("https://auth.example.invalid/realms/acme")
                .claim("weave_tenant", "weave-dogfood")
                .claim("realm_access", Map.of("roles", List.of(role)))
                .claim("groups", List.of())
                .build();
    }
}
