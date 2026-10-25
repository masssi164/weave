package com.massimotter.weave.backend.office.port;

import com.massimotter.weave.backend.model.office.OfficeCapabilitiesResponse;
import com.massimotter.weave.backend.model.office.OfficeLaunchRequest;
import com.massimotter.weave.backend.model.office.OfficeLaunchResponse;
import com.massimotter.weave.backend.provider.ProviderPort;

public interface OfficeProvider extends ProviderPort {
    OfficeCapabilitiesResponse capabilities();

    OfficeLaunchResponse launch(OfficeLaunchRequest request);
}
