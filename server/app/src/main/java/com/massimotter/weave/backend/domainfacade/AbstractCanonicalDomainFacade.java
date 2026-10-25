package com.massimotter.weave.backend.domainfacade;

import com.massimotter.weave.backend.provider.ProviderRegistry;
import com.massimotter.weave.backend.provider.ProviderSelectionRepository;
import com.massimotter.weave.backend.service.WorkspaceCapabilityService;
import java.time.Clock;
import org.springframework.security.oauth2.jwt.Jwt;

abstract class AbstractCanonicalDomainFacade implements CanonicalDomainFacade {

    private final CanonicalDomainFacadeSupport support;

    AbstractCanonicalDomainFacade(
            CanonicalDomainDefinition definition,
            ProviderRegistry providerRegistry,
            ProviderSelectionRepository providerSelectionRepository,
            WorkspaceCapabilityService workspaceCapabilityService) {
        this(definition, providerRegistry, providerSelectionRepository, workspaceCapabilityService, Clock.systemUTC());
    }

    AbstractCanonicalDomainFacade(
            CanonicalDomainDefinition definition,
            ProviderRegistry providerRegistry,
            ProviderSelectionRepository providerSelectionRepository,
            WorkspaceCapabilityService workspaceCapabilityService,
            Clock clock) {
        this.support = new CanonicalDomainFacadeSupport(
                definition,
                providerRegistry,
                providerSelectionRepository,
                workspaceCapabilityService,
                clock);
    }

    @Override
    public CanonicalDomainContract contract() {
        return support.contract();
    }

    @Override
    public CanonicalDomainReadiness memberReadiness(Jwt jwt) {
        return support.readiness(jwt, false);
    }

    @Override
    public CanonicalDomainReadiness adminReadiness(Jwt jwt) {
        return support.readiness(jwt, true);
    }

    @Override
    public CanonicalDomainItems items(Jwt jwt) {
        return support.items(jwt);
    }

    @Override
    public CanonicalCapabilityDecision evaluateCapability(Jwt jwt, String capability, String operation) {
        return support.evaluateCapability(jwt, capability, operation);
    }
}
