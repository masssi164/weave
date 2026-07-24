package com.massimotter.weave.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class AgentRuntimeAdminSecurityConfigurationTest {

    private final AgentRuntimeAdminSecurityConfiguration security =
            new AgentRuntimeAdminSecurityConfiguration();

    @Test
    void legacyRealmGroupsCannotGrantAdminAuthority() {
        Jwt jwt = token()
                .claim("scope", AgentRuntimeAdminSecurityConfiguration.ADMIN_SCOPE)
                .claim("groups", List.of("/weave/owners", "/weave/admins"))
                .build();

        assertThat(security.authorities(jwt))
                .extracting(Object::toString)
                .containsExactly(AgentRuntimeAdminSecurityConfiguration.ADMIN_AUTHORITY);
    }

    @Test
    void canonicalWeaveAppClientRoleGrantsAdminAuthority() {
        Jwt jwt = token()
                .claim("scope", AgentRuntimeAdminSecurityConfiguration.ADMIN_SCOPE)
                .claim("resource_access", Map.of(
                        "weave-app",
                        Map.of("roles", List.of("admin"))))
                .build();

        assertThat(security.authorities(jwt))
                .extracting(Object::toString)
                .containsExactlyInAnyOrder(
                        AgentRuntimeAdminSecurityConfiguration.ADMIN_AUTHORITY,
                        AgentRuntimeAdminSecurityConfiguration.ADMIN_ROLE_AUTHORITY);
    }

    private Jwt.Builder token() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("admin-1")
                .issuer("https://auth.weave.test/realms/weave");
    }
}
