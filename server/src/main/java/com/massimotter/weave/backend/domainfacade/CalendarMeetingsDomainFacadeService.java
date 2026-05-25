package com.massimotter.weave.backend.domainfacade;

import com.massimotter.weave.backend.provider.ProviderRegistry;
import com.massimotter.weave.backend.provider.ProviderSelectionRepository;
import com.massimotter.weave.backend.service.WorkspaceCapabilityService;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CalendarMeetingsDomainFacadeService extends AbstractCanonicalDomainFacade {

    @Autowired
    public CalendarMeetingsDomainFacadeService(
            ProviderRegistry providerRegistry,
            ProviderSelectionRepository providerSelectionRepository,
            WorkspaceCapabilityService workspaceCapabilityService) {
        super(CanonicalDomainDefinition.CALENDAR_MEETINGS, providerRegistry, providerSelectionRepository, workspaceCapabilityService);
    }

    CalendarMeetingsDomainFacadeService(
            ProviderRegistry providerRegistry,
            ProviderSelectionRepository providerSelectionRepository,
            WorkspaceCapabilityService workspaceCapabilityService,
            Clock clock) {
        super(CanonicalDomainDefinition.CALENDAR_MEETINGS, providerRegistry, providerSelectionRepository, workspaceCapabilityService, clock);
    }
}
