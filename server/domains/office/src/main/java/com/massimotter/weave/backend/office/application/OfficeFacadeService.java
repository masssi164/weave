package com.massimotter.weave.backend.office.application;

import com.massimotter.weave.backend.capability.WorkspaceCapabilityService;
import com.massimotter.weave.backend.office.api.OfficeCapabilitiesResponse;
import com.massimotter.weave.backend.office.api.OfficeLaunchRequest;
import com.massimotter.weave.backend.office.api.OfficeLaunchResponse;
import com.massimotter.weave.backend.office.port.OfficeProvider;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class OfficeFacadeService {

    private final OfficeProvider officeProvider;
    private final WorkspaceCapabilityService workspaceCapabilityService;

    public OfficeFacadeService(OfficeProvider officeProvider, WorkspaceCapabilityService workspaceCapabilityService) {
        this.officeProvider = officeProvider;
        this.workspaceCapabilityService = workspaceCapabilityService;
    }

    public OfficeCapabilitiesResponse capabilities(Jwt jwt) {
        workspaceCapabilityService.requireCapability(jwt, "documents.view", "office", "capabilities");
        return officeProvider.capabilities();
    }

    public OfficeLaunchResponse launch(Jwt jwt, OfficeLaunchRequest request) {
        workspaceCapabilityService.requireCapability(jwt, "documents.edit", "office", "launch");
        return officeProvider.launch(request);
    }
}
