package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.model.office.OfficeCapabilitiesResponse;
import com.massimotter.weave.backend.model.office.OfficeLaunchRequest;
import com.massimotter.weave.backend.model.office.OfficeLaunchResponse;
import com.massimotter.weave.backend.office.port.OfficeProvider;
import org.springframework.stereotype.Service;

@Service
public class OfficeFacadeService {

    private final OfficeProvider officeProvider;

    public OfficeFacadeService(OfficeProvider officeProvider) {
        this.officeProvider = officeProvider;
    }

    public OfficeCapabilitiesResponse capabilities() {
        return officeProvider.capabilities();
    }

    public OfficeLaunchResponse launch(OfficeLaunchRequest request) {
        return officeProvider.launch(request);
    }
}
