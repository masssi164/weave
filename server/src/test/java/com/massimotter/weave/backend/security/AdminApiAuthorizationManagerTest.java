package com.massimotter.weave.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

class AdminApiAuthorizationManagerTest {

    private final AdminApiAuthorizationManager manager = new AdminApiAuthorizationManager();
    private final RequestAuthorizationContext request = new RequestAuthorizationContext(new MockHttpServletRequest());

    @Test
    void admitsOneCurrentSelectedOrganizationOwnerOrAdmin() {
        assertThat(allowed(List.of("owner"), true)).isTrue();
        assertThat(allowed(List.of("admin"), true)).isTrue();
    }

    @Test
    void rejectsMembersGuestsAmbiguousRolesAndMissingScope() {
        assertThat(allowed(List.of("member"), true)).isFalse();
        assertThat(allowed(List.of("guest"), true)).isFalse();
        assertThat(allowed(List.of("admin", "member"), true)).isFalse();
        assertThat(allowed(List.of("admin"), false)).isFalse();
    }

    @Test
    void rejectsTopLevelRolesWithoutSelectedOrganization() {
        Jwt token = Jwt.withTokenValue("test")
                .header("alg", "none")
                .subject("user-123")
                .claim("realm_access", Map.of("roles", List.of("admin")))
                .build();
        assertThat(manager.authorize(() -> new JwtAuthenticationToken(token,
                List.of(new SimpleGrantedAuthority("SCOPE_weave:workspace"))), request).isGranted()).isFalse();
    }

    private boolean allowed(List<String> roles, boolean scope) {
        Jwt token = Jwt.withTokenValue("test")
                .header("alg", "none")
                .subject("user-123")
                .claim("organization", Map.of("selected", Map.of(
                        "resource_access", Map.of("weave-app", Map.of("roles", roles)))))
                .build();
        var authorities = scope
                ? List.of(new SimpleGrantedAuthority("SCOPE_weave:workspace"))
                : List.<SimpleGrantedAuthority>of();
        return manager.authorize(() -> new JwtAuthenticationToken(token, authorities), request).isGranted();
    }
}
