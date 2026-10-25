package com.massimotter.weave.backend.domainfacade;

import com.massimotter.weave.backend.provider.ProviderRegistry;
import com.massimotter.weave.backend.provider.ProviderSelectionRepository;
import com.massimotter.weave.backend.service.WorkspaceCapabilityService;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FilesDocsDomainFacadeService extends AbstractCanonicalDomainFacade {

    @Autowired
    public FilesDocsDomainFacadeService(
            ProviderRegistry providerRegistry,
            ProviderSelectionRepository providerSelectionRepository,
            WorkspaceCapabilityService workspaceCapabilityService) {
        super(CanonicalDomainDefinition.FILES_DOCS, providerRegistry, providerSelectionRepository, workspaceCapabilityService);
    }

    FilesDocsDomainFacadeService(
            ProviderRegistry providerRegistry,
            ProviderSelectionRepository providerSelectionRepository,
            WorkspaceCapabilityService workspaceCapabilityService,
            Clock clock) {
        super(CanonicalDomainDefinition.FILES_DOCS, providerRegistry, providerSelectionRepository, workspaceCapabilityService, clock);
    }
}
