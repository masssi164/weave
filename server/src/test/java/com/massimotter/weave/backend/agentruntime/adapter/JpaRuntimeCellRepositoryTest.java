package com.massimotter.weave.backend.agentruntime.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeCell;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeCellState;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeEntitlementState;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeMemberBinding;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadBinding;
import com.massimotter.weave.backend.agentruntime.port.StaleRuntimeCellException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class JpaRuntimeCellRepositoryTest {
  private static final Instant NOW = Instant.parse("2026-07-20T09:00:00Z");
  private static final String ACTIVE_ENTITLEMENT_REVISION = "sha256:" + "1".repeat(64);
  private static final String REVOKED_ENTITLEMENT_REVISION = "sha256:" + "2".repeat(64);

  private DataSource database;
  private JpaRuntimeCellRepository repository;

  @BeforeEach
  void setUp() {
    database =
        com.massimotter.weave.backend.testing.JpaTestDatabase
            .migratedDataSource("arc-cell");
    JdbcTemplate jdbc = new JdbcTemplate(database);
    insertEntitlement(jdbc, ACTIVE_ENTITLEMENT_REVISION, false);
    insertEntitlement(jdbc, REVOKED_ENTITLEMENT_REVISION, true);
    repository = AgentRuntimeJpaTestFactory.create(database).cells();
  }

  @Test
  void leaseAcquisitionIsIdempotentAndIncrementsFenceOnlyForANewLease() {
    repository.insert(cell("example"));
    UUID lease = UUID.randomUUID();

    RuntimeCell acquired =
        repository.acquireLease("cell:example", lease, NOW, NOW.plus(Duration.ofMinutes(1)));
    RuntimeCell repeated =
        repository.acquireLease(
            "cell:example", lease, NOW.plusSeconds(1), NOW.plus(Duration.ofMinutes(1)));

    assertThat(acquired.fencingEpoch()).isEqualTo(1);
    assertThat(repeated.fencingEpoch()).isEqualTo(1);
    assertThat(repeated.leaseId()).isEqualTo(lease);
    assertThatThrownBy(
            () ->
                repository.acquireLease(
                    "cell:example",
                    UUID.randomUUID(),
                    NOW.plusSeconds(2),
                    NOW.plus(Duration.ofMinutes(2))))
        .isInstanceOf(StaleRuntimeCellException.class)
        .hasMessageContaining("current lease");
  }

  @Test
  void expiredLeaseCanBeReplacedAndStaleWriterCannotObserve() {
    repository.insert(cell("example"));
    UUID oldLease = UUID.randomUUID();
    RuntimeCell first = repository.acquireLease("cell:example", oldLease, NOW, NOW.plusSeconds(5));
    UUID newLease = UUID.randomUUID();
    RuntimeCell second =
        repository.acquireLease("cell:example", newLease, NOW.plusSeconds(6), NOW.plusSeconds(60));

    assertThat(second.fencingEpoch()).isEqualTo(first.fencingEpoch() + 1);
    assertThatThrownBy(
            () ->
                repository.observe(
                    "cell:example",
                    oldLease,
                    first.fencingEpoch(),
                    RuntimeCellState.READY,
                    "audit:stale",
                    NOW.plusSeconds(7)))
        .isInstanceOf(StaleRuntimeCellException.class)
        .hasMessageContaining("rejected");

    RuntimeCell observed =
        repository.observe(
            "cell:example",
            newLease,
            second.fencingEpoch(),
            RuntimeCellState.MATERIALIZING,
            "audit:current",
            NOW.plusSeconds(7));
    assertThat(observed.observedState()).isEqualTo(RuntimeCellState.MATERIALIZING);
    assertThat(observed.auditRef()).isEqualTo("audit:current");
  }

  @Test
  void databaseRejectsAWorkloadBindingSharedAcrossCells() {
    repository.insert(cell("one"));
    RuntimeCell other = cell("two");
    RuntimeCell shared =
        new RuntimeCell(
            other.recordId(),
            other.organizationRef(),
            other.personRef(),
            other.memberBinding(),
            other.cellRef(),
            cell("one").workloadBinding(),
            other.entitlementState(),
            other.entitlementRevision(),
            other.desiredState(),
            other.observedState(),
            null,
            null,
            other.workspaceRevision(),
            other.workspaceManifestRef(),
            other.runtimeStateStoreRef(),
            0,
            null,
            null,
            0,
            other.auditRef(),
            other.createdAt(),
            other.updatedAt());

    assertThatThrownBy(() -> repository.insert(shared)).isInstanceOf(RuntimeException.class);
  }

  @Test
  void inventoryIsStableAndOrderedByCellReference() {
    RuntimeCell second = cell("two");
    RuntimeCell first = cell("one");
    repository.insert(second);
    repository.insert(first);

    assertThat(repository.findAll()).containsExactly(first, second);
  }

  @Test
  void revocationClearsTheLeaseAndFencesAllPriorWriters() {
    repository.insert(cell("example"));
    UUID lease = UUID.randomUUID();
    RuntimeCell acquired = repository.acquireLease("cell:example", lease, NOW, NOW.plusSeconds(60));

    RuntimeCell revoked =
        repository.revoke(
            "org:example",
            "person:example",
            REVOKED_ENTITLEMENT_REVISION,
            "audit:revoke",
            NOW.plusSeconds(1));

    assertThat(revoked.entitlementState()).isEqualTo(RuntimeEntitlementState.REVOKED);
    assertThat(revoked.desiredState()).isEqualTo(RuntimeCellState.REVOKING);
    assertThat(revoked.leaseId()).isNull();
    assertThat(revoked.fencingEpoch()).isEqualTo(acquired.fencingEpoch() + 1);
    assertThatThrownBy(
            () ->
                repository.observe(
                    "cell:example",
                    lease,
                    acquired.fencingEpoch(),
                    RuntimeCellState.READY,
                    "audit:stale",
                    NOW.plusSeconds(2)))
        .isInstanceOf(StaleRuntimeCellException.class);
  }

  @Test
  void desiredStateTransitionUsesVersionAndAllowedSourceAsACasBoundary() {
    RuntimeCell inserted = repository.insert(cell("example"));

    RuntimeCell starting =
        repository.transitionDesiredState(
            inserted.organizationRef(),
            inserted.personRef(),
            inserted.version(),
            Set.of(RuntimeCellState.PROVISIONING),
            RuntimeCellState.STARTING,
            "audit:start",
            NOW.plusSeconds(1));

    assertThat(starting.desiredState()).isEqualTo(RuntimeCellState.STARTING);
    assertThat(starting.version()).isEqualTo(inserted.version() + 1);
    assertThat(starting.auditRef()).isEqualTo("audit:start");
    assertThat(
            repository.transitionDesiredState(
                inserted.organizationRef(),
                inserted.personRef(),
                inserted.version(),
                Set.of(RuntimeCellState.PROVISIONING),
                RuntimeCellState.STARTING,
                "audit:replay",
                NOW.plusSeconds(2)))
        .isEqualTo(starting);
    assertThatThrownBy(
            () ->
                repository.transitionDesiredState(
                    inserted.organizationRef(),
                    inserted.personRef(),
                    starting.version(),
                    Set.of(RuntimeCellState.STOPPED),
                    RuntimeCellState.READY,
                    "audit:invalid",
                    NOW.plusSeconds(2)))
        .isInstanceOf(StaleRuntimeCellException.class);
  }

  @Test
  void revokedCellsCanStillEnterTheExplicitDeletionSidePath() {
    RuntimeCell inserted = repository.insert(cell("example"));
    RuntimeCell revoked =
        repository.revoke(
            inserted.organizationRef(),
            inserted.personRef(),
            REVOKED_ENTITLEMENT_REVISION,
            "audit:revoke",
            NOW.plusSeconds(1));

    RuntimeCell deleting =
        repository.transitionDesiredState(
            revoked.organizationRef(),
            revoked.personRef(),
            revoked.version(),
            Set.of(RuntimeCellState.REVOKING),
            RuntimeCellState.DELETING,
            "audit:delete",
            NOW.plusSeconds(2));

    assertThat(deleting.entitlementState()).isEqualTo(RuntimeEntitlementState.REVOKED);
    assertThat(deleting.desiredState()).isEqualTo(RuntimeCellState.DELETING);
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
        ACTIVE_ENTITLEMENT_REVISION,
        "workspace:1",
        "webdav-manifest:workspace:1",
        "runtime-state://org/example/person/" + id + "/state/1",
        "audit:" + id,
        NOW);
  }

  private static void insertEntitlement(JdbcTemplate jdbc, String revision, boolean revoked) {
    String digest = revision.substring("sha256:".length());
    jdbc.update(
        """
        insert into weave_agent_runtime_entitlements (
          record_id, entitlement_ref, entitlement_revision, organization_ref, person_ref,
          member_issuer, member_subject, source_provider, source_group_ref, capability_revision,
          entitlement_state, effective_at, last_observed_at, expires_at, revocation_ref,
          revoked_at, audit_ref, created_at, updated_at
        ) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """,
        UUID.randomUUID(),
        "entitlement:" + digest,
        revision,
        "org:example",
        "person:fixture",
        "https://auth.weave.test/realms/weave",
        "member-fixture",
        "keycloak",
        "sha256:" + "3".repeat(64),
        "sha256:" + "4".repeat(64),
        revoked ? "REVOKED" : "ENTITLED",
        Timestamp.from(NOW),
        Timestamp.from(NOW),
        Timestamp.from(NOW.plusSeconds(3600)),
        revoked ? "revocation:fixture" : null,
        revoked ? Timestamp.from(NOW.plusSeconds(1)) : null,
        "audit:entitlement",
        Timestamp.from(NOW),
        Timestamp.from(NOW));
  }
}
