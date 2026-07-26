package com.massimotter.weave.backend.security.device;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.persistence.jpa.security.DeviceCredentialJpaEntity;
import com.massimotter.weave.backend.persistence.jpa.security.DeviceCredentialJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.transaction.annotation.Transactional;

public class JpaDeviceCredentialRepository implements DeviceCredentialRepository {

    private static final TypeReference<Set<String>> CAPABILITIES = new TypeReference<>() {
    };

    private final DeviceCredentialJpaRepository repository;
    private final ObjectMapper objectMapper;

    public JpaDeviceCredentialRepository(
            DeviceCredentialJpaRepository repository,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DeviceCredential> findById(String credentialId) {
        return repository.findById(credentialId).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeviceCredential> findByDomainAndPrincipal(String domain, String principalRef) {
        return repository.findByDomainAndPrincipalRefOrderByIssuedAtAsc(domain, principalRef)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public DeviceCredential save(DeviceCredential credential) {
        repository.saveAndFlush(toEntity(credential));
        return credential;
    }

    private DeviceCredentialJpaEntity toEntity(DeviceCredential value) {
        return new DeviceCredentialJpaEntity(
                value.credentialId(),
                value.domain(),
                value.tenantId(),
                value.principalRef(),
                value.subject(),
                value.username(),
                value.clientType(),
                value.label(),
                capabilitiesJson(value.capabilities()),
                value.secretHash(),
                value.issuedAt(),
                value.expiresAt(),
                value.revokedAt());
    }

    private DeviceCredential toDomain(DeviceCredentialJpaEntity value) {
        return new DeviceCredential(
                value.credentialId(),
                value.domain(),
                value.tenantId(),
                value.principalRef(),
                value.subjectRef(),
                value.username(),
                value.clientType(),
                value.label(),
                capabilities(value.capabilitiesJson()),
                value.secretHash(),
                value.issuedAt(),
                value.expiresAt(),
                value.revokedAt());
    }

    private String capabilitiesJson(Set<String> capabilities) {
        try {
            return objectMapper.writeValueAsString(capabilities.stream().sorted().toList());
        } catch (JacksonException failure) {
            throw new IllegalStateException("Failed to serialize device credential capabilities.", failure);
        }
    }

    private Set<String> capabilities(String json) {
        try {
            return objectMapper.readValue(json, CAPABILITIES);
        } catch (JacksonException failure) {
            throw new IllegalStateException("Failed to read device credential capabilities.", failure);
        }
    }
}
