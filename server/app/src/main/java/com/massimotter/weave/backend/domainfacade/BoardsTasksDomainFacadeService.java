package com.massimotter.weave.backend.domainfacade;

import com.massimotter.weave.backend.provider.ProviderRegistry;
import com.massimotter.weave.backend.provider.ProviderSelectionRepository;
import com.massimotter.weave.backend.service.WorkspaceCapabilityService;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BoardsTasksDomainFacadeService extends AbstractCanonicalDomainFacade {

    @Autowired
    public BoardsTasksDomainFacadeService(
            ProviderRegistry providerRegistry,
            ProviderSelectionRepository providerSelectionRepository,
            WorkspaceCapabilityService workspaceCapabilityService) {
        super(CanonicalDomainDefinition.BOARDS_TASKS, providerRegistry, providerSelectionRepository, workspaceCapabilityService);
    }

    BoardsTasksDomainFacadeService(
            ProviderRegistry providerRegistry,
            ProviderSelectionRepository providerSelectionRepository,
            WorkspaceCapabilityService workspaceCapabilityService,
            Clock clock) {
        super(CanonicalDomainDefinition.BOARDS_TASKS, providerRegistry, providerSelectionRepository, workspaceCapabilityService, clock);
    }
}
