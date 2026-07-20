package com.massimotter.weave.backend.agentruntime.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeCell;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeCellState;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeEntitlementState;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeMemberBinding;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadBinding;
import com.massimotter.weave.backend.agentruntime.port.StaleRuntimeCellException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class JdbcRuntimeCellRepositoryTest {
    private static final Instant NOW = Instant.parse("2026-07-20T09:00:00Z");

    private EmbeddedDatabase database;
    private JdbcRuntimeCellRepository repository;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("arc-" + UUID.randomUUID())
                .build();
        new ResourceDatabasePopulator(new ClassPathResource(
                "db/migration/V011__agent_runtime_control_foundation.sql")).execute(database);
        repository = new JdbcRuntimeCellRepository(new JdbcTemplate(database));
    }

    @Test
    void leaseAcquisitionIsIdempotentAndIncrementsFenceOnlyForANewLease() {
        repository.insert(cell("example"));
        UUID lease = UUID.randomUUID();

        RuntimeCell acquired = repository.acquireLease(
                "cell:example", lease, NOW, NOW.plus(Duration.ofMinutes(1)));
        RuntimeCell repeated = repository.acquireLease(
                "cell:example", lease, NOW.plusSeconds(1), NOW.plus(Duration.ofMinutes(1)));

        assertThat(acquired.fencingEpoch()).isEqualTo(1);
        assertThat(repeated.fencingEpoch()).isEqualTo(1);
        assertThat(repeated.leaseId()).isEqualTo(lease);
        assertThatThrownBy(() -> repository.acquireLease(
                "cell:example", UUID.randomUUID(), NOW.plusSeconds(2), NOW.plus(Duration.ofMinutes(2))))
                .isInstanceOf(StaleRuntimeCellException.class)
                .hasMessageContaining("current lease");
    }

    @Test
    void expiredLeaseCanBeReplacedAndStaleWriterCannotObserve() {
        repository.insert(cell("example"));
        UUID oldLease = UUID.randomUUID();
        RuntimeCell first = repository.acquireLease("cell:example", oldLease, NOW, NOW.plusSeconds(5));
        UUID newLease = UUID.randomUUID();
        RuntimeCell second = repository.acquireLease(
                "cell:example", newLease, NOW.plusSeconds(6), NOW.plusSeconds(60));

        assertThat(second.fencingEpoch()).isEqualTo(first.fencingEpoch() + 1);
        assertThatThrownBy(() -> repository.observe(
                "cell:example", oldLease, first.fencingEpoch(), RuntimeCellState.READY,
                "audit:stale", NOW.plusSeconds(7)))
                .isInstanceOf(StaleRuntimeCellException.class)
                .hasMessageContaining("rejected");

        RuntimeCell observed = repository.observe(
                "cell:example", newLease, second.fencingEpoch(), RuntimeCellState.MATERIALIZING,
                "audit:current", NOW.plusSeconds(7));
        assertThat(observed.observedState()).isEqualTo(RuntimeCellState.MATERIALIZING);
        assertThat(observed.auditRef()).isEqualTo("audit:current");
    }

    @Test
    void databaseRejectsAWorkloadBindingSharedAcrossCells() {
        repository.insert(cell("one"));
        RuntimeCell other = cell("two");
        RuntimeCell shared = new RuntimeCell(
                other.recordId(), other.organizationRef(), other.personRef(), other.memberBinding(), other.cellRef(),
                cell("one").workloadBinding(), other.entitlementState(), other.entitlementRevision(),
                other.desiredState(), other.observedState(), null, null, other.workspaceRevision(),
                other.workspaceManifestRef(), other.runtimeStateStoreRef(), 0, null, null, 0, other.auditRef(),
                other.createdAt(), other.updatedAt());

        assertThatThrownBy(() -> repository.insert(shared)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void revocationClearsTheLeaseAndFencesAllPriorWriters() {
        repository.insert(cell("example"));
        UUID lease = UUID.randomUUID();
        RuntimeCell acquired = repository.acquireLease(
                "cell:example", lease, NOW, NOW.plusSeconds(60));

        RuntimeCell revoked = repository.revoke(
                "org:example", "person:example", "entitlement:revoked:2", "audit:revoke", NOW.plusSeconds(1));

        assertThat(revoked.entitlementState()).isEqualTo(RuntimeEntitlementState.REVOKED);
        assertThat(revoked.desiredState()).isEqualTo(RuntimeCellState.REVOKING);
        assertThat(revoked.leaseId()).isNull();
        assertThat(revoked.fencingEpoch()).isEqualTo(acquired.fencingEpoch() + 1);
        assertThatThrownBy(() -> repository.observe(
                "cell:example", lease, acquired.fencingEpoch(), RuntimeCellState.READY,
                "audit:stale", NOW.plusSeconds(2)))
                .isInstanceOf(StaleRuntimeCellException.class);
    }

    private static RuntimeCell cell(String id) {
        return RuntimeCell.provisioning(
                "org:example",
                "person:" + id,
                new RuntimeMemberBinding("https://auth.weave.test/realms/weave", "member-" + id),
                "cell:" + id,
                new RuntimeWorkloadBinding(
                        "https://auth.weave.test/realms/weave",
                        "service-account-weaver-cell-" + id,
                        "weaver-cell-" + id,
                        RuntimeWorkloadBinding.AuthenticationMethod.PRIVATE_KEY_JWT,
                        "credentialref://weave/runtime/" + id),
                "entitlement:1",
                "workspace:1",
                "webdav-manifest:workspace:1",
                "runtime-state://org/example/person/" + id + "/state/1",
                "audit:" + id,
                NOW);
    }
}
