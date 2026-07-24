package com.massimotter.weave.backend.identity.realm;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.persistence.jpa.IdentityRealmEvidenceEntity;
import com.massimotter.weave.backend.persistence.jpa.IdentityRealmEvidenceJpaRepository;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class JpaIdentityRealmEvidenceRepository implements IdentityRealmEvidenceRepository {
    private final IdentityRealmEvidenceJpaRepository repository;
    private final ObjectMapper objectMapper;

    public JpaIdentityRealmEvidenceRepository(
            IdentityRealmEvidenceJpaRepository repository,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(IdentityRealmDryRunEvidence evidence) {
        if (evidence == null || evidence.dryRunId() == null || evidence.dryRunId().isBlank()) {
            return;
        }
        try {
            repository.saveAndFlush(new IdentityRealmEvidenceEntity(
                    evidence.dryRunId(),
                    evidence.auditRef(),
                    evidence.providerKey(),
                    evidence.realmId(),
                    objectMapper.writeValueAsString(evidence.report()),
                    evidence.createdAt().atOffset(ZoneOffset.UTC)));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Identity realm evidence could not be serialized", exception);
        }
    }

    @Override
    public Optional<IdentityRealmDryRunEvidence> findDryRun(String dryRunId) {
        if (dryRunId == null || dryRunId.isBlank()) {
            return Optional.empty();
        }
        return repository.findById(dryRunId).map(this::evidence);
    }

    private IdentityRealmDryRunEvidence evidence(IdentityRealmEvidenceEntity entity) {
        try {
            return new IdentityRealmDryRunEvidence(
                    entity.dryRunId(),
                    entity.auditRef(),
                    entity.providerKey(),
                    entity.realmId(),
                    objectMapper.readValue(entity.reportJson(), IdentityRealmDryRunReport.class),
                    entity.createdAt().toInstant());
        } catch (JacksonException exception) {
            throw new IllegalStateException("Identity realm evidence could not be read", exception);
        }
    }
}
