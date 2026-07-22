package com.massimotter.weave.backend.providerbinding.adapter;

import com.massimotter.weave.backend.providerbinding.domain.ProviderBinding;
import com.massimotter.weave.backend.providerbinding.domain.ProviderObjectMapping;
import com.massimotter.weave.backend.providerbinding.port.ProviderBindingRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public final class JdbcProviderBindingRepository implements ProviderBindingRepository {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public JdbcProviderBindingRepository(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = java.util.Objects.requireNonNull(jdbc, "jdbc must not be null");
        this.transactions = new TransactionTemplate(
                java.util.Objects.requireNonNull(transactionManager, "transactionManager must not be null"));
    }

    @Override
    public Optional<ProviderBinding> current(String organizationRef, String domain) {
        return jdbc.query("""
                select * from weave_provider_bindings
                where organization_ref = ? and domain_key = ? and binding_state = 'ACTIVE'
                """, this::binding, organizationRef, domain).stream().findFirst();
    }

    @Override
    public Optional<ProviderBinding> revision(String organizationRef, String domain, long revision) {
        return jdbc.query("""
                select * from weave_provider_bindings
                where organization_ref = ? and domain_key = ? and binding_revision = ?
                """, this::binding, organizationRef, domain, revision).stream().findFirst();
    }

    @Override
    public ProviderBinding activate(
            String organizationRef,
            String domain,
            long expectedRevision,
            String adapterKey,
            String configurationRef,
            Instant activatedAt) {
        return transactions.execute(status -> {
            ProviderBinding current = currentForUpdate(organizationRef, domain);
            long actual = current == null ? 0 : current.revision();
            if (actual != expectedRevision) {
                throw new StaleProviderBindingException(organizationRef, domain, expectedRevision, actual);
            }
            if (current != null) {
                jdbc.update("""
                        update weave_provider_bindings set binding_state = 'RETIRED', active_slot = null
                        where organization_ref = ? and domain_key = ? and binding_revision = ?
                        """, organizationRef, domain, current.revision());
            }
            long next = actual + 1;
            jdbc.update("""
                    insert into weave_provider_bindings
                      (organization_ref, domain_key, binding_revision, adapter_key, configuration_ref,
                       binding_state, active_slot, activated_at_utc)
                    values (?, ?, ?, ?, ?, 'ACTIVE', true, ?)
                    """, organizationRef, domain, next, adapterKey, configurationRef, timestamp(activatedAt));
            return revision(organizationRef, domain, next).orElseThrow();
        });
    }

    @Override
    public ProviderObjectMapping saveMapping(ProviderObjectMapping mapping) {
        jdbc.update("""
                insert into weave_provider_object_mappings
                  (organization_ref, domain_key, binding_revision, canonical_object_id, provider_object_ref,
                   provenance, first_observed_at_utc, last_observed_at_utc)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (organization_ref, domain_key, binding_revision, canonical_object_id)
                do update set provider_object_ref = excluded.provider_object_ref,
                  provenance = excluded.provenance, last_observed_at_utc = excluded.last_observed_at_utc
                """, mapping.organizationRef(), mapping.domain(), mapping.bindingRevision(), mapping.canonicalObjectId(),
                mapping.providerObjectRef(), mapping.provenance(), timestamp(mapping.firstObservedAt()),
                timestamp(mapping.lastObservedAt()));
        return mappingByCanonicalId(
                mapping.organizationRef(), mapping.domain(), mapping.bindingRevision(), mapping.canonicalObjectId())
                .orElseThrow();
    }

    @Override
    public Optional<ProviderObjectMapping> mappingByCanonicalId(
            String organizationRef, String domain, long bindingRevision, String canonicalObjectId) {
        return mapping("canonical_object_id", organizationRef, domain, bindingRevision, canonicalObjectId);
    }

    @Override
    public Optional<ProviderObjectMapping> mappingByProviderRef(
            String organizationRef, String domain, long bindingRevision, String providerObjectRef) {
        return mapping("provider_object_ref", organizationRef, domain, bindingRevision, providerObjectRef);
    }

    private Optional<ProviderObjectMapping> mapping(
            String column, String organizationRef, String domain, long bindingRevision, String value) {
        return jdbc.query("select * from weave_provider_object_mappings where organization_ref = ? "
                        + "and domain_key = ? and binding_revision = ? and " + column + " = ?",
                this::mapping, organizationRef, domain, bindingRevision, value).stream().findFirst();
    }

    private ProviderBinding currentForUpdate(String organizationRef, String domain) {
        return jdbc.query("""
                select * from weave_provider_bindings
                where organization_ref = ? and domain_key = ? and binding_state = 'ACTIVE' for update
                """, this::binding, organizationRef, domain).stream().findFirst().orElse(null);
    }

    private ProviderBinding binding(ResultSet rs, int row) throws SQLException {
        return new ProviderBinding(
                rs.getString("organization_ref"), rs.getString("domain_key"), rs.getLong("binding_revision"),
                rs.getString("adapter_key"), rs.getString("configuration_ref"),
                ProviderBinding.State.valueOf(rs.getString("binding_state")),
                rs.getObject("activated_at_utc", OffsetDateTime.class).toInstant());
    }

    private ProviderObjectMapping mapping(ResultSet rs, int row) throws SQLException {
        return new ProviderObjectMapping(
                rs.getString("organization_ref"), rs.getString("domain_key"), rs.getLong("binding_revision"),
                rs.getString("canonical_object_id"), rs.getString("provider_object_ref"), rs.getString("provenance"),
                rs.getObject("first_observed_at_utc", OffsetDateTime.class).toInstant(),
                rs.getObject("last_observed_at_utc", OffsetDateTime.class).toInstant());
    }

    private OffsetDateTime timestamp(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    public static final class StaleProviderBindingException extends RuntimeException {
        public StaleProviderBindingException(String organizationRef, String domain, long expected, long actual) {
            super("provider binding changed for " + organizationRef + "/" + domain
                    + ": expected revision " + expected + " but found " + actual);
        }
    }
}
