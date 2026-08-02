package com.massimotter.weave.backend.providerbinding.adapter;

import com.massimotter.weave.backend.providerbinding.domain.ProviderBinding;
import com.massimotter.weave.backend.providerbinding.domain.ProviderObjectMapping;
import com.massimotter.weave.backend.providerbinding.port.ProviderBindingRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import static java.util.Objects.requireNonNull;

/** Typed JPA adapter for versioned provider authority and private provider-ID mappings. */
@Repository
@Transactional(readOnly = true)
public class JpaProviderBindingRepository implements ProviderBindingRepository {

    private final ProviderBindingJpaRepository bindings;
    private final ProviderObjectMappingJpaRepository mappings;

    public JpaProviderBindingRepository(
            ProviderBindingJpaRepository bindings,
            ProviderObjectMappingJpaRepository mappings) {
        this.bindings = requireNonNull(bindings, "bindings");
        this.mappings = requireNonNull(mappings, "mappings");
    }

    @Override
    public Optional<ProviderBinding> current(
            String organizationRef,
            String domain) {
        return bindings
                .findByIdOrganizationRefAndIdDomainAndState(
                        organizationRef,
                        domain,
                        ProviderBinding.State.ACTIVE)
                .map(ProviderBindingJpaEntity::toDomain);
    }

    @Override
    public Optional<ProviderBinding> revision(
            String organizationRef,
            String domain,
            long revision) {
        return bindings.findById(new ProviderBindingId(organizationRef, domain, revision))
                .map(ProviderBindingJpaEntity::toDomain);
    }

    @Override
    @Transactional
    public ProviderBinding activate(
            String organizationRef,
            String domain,
            long expectedRevision,
            String adapterKey,
            String configurationRef,
            Instant activatedAt) {
        ProviderBindingJpaEntity current = bindings
                .lockActive(
                        organizationRef,
                        domain,
                        ProviderBinding.State.ACTIVE)
                .orElse(null);
        long actual = current == null ? 0 : current.revision();
        if (actual != expectedRevision) {
            throw new StaleProviderBindingException(
                    organizationRef,
                    domain,
                    expectedRevision,
                    actual);
        }
        if (current != null) {
            current.retire();
            // Release the unique active slot before inserting the successor revision.
            bindings.flush();
        }
        ProviderBindingJpaEntity activated = ProviderBindingJpaEntity.active(
                organizationRef,
                domain,
                actual + 1,
                adapterKey,
                configurationRef,
                activatedAt);
        return bindings.saveAndFlush(activated).toDomain();
    }

    @Override
    @Transactional
    public ProviderObjectMapping saveMapping(ProviderObjectMapping mapping) {
        ProviderObjectMapping safeMapping = requireNonNull(mapping, "mapping");
        ProviderObjectMappingId id = ProviderObjectMappingId.from(safeMapping);
        ProviderObjectMappingJpaEntity entity = mappings.findById(id)
                .orElseGet(() -> ProviderObjectMappingJpaEntity.create(id, safeMapping));
        entity.observe(safeMapping);
        return mappings.saveAndFlush(entity).toDomain();
    }

    @Override
    public Optional<ProviderObjectMapping> mappingByCanonicalId(
            String organizationRef,
            String domain,
            long bindingRevision,
            String canonicalObjectId) {
        return mappings.findById(new ProviderObjectMappingId(
                        organizationRef,
                        domain,
                        bindingRevision,
                        canonicalObjectId))
                .map(ProviderObjectMappingJpaEntity::toDomain);
    }

    @Override
    public Optional<ProviderObjectMapping> mappingByProviderRef(
            String organizationRef,
            String domain,
            long bindingRevision,
            String providerObjectRef) {
        return mappings
                .findByIdOrganizationRefAndIdDomainAndIdBindingRevisionAndProviderObjectRef(
                        organizationRef,
                        domain,
                        bindingRevision,
                        providerObjectRef)
                .map(ProviderObjectMappingJpaEntity::toDomain);
    }

    public static final class StaleProviderBindingException extends RuntimeException {
        public StaleProviderBindingException(
                String organizationRef,
                String domain,
                long expected,
                long actual) {
            super("provider binding changed for " + organizationRef + "/" + domain
                    + ": expected revision " + expected + " but found " + actual);
        }
    }
}
