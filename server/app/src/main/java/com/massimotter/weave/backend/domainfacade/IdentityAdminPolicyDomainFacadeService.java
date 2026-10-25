package com.massimotter.weave.backend.domainfacade;

import com.massimotter.weave.backend.provider.ProviderRegistry;
import com.massimotter.weave.backend.provider.ProviderSelectionRepository;
import com.massimotter.weave.backend.service.WorkspaceCapabilityService;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class IdentityAdminPolicyDomainFacadeService extends AbstractCanonicalDomainFacade {

    @Autowired
    public IdentityAdminPolicyDomainFacadeService(
            ProviderRegistry providerRegistry,
            ProviderSelectionRepository providerSelectionRepository,
            WorkspaceCapabilityService workspaceCapabilityService) {
        super(CanonicalDomainDefinition.IDENTITY_ADMIN_POLICY, providerRegistry, providerSelectionRepository, workspaceCapabilityService);
    }

    IdentityAdminPolicyDomainFacadeService(
            ProviderRegistry providerRegistry,
            ProviderSelectionRepository providerSelectionRepository,
            WorkspaceCapabilityService workspaceCapabilityService,
            Clock clock) {
        super(CanonicalDomainDefinition.IDENTITY_ADMIN_POLICY, providerRegistry, providerSelectionRepository, workspaceCapabilityService, clock);
    }
}
