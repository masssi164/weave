package com.massimotter.weave.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.massimotter.weave.backend.runner.application.RunnerWorkloadIdentity;
import com.massimotter.weave.backend.runner.application.RunnerWorkloadIdentityDirectory;
import com.massimotter.weave.backend.runner.domain.RunnerControl.RunnerId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

class RunnerX509UserDetailsServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-28T18:00:00Z");
    private static final String FINGERPRINT =
            "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @Test
    void activeCertificateResolvesToRunnerPrincipalWithoutPasswordAuthority() {
        RunnerWorkloadIdentityDirectory directory = mock(RunnerWorkloadIdentityDirectory.class);
        RunnerWorkloadIdentity identity = identity();
        when(directory.resolveActive(FINGERPRINT, NOW)).thenReturn(Optional.of(identity));

        RunnerAuthenticatedPrincipal principal = (RunnerAuthenticatedPrincipal)
                new RunnerX509UserDetailsService(directory, fixedClock())
                        .loadUserByUsername(FINGERPRINT);

        assertThat(principal.identity()).isEqualTo(identity);
        assertThat(principal.getUsername()).isEqualTo("runner_security_01");
        assertThat(principal.getPassword()).isEmpty();
        assertThat(principal.getAuthorities())
                .extracting(Object::toString)
                .containsExactly(RunnerAuthenticatedPrincipal.AUTHORITY);
    }

    @Test
    void unknownRevokedOrExpiredFingerprintFailsClosed() {
        RunnerWorkloadIdentityDirectory directory = mock(RunnerWorkloadIdentityDirectory.class);
        when(directory.resolveActive(FINGERPRINT, NOW)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new RunnerX509UserDetailsService(directory, fixedClock())
                        .loadUserByUsername(FINGERPRINT))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("Runner client certificate is not active.");
    }

    private static RunnerWorkloadIdentity identity() {
        return new RunnerWorkloadIdentity(
                new RunnerId("runner_security_01"),
                "org:security-test",
                FINGERPRINT,
                NOW.minusSeconds(60),
                NOW.plusSeconds(3600));
    }

    private static Clock fixedClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }
}
