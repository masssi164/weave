package com.massimotter.weave.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.massimotter.weave.backend.support.HumanJwtTestSupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

class WorkspaceAccessAuthorizationManagerTest {

    private final WorkspaceAccessAuthorizationManager manager =
            new WorkspaceAccessAuthorizationManager();

    @Test
    void acceptsExactScopeAndOneSelectedOrganizationProductRole() {
        assertThat(authorize(
                        human(List.of("member"), true),
                        "/api/profile/readiness"))
                .isTrue();
    }

    @Test
    void rejectsMissingScopeMissingRoleMultipleProductRolesAndTopLevelFallback() {
        assertThat(authorize(human(List.of("member"), false), "/api/profile/readiness"))
                .isFalse();
        assertThat(authorize(human(List.of(), true), "/api/profile/readiness")).isFalse();
        assertThat(authorize(
                        human(List.of("owner", "admin"), true),
                        "/api/profile/readiness"))
                .isFalse();
        assertThat(authorize(topLevelRole(), "/api/profile/readiness")).isFalse();
    }

    @Test
    void acceptsDeviceCredentialOnlyOnDavProtocolRoutes() {
        JwtAuthenticationToken device = deviceCredential();

        assertThat(authorize(device, "/dav/files")).isTrue();
        assertThat(authorize(device, "/dav/files/report.txt")).isTrue();
        assertThat(authorize(device, "/caldav/user/calendar")).isTrue();
        assertThat(authorize(device, "/api/profile/readiness")).isFalse();
        assertThat(authorize(device, "/_matrix/client/v3/sync")).isFalse();
        assertThat(authorize(device, "/dav/files-escape")).isFalse();
    }

    private boolean authorize(JwtAuthenticationToken authentication, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(path);
        return manager.authorize(
                        () -> authentication,
                        new RequestAuthorizationContext(request))
                .isGranted();
    }

    private JwtAuthenticationToken human(List<String> roles, boolean scope) {
        Jwt jwt = token()
                .claim("organization", HumanJwtTestSupport.organizationWithRoles(roles))
                .build();
        return new JwtAuthenticationToken(
                jwt,
                scope
                        ? List.of(new SimpleGrantedAuthority("SCOPE_weave:workspace"))
                        : List.of());
    }

    private JwtAuthenticationToken topLevelRole() {
        Jwt jwt = token()
                .claim(
                        "resource_access",
                        Map.of("weave-app", Map.of("roles", List.of("member"))))
                .build();
        return new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority("SCOPE_weave:workspace")));
    }

    private JwtAuthenticationToken deviceCredential() {
        Jwt jwt = token().claim("weave_auth_method", "device_credential").build();
        return new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority("SCOPE_weave:workspace")));
    }

    private Jwt.Builder token() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("person-1")
                .issuer("https://auth.weave.test/realms/weave");
    }
}
