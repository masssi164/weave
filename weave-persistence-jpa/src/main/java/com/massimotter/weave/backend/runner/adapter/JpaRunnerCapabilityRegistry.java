package com.massimotter.weave.backend.runner.adapter;

import com.massimotter.weave.backend.runner.application.RunnerCapabilityRegistry;
import com.massimotter.weave.backend.runner.domain.RunnerControl.CapabilityRef;
import com.massimotter.weave.backend.runner.domain.RunnerControl.RunnerId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL-backed public capability catalog with replaceable Runner offerings. */
public class JpaRunnerCapabilityRegistry implements RunnerCapabilityRegistry {

    private final EntityManager entityManager;

    public JpaRunnerCapabilityRegistry(EntityManager entityManager) {
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager");
    }

    @Override
    @Transactional
    public PublicationResult publish(PublicBundlePublication publication) {
        Objects.requireNonNull(publication, "publication");
        RunnerCapabilityCatalogJpaEntity catalog = lockCatalog(publication);

        Map<String, RunnerCapabilityDefinitionJpaEntity> definitions = definitions(
                publication.organizationRef(),
                LockModeType.PESSIMISTIC_WRITE);
        List<CapabilityContract> newContracts = new ArrayList<>();
        for (CapabilityContract contract : publication.capabilities()) {
            RunnerCapabilityDefinitionJpaEntity existing =
                    definitions.get(contract.capability().coordinate());
            if (existing == null) {
                newContracts.add(contract);
            } else if (!existing.matches(contract)) {
                throw new IllegalStateException(
                        "capability coordinate already exists with a different public capability contract");
            }
        }

        boolean created = false;
        boolean updated = false;
        long catalogRevision = catalog.revision();
        if (!newContracts.isEmpty()) {
            catalogRevision = catalog.increment(publication.observedAt());
            for (CapabilityContract contract : newContracts) {
                RunnerCapabilityDefinitionJpaEntity definition =
                        RunnerCapabilityDefinitionJpaEntity.create(
                                publication.organizationRef(),
                                contract,
                                catalogRevision,
                                publication.observedAt());
                entityManager.persist(definition);
                definitions.put(contract.capability().coordinate(), definition);
            }
            entityManager.flush();
            created = true;
        }

        List<RunnerCapabilityOfferingJpaEntity> currentOfferings = entityManager.createQuery(
                        """
                        select offering
                        from RunnerCapabilityOfferingJpaEntity offering
                        where offering.organizationRef = :organizationRef
                          and offering.runnerId = :runnerId
                        """,
                        RunnerCapabilityOfferingJpaEntity.class)
                .setParameter("organizationRef", publication.organizationRef())
                .setParameter("runnerId", publication.runnerId().value())
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();
        Map<String, RunnerCapabilityOfferingJpaEntity> offeringsByCoordinate = new LinkedHashMap<>();
        for (RunnerCapabilityOfferingJpaEntity offering : currentOfferings) {
            offeringsByCoordinate.put(offering.coordinate(), offering);
        }

        for (CapabilityContract contract : publication.capabilities()) {
            String coordinate = contract.capability().coordinate();
            RunnerCapabilityDefinitionJpaEntity definition = definitions.get(coordinate);
            RunnerCapabilityOfferingJpaEntity offering = offeringsByCoordinate.remove(coordinate);
            if (offering == null) {
                entityManager.persist(RunnerCapabilityOfferingJpaEntity.create(definition, publication));
                created = true;
                continue;
            }
            if (!offering.definitionId().equals(definition.definitionId())) {
                throw new IllegalStateException(
                        "Runner offering points to a different public capability definition");
            }
            updated |= offering.update(publication);
        }
        for (RunnerCapabilityOfferingJpaEntity omitted : offeringsByCoordinate.values()) {
            updated |= omitted.deactivate(publication.observedAt());
        }
        entityManager.flush();

        PublicationDisposition disposition = created
                ? PublicationDisposition.CREATED
                : updated ? PublicationDisposition.UPDATED : PublicationDisposition.IDEMPOTENT_REPLAY;
        return new PublicationResult(
                catalogRevision,
                disposition,
                countDefinitions(publication.organizationRef()),
                countActiveOfferings(publication.organizationRef()));
    }

    @Override
    @Transactional
    public AvailabilityResult observeAvailability(AvailabilityObservation observation) {
        AvailabilityObservation value = Objects.requireNonNull(observation, "observation");
        List<RunnerCapabilityOfferingJpaEntity> offerings = entityManager.createQuery(
                        """
                        select offering
                        from RunnerCapabilityOfferingJpaEntity offering
                        where offering.organizationRef = :organizationRef
                          and offering.runnerId = :runnerId
                          and offering.publicBundleDigest = :publicBundleDigest
                          and offering.active = true
                        order by offering.capabilityId, offering.capabilityVersion
                        """,
                        RunnerCapabilityOfferingJpaEntity.class)
                .setParameter("organizationRef", value.organizationRef())
                .setParameter("runnerId", value.runnerId().value())
                .setParameter("publicBundleDigest", value.publicBundleDigest())
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();
        if (offerings.isEmpty()) {
            throw new IllegalStateException(
                    "Runner heartbeat references an unpublished public capability bundle");
        }

        RunnerSessionJpaEntity session = entityManager.find(
                RunnerSessionJpaEntity.class,
                value.runnerId().value(),
                LockModeType.PESSIMISTIC_WRITE);
        AvailabilityDisposition disposition;
        if (session == null) {
            session = RunnerSessionJpaEntity.create(value);
            entityManager.persist(session);
            disposition = AvailabilityDisposition.CREATED;
        } else {
            disposition = session.observe(value);
        }

        for (RunnerCapabilityOfferingJpaEntity offering : offerings) {
            offering.observeAvailability(value);
        }
        entityManager.flush();
        return new AvailabilityResult(
                disposition,
                offerings.size(),
                value.availableSlots());
    }

