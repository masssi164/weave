package com.massimotter.weave.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.massimotter.weave.backend.support.HumanJwtTestSupport;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class FilesWebDavSecurityConfigurationTest {

    @Test
    void directMemberProfileWinsWithoutConsultingWorkloadProfile() {
        Jwt member = token("member", "weave-app", "weave:workspace");
        JwtDecoder memberDecoder = mock(JwtDecoder.class);
        JwtDecoder workloadDecoder = mock(JwtDecoder.class);
        when(memberDecoder.decode("encoded")).thenReturn(member);

        Jwt decoded = new FilesWebDavSecurityConfiguration()
                .filesWebDavJwtDecoder(memberDecoder, workloadDecoder, HumanJwtTestSupport.organizationAdmission())
                .decode("encoded");

        assertThat(decoded).isSameAs(member);
        verify(memberDecoder).decode("encoded");
        verifyNoInteractions(workloadDecoder);
    }

    @ParameterizedTest
    @ValueSource(strings = {"foreign-id", "foreign-alias", "missing", "malformed", "tenant", "fallback"})
    void decodedHumanWithDeniedOrganizationNeverFallsThroughToWorkloadDecoder(String mismatch) {
        var claims = new HashMap<>(token("member", "weave-app", "weave:workspace").getClaims());
        switch (mismatch) {
            case "foreign-id" -> claims.put("organization", Map.of(HumanJwtTestSupport.ORGANIZATION_ALIAS,
                    Map.of("id", "foreign-org")));
            case "foreign-alias" -> claims.put("organization", Map.of("foreign-alias",
                    Map.of("id", HumanJwtTestSupport.ORGANIZATION_ID)));
            case "missing" -> claims.remove("organization");
            case "malformed" -> claims.put("organization", List.of("invalid"));
            case "tenant" -> claims.put("weave_tenant_id", "foreign-tenant");
            case "fallback" -> claims.put("tenant_id", "foreign-tenant");
            default -> throw new AssertionError(mismatch);
        }
        Jwt deniedMember = Jwt.withTokenValue("member").header("alg", "RS256")
                .claims(values -> values.putAll(claims)).build();
        JwtDecoder memberDecoder = mock(JwtDecoder.class);
        JwtDecoder workloadDecoder = mock(JwtDecoder.class);
        when(memberDecoder.decode("encoded")).thenReturn(deniedMember);

        assertThatThrownBy(() -> new FilesWebDavSecurityConfiguration()
                .filesWebDavJwtDecoder(memberDecoder, workloadDecoder, HumanJwtTestSupport.organizationAdmission())
                .decode("encoded"))
                .isInstanceOf(BadJwtException.class)
                .hasMessageContaining("deployment organization");
        verifyNoInteractions(workloadDecoder);
    }

    @Test
    void rejectedMemberMayEnterOnlyThroughTheValidatedWorkloadProfile() {
        Jwt workload = token("workload", "weave-mcp-server", "files.read");
        JwtDecoder memberDecoder = mock(JwtDecoder.class);
        JwtDecoder workloadDecoder = mock(JwtDecoder.class);
        when(memberDecoder.decode("encoded")).thenThrow(new BadJwtException("member rejected"));
        when(workloadDecoder.decode("encoded")).thenReturn(workload);

        Jwt decoded = new FilesWebDavSecurityConfiguration()
                .filesWebDavJwtDecoder(memberDecoder, workloadDecoder, HumanJwtTestSupport.organizationAdmission())
                .decode("encoded");

        assertThat(decoded).isSameAs(workload);
        verify(workloadDecoder).decode("encoded");
    }

    @Test
    void tokenRejectedByBothClosedProfilesFailsClosed() {
        JwtDecoder memberDecoder = mock(JwtDecoder.class);
        JwtDecoder workloadDecoder = mock(JwtDecoder.class);
        when(memberDecoder.decode("encoded")).thenThrow(new BadJwtException("member rejected"));
        when(workloadDecoder.decode("encoded")).thenThrow(new BadJwtException("workload rejected"));

        assertThatThrownBy(() -> new FilesWebDavSecurityConfiguration()
                .filesWebDavJwtDecoder(memberDecoder, workloadDecoder, HumanJwtTestSupport.organizationAdmission())
                .decode("encoded"))
                .isInstanceOf(BadJwtException.class)
                .hasMessageContaining("workload rejected");
    }

    private static Jwt token(String subject, String authorizedParty, String scope) {
        Instant now = Instant.now();
        var builder = Jwt.withTokenValue(subject)
                .header("alg", "RS256")
                .header("typ", "at+jwt")
                .issuer("https://auth.weave.test/realms/weave")
                .subject(subject)
                .audience(List.of("https://api.weave.test/api"))
                .claim("azp", authorizedParty)
                .claim("scope", scope)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60));
        if ("weave-app".equals(authorizedParty)) {
            builder.claim("organization", HumanJwtTestSupport.organizationWithRole("member"));
        }
        return builder.build();
    }
}
