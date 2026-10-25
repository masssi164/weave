package com.massimotter.weave.backend.domainfacade;

import org.springframework.security.oauth2.jwt.Jwt;

public interface CanonicalDomainFacade {
    CanonicalDomainContract contract();

    CanonicalDomainReadiness memberReadiness(Jwt jwt);

    CanonicalDomainReadiness adminReadiness(Jwt jwt);

    CanonicalDomainItems items(Jwt jwt);

    CanonicalCapabilityDecision evaluateCapability(Jwt jwt, String capability, String operation);
}