    @Override
    @Transactional(readOnly = true)
    public CatalogSnapshot catalog(String organizationRef) {
        String organization = organization(organizationRef);
        RunnerCapabilityCatalogJpaEntity catalog =
                entityManager.find(RunnerCapabilityCatalogJpaEntity.class, organization);
        if (catalog == null) {
            return new CatalogSnapshot(organization, 0, List.of());
        }
        List<CapabilityDefinition> snapshots = definitions(organization, null).values().stream()
                .map(RunnerCapabilityDefinitionJpaEntity::snapshot)
                .sorted(Comparator.comparing(value -> value.contract().capability().coordinate()))
                .toList();
        return new CatalogSnapshot(organization, catalog.revision(), snapshots);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RunnerOffering> offerings(
            String organizationRef,
            CapabilityRef capability) {
        String organization = organization(organizationRef);
        CapabilityRef requested = Objects.requireNonNull(capability, "capability");
        return entityManager.createQuery(
                        """
                        select offering
                        from RunnerCapabilityOfferingJpaEntity offering
                        where offering.organizationRef = :organizationRef
                          and offering.capabilityId = :capabilityId
                          and offering.capabilityVersion = :capabilityVersion
                        order by offering.runnerId
                        """,
                        RunnerCapabilityOfferingJpaEntity.class)
                .setParameter("organizationRef", organization)
                .setParameter("capabilityId", requested.id().value())
                .setParameter("capabilityVersion", requested.version())
                .getResultList()
                .stream()
                .map(RunnerCapabilityOfferingJpaEntity::snapshot)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RunnerSession> session(
            String organizationRef,
            RunnerId runnerId) {
        String organization = organization(organizationRef);
        RunnerId runner = Objects.requireNonNull(runnerId, "runnerId");
        return entityManager.createQuery(
                        """
                        select session
                        from RunnerSessionJpaEntity session
                        where session.organizationRef = :organizationRef
                          and session.runnerId = :runnerId
                        """,
                        RunnerSessionJpaEntity.class)
                .setParameter("organizationRef", organization)
                .setParameter("runnerId", runner.value())
                .getResultStream()
                .findFirst()
                .map(RunnerSessionJpaEntity::snapshot);
    }

    private RunnerCapabilityCatalogJpaEntity lockCatalog(PublicBundlePublication publication) {
        RunnerCapabilityCatalogLockJpaEntity publicationLock = entityManager.find(
                RunnerCapabilityCatalogLockJpaEntity.class,
                RunnerCapabilityCatalogLockJpaEntity.PUBLICATION_LOCK_ID,
                LockModeType.PESSIMISTIC_WRITE);
        if (publicationLock == null) {
            throw new IllegalStateException("capability catalog publication lock is missing");
        }

        RunnerCapabilityCatalogJpaEntity catalog = entityManager.find(
                RunnerCapabilityCatalogJpaEntity.class,
                publication.organizationRef(),
                LockModeType.PESSIMISTIC_WRITE);
        if (catalog == null) {
            catalog = RunnerCapabilityCatalogJpaEntity.create(
                    publication.organizationRef(),
                    publication.observedAt());
            entityManager.persist(catalog);
            entityManager.flush();
        }
        return catalog;
    }

    private Map<String, RunnerCapabilityDefinitionJpaEntity> definitions(
            String organizationRef,
            LockModeType lockMode) {
        var query = entityManager.createQuery(
                        """
                        select definition
                        from RunnerCapabilityDefinitionJpaEntity definition
                        where definition.organizationRef = :organizationRef
                        order by definition.capabilityId, definition.capabilityVersion
                        """,
                        RunnerCapabilityDefinitionJpaEntity.class)
                .setParameter("organizationRef", organizationRef);
        if (lockMode != null) {
            query.setLockMode(lockMode);
        }
        Map<String, RunnerCapabilityDefinitionJpaEntity> result = new LinkedHashMap<>();
        for (RunnerCapabilityDefinitionJpaEntity definition : query.getResultList()) {
            result.put(definition.capability().coordinate(), definition);
        }
        return result;
    }

    private int countDefinitions(String organizationRef) {
        return Math.toIntExact(entityManager.createQuery(
                        """
                        select count(definition)
                        from RunnerCapabilityDefinitionJpaEntity definition
                        where definition.organizationRef = :organizationRef
                        """,
                        Long.class)
                .setParameter("organizationRef", organizationRef)
                .getSingleResult());
    }

    private int countActiveOfferings(String organizationRef) {
        return Math.toIntExact(entityManager.createQuery(
                        """
                        select count(offering)
                        from RunnerCapabilityOfferingJpaEntity offering
                        where offering.organizationRef = :organizationRef
                          and offering.active = true
                        """,
                        Long.class)
                .setParameter("organizationRef", organizationRef)
                .getSingleResult());
    }

    private static String organization(String value) {
        if (value == null || value.isBlank() || !value.equals(value.strip()) || value.length() > 256) {
            throw new IllegalArgumentException("organizationRef is invalid");
        }
        return value;
    }
}
