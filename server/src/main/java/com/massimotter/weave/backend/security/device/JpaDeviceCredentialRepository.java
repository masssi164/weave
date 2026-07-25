package com.massimotter.weave.backend.security.device;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import static java.util.Objects.requireNonNull;

@Repository
@Transactional(readOnly = true)
public class JpaDeviceCredentialRepository implements DeviceCredentialRepository {

    private static final TypeReference<Set<String>> CAPABILITIES = new TypeReference<>() {
    };

    private final DeviceCredentialJpaRepository credentials;
    private final ObjectMapper objectMapper;

    public JpaDeviceCredentialRepository(
            DeviceCredentialJpaRepository credentials,
            ObjectMapper objectMapper) {
        this.credentials = requireNonNull(credentials, "credentials");
        this.objectMapper = requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public Optional<DeviceCredential> findById(String credentialId) {
        return credentials.findById(credentialId).map(this::toDomain);
    }

    @Override
    public List<DeviceCredential> findByDomainAndPrincipal(String domain, String principalRef) {
        return credentials.findByDomainAndPrincipalRefOrderByIssuedAtAsc(domain, principalRef).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public DeviceCredential save(DeviceCredential credential) {
        requireNonNull(credential, "credential");
        String capabilitiesJson = capabilitiesJson(credential.capabilities());
        DeviceCredentialJpaEntity entity = credentials.findById(credential.credentialId())
                .orElseGet(() -> DeviceCredentialJpaEntity.create(credential, capabilitiesJson));
        entity.applyRetry(credential, capabilitiesJson);
        return toDomain(credentials.saveAndFlush(entity));
    }

    private DeviceCredential toDomain(DeviceCredentialJpaEntity entity) {
        return new DeviceCredential(
                entity.credentialId(),
                entity.domain(),
                entity.tenantId(),
                entity.principalRef(),
                entity.subjectRef(),
                entity.username(),
                entity.clientType(),
                entity.label(),
                capabilities(entity.capabilitiesJson()),
                entity.secretHash(),
                entity.issuedAt().toInstant(),
                entity.expiresAt().toInstant(),
                entity.revokedAt() == null ? null : entity.revokedAt().toInstant());
    }

    private String capabilitiesJson(Set<String> capabilities) {
        try {
            return objectMapper.writeValueAsString(capabilities.stream().sorted().toList());
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Failed to serialize device credential capabilities.",
                    exception);
        }
    }

    private Set<String> capabilities(String json) {
        try {
            return objectMapper.readValue(json, CAPABILITIES);
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Failed to read device credential capabilities.",
                    exception);
        }
    }
}
