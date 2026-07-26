package com.massimotter.weave.backend.agentruntime.adapter;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeAuditCorrelation;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeEntitlementObservation;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeEntitlementRef;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeEntitlementState;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeRevocation;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadOwnership;
import com.massimotter.weave.backend.agentruntime.port.RuntimeGovernanceRepository;
import jakarta.persistence.PersistenceException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static java.util.Objects.requireNonNull;

/** Relational authority facts with versioned entitlements and append-only evidence ledgers. */
@Repository
public class JpaRuntimeGovernanceRepository
        implements RuntimeGovernanceRepository {

    private static final String ENTITLEMENT_DOMAIN =
            "weave.agent-runtime.entitlement/v1";

    private final RuntimeEntitlementJpaRepository entitlements;
    private final RuntimeRevocationJpaRepository revocations;
    private final RuntimeAuditCorrelationJpaRepository correlations;
    private final TransactionTemplate transactions;

    public JpaRuntimeGovernanceRepository(
            RuntimeEntitlementJpaRepository entitlements,
            RuntimeRevocationJpaRepository revocations,
            RuntimeAuditCorrelationJpaRepository correlations,
            PlatformTransactionManager transactionManager) {
        this.entitlements = requireNonNull(entitlements, "entitlements");
        this.revocations = requireNonNull(revocations, "revocations");
        this.correlations = requireNonNull(correlations, "correlations");
        this.transactions = new TransactionTemplate(
                requireNonNull(transactionManager, "transactionManager"));
    }

    @Override
    public RuntimeEntitlementRef activate(
            RuntimeEntitlementObservation observation,
            String activationRef,
            String auditRef,
            Instant now) {
        requireNonNull(observation, "observation");
        requireText(activationRef, "activationRef");
        requireText(auditRef, "auditRef");
        requireNonNull(now, "now");
        if (observation.observedAt().isAfter(now.plusSeconds(1))) {
            throw new IllegalArgumentException(
                    "future entitlement observations are rejected");
        }
        try {
            return requireNonNull(transactions.execute(status -> {
                Optional<RuntimeEntitlementJpaEntity> reusable =
                        entitlements.lockReusable(
                                observation.organizationRef(),
                                observation.personRef(),
                                observation.memberBinding().issuer(),
                                observation.memberBinding().subject(),
                                observation.sourceProvider(),
                                observation.sourceGroupRef(),
                                observation.capabilityRevision(),
                                RuntimeEntitlementState.ENTITLED);
                if (reusable.isPresent()) {
                    RuntimeEntitlementJpaEntity entity = reusable.orElseThrow();
                    entity.observe(observation, auditRef, now);
                    return entitlements.saveAndFlush(entity).toDomain();
                }
                RuntimeEntitlementRef proposed =
                        proposedEntitlement(
                                observation,
                                activationRef,
                                auditRef,
                                now);
                RuntimeEntitlementJpaEntity existing = entitlements
                        .findByEntitlementRef(proposed.entitlementRef())
                        .orElse(null);
                if (existing != null) {
                    return requireSameEntitlement(proposed, existing.toDomain());
                }
                return entitlements.saveAndFlush(
                                RuntimeEntitlementJpaEntity.from(proposed))
                        .toDomain();
            }));
        } catch (DataIntegrityViolationException | PersistenceException duplicateOrFailure) {
            RuntimeEntitlementRef proposed =
                    proposedEntitlement(
                            observation,
                            activationRef,
                            auditRef,
                            now);
            return transactions.execute(status -> requireSameEntitlement(
                    proposed,
                    entitlements.findByEntitlementRef(proposed.entitlementRef())
                            .orElseThrow(() -> duplicateOrFailure)
                            .toDomain()));
        }
    }

    @Override
    public Optional<RuntimeEntitlementRef> findCurrent(
            String organizationRef,
            String personRef) {
        requireText(organizationRef, "organizationRef");
        requireText(personRef, "personRef");
        Optional<RuntimeEntitlementJpaEntity> entitled = entitlements
                .findFirstByOrganizationRefAndPersonRefAndStateOrderByLastObservedAtDescCreatedAtDescEntitlementRefDesc(
                        organizationRef,
                        personRef,
                        RuntimeEntitlementState.ENTITLED);
        return entitled
                .or(() -> entitlements
                        .findFirstByOrganizationRefAndPersonRefOrderByLastObservedAtDescCreatedAtDescEntitlementRefDesc(
                                organizationRef,
                                personRef))
                .map(RuntimeEntitlementJpaEntity::toDomain);
    }

    @Override
    public Optional<RuntimeEntitlementRef> findRevision(
            String organizationRef,
            String personRef,
            String entitlementRevision) {
        requireText(organizationRef, "organizationRef");
        requireText(personRef, "personRef");
        requireText(entitlementRevision, "entitlementRevision");
        return entitlements
                .findByOrganizationRefAndPersonRefAndEntitlementRevision(
                        organizationRef,
                        personRef,
                        entitlementRevision)
                .map(RuntimeEntitlementJpaEntity::toDomain);
    }

    @Override
    public Optional<RuntimeEntitlementRef> findEffectiveRevision(
            String organizationRef,
            String personRef,
            String entitlementRevision,
            Instant now) {
        requireText(organizationRef, "organizationRef");
        requireText(personRef, "personRef");
        requireText(entitlementRevision, "entitlementRevision");
        requireNonNull(now, "now");
        return entitlements
                .findEffectiveRevision(
                        organizationRef,
                        personRef,
                        entitlementRevision,
                        RuntimeEntitlementState.ENTITLED,
                        RuntimePersistenceTime.utc(now))
                .map(RuntimeEntitlementJpaEntity::toDomain);
    }

    @Override
    public RuntimeRevocation revoke(
            RuntimeEntitlementRef entitlement,
            String cellRef,
            String profileHash,
            String workloadRefHash,
            String reasonCode,
            String reasonRefHash,
            String actorRefHash,
            String revocationRef,
            String auditCorrelationRef,
            Instant now) {
        requireNonNull(entitlement, "entitlement");
        RuntimeRevocation proposed = new RuntimeRevocation(
                UUID.randomUUID(),
                revocationRef,
                entitlement.organizationRef(),
                entitlement.personRef(),
                reasonCode,
                reasonRefHash,
                actorRefHash,
                now,
                entitlement.entitlementRef(),
                entitlement.entitlementRevision(),
                cellRef,
                profileHash,
                workloadRefHash,
                auditCorrelationRef,
                now);
        RuntimeRevocationJpaEntity existing =
                revocations.findByRevocationRef(revocationRef).orElse(null);
        if (existing != null) {
            return requireSameRevocation(proposed, existing.toDomain());
        }
        try {
            return requireNonNull(transactions.execute(status -> {
                RuntimeEntitlementJpaEntity stored = entitlements
                        .lockByEntitlementRef(entitlement.entitlementRef())
                        .orElseThrow(() -> new IllegalStateException(
                                "revoked entitlement disappeared"));
                RuntimeRevocationJpaEntity persisted =
                        revocations.save(
                                RuntimeRevocationJpaEntity.from(proposed));
                stored.revoke(proposed, now);
                entitlements.flush();
                revocations.flush();
                return persisted.toDomain();
            }));
        } catch (DataIntegrityViolationException | PersistenceException duplicateOrFailure) {
            return transactions.execute(status -> requireSameRevocation(
                    proposed,
                    revocations.findByRevocationRef(revocationRef)
                            .orElseThrow(() -> duplicateOrFailure)
                            .toDomain()));
        }
    }

    @Override
    public RuntimeAuditCorrelation appendCorrelation(
            RuntimeAuditCorrelation correlation) {
        requireNonNull(correlation, "correlation");
        RuntimeAuditCorrelationJpaEntity existing = correlations
                .findByCorrelationRef(correlation.correlationRef())
                .orElse(null);
        if (existing != null) {
            return requireSameCorrelation(correlation, existing.toDomain());
        }
        try {
            return requireNonNull(transactions.execute(status -> correlations
                    .saveAndFlush(RuntimeAuditCorrelationJpaEntity.from(correlation))
                    .toDomain()));
        } catch (DataIntegrityViolationException | PersistenceException duplicateOrFailure) {
            return transactions.execute(status -> requireSameCorrelation(
                    correlation,
                    correlations.findByCorrelationRef(correlation.correlationRef())
                            .orElseThrow(() -> duplicateOrFailure)
                            .toDomain()));
        }
    }

    private RuntimeEntitlementRef proposedEntitlement(
            RuntimeEntitlementObservation observation,
            String activationRef,
            String auditRef,
            Instant now) {
        String identity = material(observation, activationRef);
        String entitlementRef = "entitlement:"
                + RuntimeWorkloadOwnership.fingerprint(identity).substring(7);
        String revision = RuntimeWorkloadOwnership.fingerprint(
                ENTITLEMENT_DOMAIN + "\u0000" + identity);
        return new RuntimeEntitlementRef(
                UUID.randomUUID(),
                entitlementRef,
                revision,
                observation.organizationRef(),
                observation.personRef(),
                observation.memberBinding(),
                observation.sourceProvider(),
                observation.sourceGroupRef(),
                observation.capabilityRevision(),
                RuntimeEntitlementState.ENTITLED,
                observation.observedAt(),
                observation.observedAt(),
                observation.expiresAt(),
                null,
                null,
                auditRef,
                now,
                now);
    }

    private static String material(
            RuntimeEntitlementObservation observation,
            String activationRef) {
        return observation.organizationRef() + "\u0000"
                + observation.personRef() + "\u0000"
                + observation.memberBinding().issuer() + "\u0000"
                + observation.memberBinding().subject() + "\u0000"
                + observation.sourceProvider() + "\u0000"
                + observation.sourceGroupRef() + "\u0000"
                + observation.capabilityRevision() + "\u0000"
                + activationRef;
    }

    private static RuntimeEntitlementRef requireSameEntitlement(
            RuntimeEntitlementRef expected,
            RuntimeEntitlementRef actual) {
        if (!expected.entitlementRef().equals(actual.entitlementRef())
                || !expected.entitlementRevision().equals(actual.entitlementRevision())
                || !expected.organizationRef().equals(actual.organizationRef())
                || !expected.personRef().equals(actual.personRef())
                || !expected.memberBinding().equals(actual.memberBinding())
                || !expected.sourceProvider().equals(actual.sourceProvider())
                || !expected.sourceGroupRef().equals(actual.sourceGroupRef())
                || !expected.capabilityRevision().equals(actual.capabilityRevision())) {
            throw new IllegalStateException(
                    "entitlement reference is bound to different authority evidence");
        }
        if (actual.state() != RuntimeEntitlementState.ENTITLED) {
            throw new IllegalStateException(
                    "a revoked entitlement activation cannot be replayed");
        }
        return actual;
    }

    private static RuntimeRevocation requireSameRevocation(
            RuntimeRevocation expected,
            RuntimeRevocation actual) {
        if (!expected.revocationRef().equals(actual.revocationRef())
                || !expected.organizationRef().equals(actual.organizationRef())
                || !expected.personRef().equals(actual.personRef())
                || !expected.reasonCode().equals(actual.reasonCode())
                || !expected.reasonRefHash().equals(actual.reasonRefHash())
                || !expected.actorRefHash().equals(actual.actorRefHash())
                || !expected.entitlementRef().equals(actual.entitlementRef())
                || !expected.entitlementRevision().equals(actual.entitlementRevision())
                || !expected.cellRef().equals(actual.cellRef())
                || !Objects.equals(expected.profileHash(), actual.profileHash())
                || !expected.workloadRefHash().equals(actual.workloadRefHash())
                || !expected.auditCorrelationRef().equals(actual.auditCorrelationRef())) {
            throw new IllegalStateException(
                    "revocation reference is bound to different evidence");
        }
        return actual;
    }

    private static RuntimeAuditCorrelation requireSameCorrelation(
            RuntimeAuditCorrelation expected,
            RuntimeAuditCorrelation actual) {
        if (!expected.correlationRef().equals(actual.correlationRef())
                || !expected.organizationRefHash().equals(actual.organizationRefHash())
                || !expected.personRefHash().equals(actual.personRefHash())
                || !Objects.equals(expected.keycloakRefHash(), actual.keycloakRefHash())
                || !Objects.equals(expected.orchestratorRefHash(), actual.orchestratorRefHash())
                || !Objects.equals(expected.openClawRefHash(), actual.openClawRefHash())
                || !Objects.equals(expected.matrixRefHash(), actual.matrixRefHash())
                || !Objects.equals(expected.mcpRefHash(), actual.mcpRefHash())
                || !Objects.equals(expected.domainAuditRefHash(), actual.domainAuditRefHash())) {
            throw new IllegalStateException(
                    "audit correlation reference is bound to different evidence");
        }
        return actual;
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 255) {
            throw new IllegalArgumentException(field + " is required and bounded");
        }
    }
}
