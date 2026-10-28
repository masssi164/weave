package com.massimotter.weave.backend.security.device;

import java.util.List;
import java.util.Optional;

public interface DeviceCredentialRepository {

    Optional<DeviceCredential> findById(String credentialId);

    List<DeviceCredential> findByDomainAndPrincipal(String domain, String principalRef);

    DeviceCredential save(DeviceCredential credential);
}
