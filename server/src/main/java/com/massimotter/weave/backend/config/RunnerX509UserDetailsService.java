package com.massimotter.weave.backend.config;

import com.massimotter.weave.backend.runner.application.RunnerWorkloadIdentityDirectory;
import java.time.Clock;
import java.util.Objects;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/** Resolves a certificate fingerprint through the persisted Runner certificate directory. */
public final class RunnerX509UserDetailsService implements UserDetailsService {

    private final RunnerWorkloadIdentityDirectory identities;
    private final Clock clock;

    public RunnerX509UserDetailsService(
            RunnerWorkloadIdentityDirectory identities,
            Clock clock) {
        this.identities = Objects.requireNonNull(identities, "identities");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public UserDetails loadUserByUsername(String certificateFingerprint) {
        return identities.resolveActive(certificateFingerprint, clock.instant())
                .map(RunnerAuthenticatedPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Runner client certificate is not active."));
    }
}
