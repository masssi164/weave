package com.massimotter.weave.backend.service;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.persistence.jpa.OrganizationBootstrapEntity;
import com.massimotter.weave.backend.persistence.jpa.OrganizationBootstrapJpaRepository;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class JpaOrganizationBootstrapRepository implements OrganizationBootstrapRepository {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final OrganizationBootstrapJpaRepository repository;
    private final ObjectMapper objectMapper;

    public JpaOrganizationBootstrapRepository(
            OrganizationBootstrapJpaRepository repository,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<OrganizationBootstrapRecord> findByOrganizationId(String organizationId) {
        if (organizationId == null || organizationId.isBlank()) {
            return Optional.empty();
        }
        return repository.findById(normalize(organizationId)).map(this::record);
    }

    @Override
    public OrganizationBootstrapRecord save(OrganizationBootstrapRecord record) {
        if (record == null || record.organizationId() == null || record.organizationId().isBlank()) {
            throw new IllegalArgumentException("Organization bootstrap record requires a non-blank organization id.");
        }
        try {
            OrganizationBootstrapEntity saved = repository.saveAndFlush(new OrganizationBootstrapEntity(
                    normalize(record.organizationId()),
                    record.bootstrapMode(),
                    record.actorPrimaryIdentityKey(),
                    objectMapper.writeValueAsString(record.retainedAdminPrimaryIdentityKeys()),
                    record.bootstrappedAt().atOffset(ZoneOffset.UTC)));
            return record(saved);
        } catch (JacksonException exception) {
            throw new OrganizationBootstrapStoreException(
                    "Failed to serialize organization bootstrap authority state", exception);
        }
    }

    private OrganizationBootstrapRecord record(OrganizationBootstrapEntity entity) {
        try {
            return new OrganizationBootstrapRecord(
                    entity.organizationId(),
                    entity.bootstrapMode(),
                    entity.actorPrimaryIdentityKey(),
                    objectMapper.readValue(entity.retainedAdminPrimaryIdentityKeysJson(), STRING_LIST),
                    entity.bootstrappedAt().toInstant());
        } catch (JacksonException exception) {
            throw new OrganizationBootstrapStoreException(
                    "Failed to read organization bootstrap authority state", exception);
        }
    }

    private static String normalize(String organizationId) {
        return organizationId.trim().toLowerCase(Locale.ROOT);
    }
}
