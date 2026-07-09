package com.massimotter.weave.backend.security.device;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(
        name = "weave.security.device-credentials.storage.mode",
        havingValue = "memory",
        matchIfMissing = true)
public class InMemoryDeviceCredentialRepository implements DeviceCredentialRepository {

    private final Map<String, DeviceCredential> credentials = new ConcurrentHashMap<>();

    @Override
    public Optional<DeviceCredential> findById(String credentialId) {
        return Optional.ofNullable(credentials.get(credentialId));
    }

    @Override
    public List<DeviceCredential> findByDomainAndPrincipal(String domain, String principalRef) {
        return credentials.values().stream()
                .filter(credential -> credential.domain().equals(domain)
                        && credential.principalRef().equals(principalRef))
                .sorted(java.util.Comparator.comparing(DeviceCredential::issuedAt))
                .toList();
    }

    @Override
    public DeviceCredential save(DeviceCredential credential) {
        credentials.put(credential.credentialId(), credential);
        return credential;
    }
}
