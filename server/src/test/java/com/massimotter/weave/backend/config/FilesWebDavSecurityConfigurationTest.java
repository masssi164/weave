package com.massimotter.weave.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
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
                .filesWebDavJwtDecoder(memberDecoder, workloadDecoder)
                .decode("encoded");

        assertThat(decoded).isSameAs(member);
        verify(memberDecoder).decode("encoded");
        org.mockito.Mockito.verifyNoInteractions(workloadDecoder);
    }

    @Test
    void rejectedMemberMayEnterOnlyThroughTheValidatedWorkloadProfile() {
        Jwt workload = token("workload", "weave-mcp-server", "files.read");
        JwtDecoder memberDecoder = mock(JwtDecoder.class);
        JwtDecoder workloadDecoder = mock(JwtDecoder.class);
        when(memberDecoder.decode("encoded")).thenThrow(new BadJwtException("member rejected"));
        when(workloadDecoder.decode("encoded")).thenReturn(workload);

        Jwt decoded = new FilesWebDavSecurityConfiguration()
                .filesWebDavJwtDecoder(memberDecoder, workloadDecoder)
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
                .filesWebDavJwtDecoder(memberDecoder, workloadDecoder)
                .decode("encoded"))
                .isInstanceOf(BadJwtException.class)
                .hasMessageContaining("workload rejected");
    }

    private static Jwt token(String subject, String authorizedParty, String scope) {
        Instant now = Instant.now();
        return Jwt.withTokenValue(subject)
                .header("alg", "RS256")
                .header("typ", "at+jwt")
                .issuer("https://auth.weave.test/realms/weave")
                .subject(subject)
                .audience(List.of("https://api.weave.test/api"))
                .claim("azp", authorizedParty)
                .claim("scope", scope)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .build();
    }
}
