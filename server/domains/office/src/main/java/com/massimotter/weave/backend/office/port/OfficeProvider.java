package com.massimotter.weave.backend.office.port;

import com.massimotter.weave.backend.office.api.OfficeCapabilitiesResponse;
import com.massimotter.weave.backend.office.api.OfficeLaunchRequest;
import com.massimotter.weave.backend.office.api.OfficeLaunchResponse;
import com.massimotter.weave.backend.discovery.provider.ProviderPort;

public interface OfficeProvider extends ProviderPort {
    OfficeCapabilitiesResponse capabilities();

    OfficeLaunchResponse launch(OfficeLaunchRequest request);
}
