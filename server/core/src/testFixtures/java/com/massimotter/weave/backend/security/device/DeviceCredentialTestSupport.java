package com.massimotter.weave.backend.security.device;

import java.util.List;

/** Explicit two-domain installation used only by existing product test fixtures. */
public final class DeviceCredentialTestSupport {
    private DeviceCredentialTestSupport() {}

    public static DeviceCredentialService service(DeviceCredentialRepository repository) {
        return new DeviceCredentialService(repository, List.of(
                new DeviceCredentialDomain("files"), new DeviceCredentialDomain("calendar")));
    }
}
