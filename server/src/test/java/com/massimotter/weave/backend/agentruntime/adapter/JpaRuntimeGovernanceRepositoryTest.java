package com.massimotter.weave.backend.agentruntime.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeAuditCorrelation;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeCell;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeEntitlementObservation;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeEntitlementRef;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeEntitlementState;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeMemberBinding;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeRevocation;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadBinding;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadOwnership;
import com.massimotter.weave.backend.testing.JpaTestDatabase;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class JpaRuntimeGovernanceRepositoryTest {
    private static final Instant NOW = Instant.parse("2026-07-20T09:00:00Z");
    private static final String HASH_ONE = "sha256:" + "1".repeat(64);
    private static final String HASH_TWO = "sha256:" + "2".repeat(64);
    private static final String HASH_THREE = "sha256:" + "3".repeat(64);

    private JdbcTemplate jdbc;
    private JpaRuntimeGovernanceRepository repository;
    private JpaRuntimeCellRepository cells;

    @BeforeEach
    void setUp() {
        var database = JpaTestDatabase.entityFirstDataSource("arc-governance");
        jdbc = new JdbcTemplate(database);
        var persistence = AgentRuntimeJpaTestFactory.create(database);
        repository = persistence.governance();
        cells = persistence.cells();
    }

    @Test
    void repeatedAuthorityObservationReusesTheFactAndExtendsItsBoundedValidity() {
        RuntimeEntitlementRef first = repository.activate(
                observation(NOW, NOW.plusSeconds(120)), "idempotency-key-0001", "correlation:first", NOW);
        RuntimeEntitlementRef refreshed = repository.activate(
                observation(NOW.plusSeconds(60), NOW.plusSeconds(300)),
                "a-different-command-key", "correlation:refresh", NOW.plusSeconds(60));

        assertThat(refreshed.entitlementRef()).isEqualTo(first.entitlementRef());
        assertThat(refreshed.entitlementRevision()).isEqualTo(first.entitlementRevision());
        assertThat(refreshed.effectiveAt()).isEqualTo(NOW);
        assertThat(refreshed.lastObservedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(refreshed.expiresAt()).isEqualTo(NOW.plusSeconds(300));
        assertThat(jdbc.queryForObject("select count(*) from weave_agent_runtime_entitlements", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void revocationIsAppendOnlyAndARegrantedMembershipMintsANewEntitlementIncarnation() {
        RuntimeEntitlementRef first = repository.activate(
                observation(NOW, NOW.plusSeconds(120)), "idempotency-key-0001", "correlation:first", NOW);
        RuntimeCell cell = cells.insert(cell(first));
        RuntimeAuditCorrelation correlation = repository.appendCorrelation(correlation(correlationRef('a')));
        RuntimeRevocation revoked = repository.revoke(
                first, cell.cellRef(), null, HASH_TWO, "membership-revoked", HASH_THREE, HASH_ONE,
                "revocation:" + "a".repeat(64), correlation.correlationRef(), NOW.plusSeconds(10));
        RuntimeRevocation replay = repository.revoke(
                first, cell.cellRef(), null, HASH_TWO, "membership-revoked", HASH_THREE, HASH_ONE,
                revoked.revocationRef(), correlation.correlationRef(), NOW.plusSeconds(10));

        RuntimeEntitlementRef replacement = repository.activate(
                observation(NOW.plusSeconds(20), NOW.plusSeconds(320)),
                "idempotency-key-0003", "correlation:replacement", NOW.plusSeconds(20));

        assertThat(replay).isEqualTo(revoked);
        assertThat(revoked.reasonRefHash()).isEqualTo(HASH_THREE);
        assertThat(repository.findEffectiveRevision(
                first.organizationRef(), first.personRef(), first.entitlementRevision(), NOW.plusSeconds(20)))
                .isEmpty();
        assertThat(replacement.state()).isEqualTo(RuntimeEntitlementState.ENTITLED);
        assertThat(replacement.entitlementRevision()).isNotEqualTo(first.entitlementRevision());
        assertThat(jdbc.queryForObject("select count(*) from weave_agent_runtime_revocations", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void oneSupportCorrelationReferenceCannotBeReboundToOtherEvidence() {
        RuntimeAuditCorrelation first = repository.appendCorrelation(correlation(correlationRef('b')));
        RuntimeAuditCorrelation replay = repository.appendCorrelation(correlation(correlationRef('b')));

        assertThat(replay.correlationRef()).isEqualTo(first.correlationRef());
        RuntimeAuditCorrelation conflicting = new RuntimeAuditCorrelation(
                UUID.randomUUID(), first.correlationRef(), HASH_ONE, HASH_TWO, null,
                RuntimeWorkloadOwnership.fingerprint("different-cell"), null, null, null, HASH_ONE, NOW, NOW);
        assertThatThrownBy(() -> repository.appendCorrelation(conflicting))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("different evidence");
    }

    private static RuntimeEntitlementObservation observation(Instant observedAt, Instant expiresAt) {
        return new RuntimeEntitlementObservation(
                "org:example", "person:example",
                new RuntimeMemberBinding("https://auth.weave.test/realms/weave", "member-1"),
                "keycloak", HASH_ONE, HASH_TWO, observedAt, expiresAt);
    }

    private static RuntimeAuditCorrelation correlation(String correlationRef) {
        return new RuntimeAuditCorrelation(
                UUID.randomUUID(), correlationRef, HASH_ONE, HASH_TWO, HASH_ONE, HASH_TWO,
                null, null, null, HASH_ONE, NOW, NOW);
    }

    private static String correlationRef(char value) {
        return "correlation:" + String.valueOf(value).repeat(64);
    }

    private static RuntimeCell cell(RuntimeEntitlementRef entitlement) {
        return RuntimeCell.provisioning(
                entitlement.organizationRef(), entitlement.personRef(), entitlement.memberBinding(),
                "cell:example",
                new RuntimeWorkloadBinding(
                        "https://auth.weave.test/realms/weave", "service-account-1", "weaver-cell-example",
                        RuntimeWorkloadBinding.AuthenticationMethod.PRIVATE_KEY_JWT,
                        "credentialref://weave/runtime/example"),
                entitlement.entitlementRevision(), "workspace:1", "webdav-manifest:workspace:1",
                "runtime-state://org/example/person/example/state/1", "correlation:first", NOW);
    }
}
