package com.massimotter.weave.backend.config;

import com.massimotter.weave.backend.runner.application.RunnerWorkloadIdentity;
import java.io.Serial;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** Security principal whose identity was resolved from a trusted Runner client certificate. */
public record RunnerAuthenticatedPrincipal(RunnerWorkloadIdentity identity)
        implements UserDetails {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String AUTHORITY = "RUNNER_CONTROL";
    private static final List<GrantedAuthority> AUTHORITIES =
            List.of(new SimpleGrantedAuthority(AUTHORITY));

    public RunnerAuthenticatedPrincipal {
        identity = Objects.requireNonNull(identity, "identity");
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return AUTHORITIES;
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return identity.runnerId().value();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
