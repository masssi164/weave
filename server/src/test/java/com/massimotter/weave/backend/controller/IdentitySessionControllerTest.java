package com.massimotter.weave.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.massimotter.weave.backend.model.identity.IdentitySessionReconcileResponse;
import com.massimotter.weave.backend.service.MemberInvitationService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.CacheControl;
import org.springframework.security.oauth2.jwt.Jwt;

class IdentitySessionControllerTest {

    @Test
    void reportsAccessUpdateAndPreventsCaching() {
        MemberInvitationService invitations = mock(MemberInvitationService.class);
        Jwt jwt = memberJwt();
        when(invitations.reconcileAuthenticated(jwt)).thenReturn(true);

        var response = new IdentitySessionController(invitations).reconcile(jwt);

        assertThat(response.getBody())
                .isEqualTo(IdentitySessionReconcileResponse.accessUpdated());
        assertThat(response.getHeaders().getCacheControl())
                .isEqualTo(CacheControl.noStore().getHeaderValue());
    }

    @Test
    void reportsUnchangedWithoutInventingAccess() {
        MemberInvitationService invitations = mock(MemberInvitationService.class);
        Jwt jwt = memberJwt();
        when(invitations.reconcileAuthenticated(jwt)).thenReturn(false);

        var response = new IdentitySessionController(invitations).reconcile(jwt);

        assertThat(response.getBody())
                .isEqualTo(IdentitySessionReconcileResponse.unchanged());
    }

    private Jwt memberJwt() {
        return Jwt.withTokenValue("member-token")
                .header("alg", "none")
                .subject("member-1")
                .issuer("https://auth.example.invalid/realms/weave")
                .claim("weave_tenant", "weave-dogfood")
                .claim("email", "member@example.invalid")
                .claim("email_verified", true)
                .claim(
                        "resource_access",
                        Map.of("weave-app", Map.of("roles", List.of())))
                .build();
    }
}
