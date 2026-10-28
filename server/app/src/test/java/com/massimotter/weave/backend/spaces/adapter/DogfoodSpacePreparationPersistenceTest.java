package com.massimotter.weave.backend.spaces.adapter;

import static org.assertj.core.api.Assertions.*;

import com.massimotter.weave.backend.spaces.port.DogfoodSpacePreparationPort;
import com.massimotter.weave.backend.spaces.port.SpaceMembershipAdministrationPort;
import com.massimotter.weave.backend.spaces.port.SpaceAccessPort.Permission;
import com.massimotter.weave.backend.testing.JpaTestDatabase;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DogfoodSpacePreparationPersistenceTest {
    private static final String OWNER = "acct_" + "0".repeat(32);
    private static final String A = "acct_" + "a".repeat(32);
    private static final String B = "acct_" + "b".repeat(32);
    private javax.sql.DataSource source;
    private SpaceJpaRepository roots;
    private SpaceMembershipJpaRepository members;
    private DogfoodSpacePreparationPort operator;
    private SpaceMembershipAdministrationPort admin;

    @BeforeEach void setup() {
        source = JpaTestDatabase.entityFirstDataSource("dogfood-operator-spaces");
        roots = JpaTestDatabase.repository(source, SpaceJpaRepository.class);
        members = JpaTestDatabase.repository(source, SpaceMembershipJpaRepository.class);
        var provision = JpaTestDatabase.transactional(source, new JpaSpaceProvisioningAdapter(roots, members));
        provision.provision("org", "workspace-default", "Current workspace", OWNER, Set.of());
        operator = JpaTestDatabase.transactional(source, new JpaDogfoodSpacePreparationAdapter(roots, members));
        admin = JpaTestDatabase.transactional(source, new JpaSpaceMembershipAdminAdapter(roots, members));
    }
    private static List<DogfoodSpacePreparationPort.Target> targets() {
        return List.of(new DogfoodSpacePreparationPort.Target(A, null), new DogfoodSpacePreparationPort.Target(B, null));
    }
    @Test void createsOnlyTwoAbsentEditGrantsAndVerifiesExactGrantsWithoutChangingVersionsOrOwner() {
        AtomicBoolean rechecked = new AtomicBoolean();
        var first = operator.grantTwo("org", OWNER, targets(), () -> rechecked.set(true), result -> {
            assertThat(rechecked).isTrue();
            assertThat(result).hasSize(2);
        });
        assertThat(first).allMatch(DogfoodSpacePreparationPort.Grant::created);
        var second = operator.grantTwo("org", OWNER, first.stream().map(g ->
                new DogfoodSpacePreparationPort.Target(g.accountRef(), g.strongEtag())).toList(), () -> {}, result -> {});
        assertThat(second).allMatch(g -> !g.created());
        assertThat(second.stream().map(DogfoodSpacePreparationPort.Grant::strongEtag)).containsExactly("\"sm-0\"", "\"sm-0\"");
        assertThat(members.findByIdOrganizationRefAndIdSpaceRef("org", "workspace-default")).hasSize(3);
        assertThat(admin.get("org", "workspace-default", OWNER, OWNER).strongEtag()).isEqualTo("\"sm-0\"");
        assertThat(roots.findById(new SpaceId("org", "workspace-default")).orElseThrow().displayName()).isEqualTo("Current workspace");
    }
    @Test void secondTargetRevokedOrBroaderConflictLeavesFirstAbsentAndAllExistingStateUnchanged() {
        members.saveAndFlush(SpaceMembershipJpaEntity.active("org", "workspace-default", B, "VIEW,EDIT,ADMIN", Instant.now()));
        assertThatThrownBy(() -> operator.grantTwo("org", OWNER, targets(), () -> {}, result -> {}))
                .isInstanceOf(DogfoodSpacePreparationPort.Conflict.class);
        assertThat(members.findByIdOrganizationRefAndIdSpaceRefAndIdPersonRef("org", "workspace-default", A)).isEmpty();
        var row = members.findByIdOrganizationRefAndIdSpaceRefAndIdPersonRef("org", "workspace-default", B).orElseThrow();
        row.revoke(Instant.now()); row = members.saveAndFlush(row);
        long version = row.version();
        assertThatThrownBy(() -> operator.grantTwo("org", OWNER, targets(), () -> {}, result -> {}))
                .isInstanceOf(DogfoodSpacePreparationPort.Conflict.class);
        var preserved = members.findByIdOrganizationRefAndIdSpaceRefAndIdPersonRef("org", "workspace-default", B).orElseThrow();
        assertThat(preserved.active()).isFalse(); assertThat(preserved.version()).isEqualTo(version);
        assertThat(members.findByIdOrganizationRefAndIdSpaceRefAndIdPersonRef("org", "workspace-default", A)).isEmpty();
    }
    @Test void identityOrAuditFailureRollsBackBothNewGrants() {
        assertThatThrownBy(() -> operator.grantTwo("org", OWNER, targets(), () -> { throw new IllegalStateException("identity changed"); }, result -> {}))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> operator.grantTwo("org", OWNER, targets(), () -> {}, result -> { throw new IllegalStateException("audit unavailable"); }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(members.findByIdOrganizationRefAndIdSpaceRef("org", "workspace-default")).hasSize(1);
    }

    @Test void verificationNeverCreatesMissingGrantsAndRetainsExactVersions() {
        var expected = List.of(new DogfoodSpacePreparationPort.Target(A, "\"sm-0\""),
                new DogfoodSpacePreparationPort.Target(B, "\"sm-0\""));
        assertThatThrownBy(() -> operator.verifyTwo("org", OWNER, expected, () -> {}))
                .isInstanceOf(DogfoodSpacePreparationPort.VersionConflict.class);
        assertThat(members.findByIdOrganizationRefAndIdSpaceRef("org", "workspace-default")).hasSize(1);
        operator.grantTwo("org", OWNER, targets(), () -> {}, result -> {});
        AtomicBoolean checked = new AtomicBoolean();
        assertThat(operator.verifyTwo("org", OWNER, expected, () -> checked.set(true)))
                .allMatch(grant -> !grant.created() && "\"sm-0\"".equals(grant.strongEtag()));
        assertThat(checked).isTrue();
        assertThatThrownBy(() -> operator.verifyTwo("org", OWNER, targets(), () -> {}))
                .isInstanceOf(DogfoodSpacePreparationPort.VersionConflict.class);
        var revoked = members.findByIdOrganizationRefAndIdSpaceRefAndIdPersonRef("org", "workspace-default", B).orElseThrow();
        revoked.revoke(Instant.now()); members.saveAndFlush(revoked);
        assertThatThrownBy(() -> operator.verifyTwo("org", OWNER, expected, () -> {}))
                .isInstanceOf(DogfoodSpacePreparationPort.Conflict.class);
        assertThat(admin.get("org", "workspace-default", OWNER, A).strongEtag()).isEqualTo("\"sm-0\"");
        assertThat(members.findByIdOrganizationRefAndIdSpaceRef("org", "workspace-default")).hasSize(3);
    }
    @Test void staleStrongVersionAndForeignOrganizationBlockWithoutRewriting() {
        var grants = operator.grantTwo("org", OWNER, targets(), () -> {}, result -> {});
        admin.grant("org", "workspace-default", OWNER, A, Set.of(Permission.VIEW, Permission.EDIT), grants.getFirst().strongEtag(), false);
        var expected = List.of(new DogfoodSpacePreparationPort.Target(A, "\"sm-0\""), new DogfoodSpacePreparationPort.Target(B, "\"sm-0\""));
        assertThatThrownBy(() -> operator.grantTwo("org", OWNER, expected, () -> {}, result -> {}))
                .isInstanceOf(DogfoodSpacePreparationPort.VersionConflict.class);
        assertThatThrownBy(() -> operator.grantTwo("foreign", OWNER, targets(), () -> {}, result -> {}))
                .isInstanceOf(DogfoodSpacePreparationPort.OwnerConflict.class);
        assertThat(admin.get("org", "workspace-default", OWNER, A).strongEtag()).isEqualTo("\"sm-1\"");
    }
    @Test void concurrentNormalRevocationWinsRootFenceAndLeavesOtherTargetAbsent() throws Exception {
        members.saveAndFlush(SpaceMembershipJpaEntity.active("org", "workspace-default", B, "VIEW,EDIT", Instant.now()));
        var held = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var attempted = new java.util.concurrent.CountDownLatch(1);
        try (var threads = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var revoke = threads.submit(() -> new org.springframework.transaction.support.TransactionTemplate(
                    JpaTestDatabase.transactionManager(source)).executeWithoutResult(status -> {
                var prior = admin.get("org", "workspace-default", OWNER, B);
                admin.revoke("org", "workspace-default", OWNER, B, prior.strongEtag());
                held.countDown();
                try { if (!release.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("test deadline"); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
            }));
            assertThat(held.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var prepare = threads.submit(() -> {
                attempted.countDown();
                return operator.grantTwo("org", OWNER, targets(), () -> {}, result -> {});
            });
            assertThat(attempted.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            try {
                assertThatThrownBy(() -> prepare.get(150, java.util.concurrent.TimeUnit.MILLISECONDS))
                        .isInstanceOf(java.util.concurrent.TimeoutException.class);
            } finally { release.countDown(); }
            revoke.get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertThatThrownBy(() -> prepare.get(10, java.util.concurrent.TimeUnit.SECONDS))
                    .hasCauseInstanceOf(DogfoodSpacePreparationPort.Conflict.class);
        } finally { release.countDown(); }
        assertThat(members.findByIdOrganizationRefAndIdSpaceRefAndIdPersonRef("org", "workspace-default", A)).isEmpty();
        assertThat(members.findByIdOrganizationRefAndIdSpaceRefAndIdPersonRef("org", "workspace-default", B).orElseThrow().active()).isFalse();
    }
    @Test void missingSpaceSuspendedSpaceAndRevokedOwnerNeverCreateOrRepair() {
        assertThatThrownBy(() -> operator.verifyOwner("missing", OWNER)).isInstanceOf(DogfoodSpacePreparationPort.OwnerConflict.class);
        var root = roots.findById(new SpaceId("org", "workspace-default")).orElseThrow();
        root.transition("SUSPENDED", Instant.now()); root = roots.saveAndFlush(root);
        assertThatThrownBy(() -> operator.grantTwo("org", OWNER, targets(), () -> {}, result -> {}))
                .isInstanceOf(DogfoodSpacePreparationPort.OwnerConflict.class);
        root.transition("ACTIVE", Instant.now()); root = roots.saveAndFlush(root);
        var owner = members.findByIdOrganizationRefAndIdSpaceRefAndIdPersonRef("org", "workspace-default", OWNER).orElseThrow();
        owner.revoke(Instant.now()); members.saveAndFlush(owner);
        assertThatThrownBy(() -> operator.grantTwo("org", OWNER, targets(), () -> {}, result -> {}))
                .isInstanceOf(DogfoodSpacePreparationPort.OwnerConflict.class);
        assertThat(members.findByIdOrganizationRefAndIdSpaceRef("org", "workspace-default")).hasSize(1);
    }
}
