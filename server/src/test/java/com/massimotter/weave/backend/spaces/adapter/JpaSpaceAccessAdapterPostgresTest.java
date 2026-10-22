package com.massimotter.weave.backend.spaces.adapter;

import com.massimotter.weave.backend.spaces.port.SpaceAccessPort.Permission;
import com.massimotter.weave.backend.spaces.port.SpaceProvisioningPort;
import com.massimotter.weave.backend.spaces.port.DefaultSpacePreparationPort;
import com.massimotter.weave.backend.testing.JpaTestDatabase;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.transaction.support.TransactionTemplate;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class JpaSpaceAccessAdapterPostgresTest {
    @Container private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");
    private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");

    @Test
    void legacyNameMigrationPreservesReferencesGrantsAndMaterializedFileRelationships() {
        String schema = "legacy_names_" + java.util.UUID.randomUUID().toString().replace("-", "");
        String separator = POSTGRES.getJdbcUrl().contains("?") ? "&" : "?";
        var source = new DriverManagerDataSource(POSTGRES.getJdbcUrl() + separator + "currentSchema=" + schema,
                POSTGRES.getUsername(), POSTGRES.getPassword());
        source.setDriverClassName(POSTGRES.getDriverClassName());
        Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).target("13")
                .locations("classpath:db/migration").load().migrate();
        var sql = new org.springframework.jdbc.core.JdbcTemplate(source);
        for (String[] key : new String[][] {{"org:a", "workspace-default"}, {"org:a", "space:z"},
                {"org:a", "space:a"}, {"org:b", "space:a"}}) {
            sql.update("insert into weave_spaces (organization_ref, space_ref, lifecycle_state, created_at_utc, updated_at_utc, version) values (?, ?, 'ACTIVE', ?, ?, 7)",
                    key[0], key[1], java.time.OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC), java.time.OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
            sql.update("insert into weave_space_memberships (organization_ref, space_ref, person_ref, permission_set, lifecycle_state, created_at_utc, updated_at_utc, version) values (?, ?, 'owner', 'VIEW,EDIT,ADMIN', 'ACTIVE', ?, ?, 3)",
                    key[0], key[1], java.time.OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC), java.time.OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
        }
        sql.update("insert into weave_files_user_resources (organization_ref, file_id, binding_revision, space_ref, parent_file_id, canonical_path, active_path_key, object_kind, owner_principal_ref, lifecycle_state, created_at_utc, modified_at_utc, version, visibility_scope) values ('org:a', 'file:kept', 1, 'space:a', 'root', '/kept.txt', '/kept.txt', 'FILE', 'owner', 'ACTIVE', ?, ?, 4, 'PRIVATE')",
                java.time.OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC), java.time.OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
        var rootsBefore = sql.queryForList("select organization_ref, space_ref, lifecycle_state, created_at_utc, updated_at_utc, version from weave_spaces order by organization_ref, space_ref");
        var grantsBefore = sql.queryForList("select * from weave_space_memberships order by organization_ref, space_ref, person_ref");
        var relationshipsBefore = sql.queryForList("select * from weave_files_user_resources");
        var migrations = Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").load();
        migrations.migrate();
        JpaTestDatabase.validateSchema(source);
        assertThat(sql.queryForList("select display_name from weave_spaces where organization_ref = 'org:a' order by space_ref", String.class))
                .containsExactly("Space 1", "Space 2", "Organization workspace");
        assertThat(sql.queryForObject("select display_name from weave_spaces where organization_ref = 'org:b'", String.class)).isEqualTo("Space 1");
        assertThat(sql.queryForList("select organization_ref, space_ref, lifecycle_state, created_at_utc, updated_at_utc, version from weave_spaces order by organization_ref, space_ref")).isEqualTo(rootsBefore);
        assertThat(sql.queryForList("select * from weave_space_memberships order by organization_ref, space_ref, person_ref")).isEqualTo(grantsBefore);
        assertThat(sql.queryForList("select * from weave_files_user_resources")).isEqualTo(relationshipsBefore);
        sql.update("update weave_spaces set display_name = 'Existing reviewed name' where organization_ref = 'org:a' and space_ref = 'workspace-default'");
        migrations.migrate();
        var roots = JpaTestDatabase.repository(source, SpaceJpaRepository.class);
        var members = JpaTestDatabase.repository(source, SpaceMembershipJpaRepository.class);
        var prepare = JpaTestDatabase.transactional(source, new JpaSpaceProvisioningAdapter(roots, members));
        assertThat(prepare.prepare("org:a", "owner")).isEqualTo(DefaultSpacePreparationPort.Result.VERIFIED);
        assertThat(new JpaSpaceAccessAdapter(roots, members).visibleSpace("org:a", "owner", "workspace-default").orElseThrow().displayName())
                .isEqualTo("Existing reviewed name");
        assertThat(sql.queryForList("select * from weave_space_memberships order by organization_ref, space_ref, person_ref")).isEqualTo(grantsBefore);
        assertThat(sql.queryForList("select * from weave_files_user_resources")).isEqualTo(relationshipsBefore);
    }

    @Test
    void concurrentAdminAndBootstrapCreationCannotMixMembershipIntent() throws Exception {
        var source = migratedSource();
        var spaces = JpaTestDatabase.repository(source, SpaceJpaRepository.class);
        var members = JpaTestDatabase.repository(source, SpaceMembershipJpaRepository.class);
        var adapter = JpaTestDatabase.transactional(source, new JpaSpaceProvisioningAdapter(spaces, members));
        var transactions = new TransactionTemplate(JpaTestDatabase.transactionManager(source));
        for (boolean bootstrapWins : new boolean[] {false, true}) {
            String organization = "org:creation-race-" + bootstrapWins;
            var winnerWritten = new CountDownLatch(1);
            var releaseWinner = new CountDownLatch(1);
            var loserStarted = new CountDownLatch(1);
            try (var threads = Executors.newFixedThreadPool(2)) {
                var winner = threads.submit(() -> transactions.execute(status -> {
                    if (bootstrapWins) {
                        assertThat(adapter.prepare(organization, "owner:a"))
                                .isEqualTo(DefaultSpacePreparationPort.Result.CREATED);
                    } else {
                        assertThat(adapter.provision(organization, "workspace-default", "Organization workspace", "admin:b", Set.of("member:c")))
                                .isEqualTo(SpaceProvisioningPort.Result.CREATED);
                    }
                    JpaTestDatabase.entityManager(source).flush();
                    winnerWritten.countDown();
                    await(releaseWinner);
                    return true;
                }));
                assertThat(winnerWritten.await(10, TimeUnit.SECONDS)).isTrue();
                var loser = threads.submit(() -> {
                    loserStarted.countDown();
                    if (bootstrapWins) {
                        assertThatThrownBy(() -> adapter.provision(organization, "workspace-default", "Organization workspace", "admin:b", Set.of("member:c")))
                                .isInstanceOf(SpaceProvisioningPort.Conflict.class);
                    } else {
                        assertThatThrownBy(() -> adapter.prepare(organization, "owner:a"))
                                .isInstanceOf(DefaultSpacePreparationPort.Conflict.class);
                    }
                    return true;
                });
                assertThat(loserStarted.await(10, TimeUnit.SECONDS)).isTrue();
                try {
                    assertThatThrownBy(() -> loser.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                } finally {
                    releaseWinner.countDown();
                }
                assertThat(winner.get(10, TimeUnit.SECONDS)).isTrue();
                assertThat(loser.get(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                releaseWinner.countDown();
            }
            Map<String, String> current = members.findByIdOrganizationRefAndIdSpaceRef(organization, "workspace-default")
                    .stream().collect(java.util.stream.Collectors.toMap(
                            SpaceMembershipJpaEntity::accountRef, SpaceMembershipJpaEntity::permissionSet));
            assertThat(current).isEqualTo(bootstrapWins ? Map.of("owner:a", "VIEW,EDIT,ADMIN")
                    : Map.of("admin:b", "VIEW,EDIT,ADMIN", "member:c", "VIEW"));
            assertThat(members.findByIdOrganizationRefAndIdSpaceRef(organization, "workspace-default"))
                    .allSatisfy(member -> assertThat(member.version()).isZero());
        }
    }

    @Test
    void preparationWaitsForCurrentRevocationAndSuspensionRatherThanVerifyingStaleAccess() throws Exception {
        var source = migratedSource();
        var spaces = JpaTestDatabase.repository(source, SpaceJpaRepository.class);
        var members = JpaTestDatabase.repository(source, SpaceMembershipJpaRepository.class);
        var adapter = JpaTestDatabase.transactional(source, new JpaSpaceProvisioningAdapter(spaces, members));
        var administration = JpaTestDatabase.transactional(source, new JpaSpaceMembershipAdminAdapter(spaces, members));
        var transactions = new TransactionTemplate(JpaTestDatabase.transactionManager(source));
        for (boolean suspend : new boolean[] {false, true}) {
            String organization = "org:access-race-" + suspend;
            adapter.prepare(organization, "owner:a");
            members.saveAndFlush(SpaceMembershipJpaEntity.active(
                    organization, "workspace-default", "admin:b", "VIEW,EDIT,ADMIN", NOW));
            String etag = administration.get(organization, "workspace-default", "admin:b", "owner:a").strongEtag();
            var changeWritten = new CountDownLatch(1);
            var releaseChange = new CountDownLatch(1);
            var prepareStarted = new CountDownLatch(1);
            try (var threads = Executors.newFixedThreadPool(2)) {
                var change = threads.submit(() -> transactions.execute(status -> {
                    if (suspend) {
                        var root = spaces.lockById(new SpaceId(organization, "workspace-default")).orElseThrow();
                        root.transition("SUSPENDED", NOW.plusSeconds(1));
                        spaces.saveAndFlush(root);
                    } else {
                        administration.revoke(organization, "workspace-default", "admin:b", "owner:a", etag);
                    }
                    changeWritten.countDown();
                    await(releaseChange);
                    return true;
                }));
                assertThat(changeWritten.await(10, TimeUnit.SECONDS)).isTrue();
                var preparation = threads.submit(() -> {
                    prepareStarted.countDown();
                    assertThatThrownBy(() -> adapter.prepare(organization, "owner:a"))
                            .isInstanceOf(DefaultSpacePreparationPort.Conflict.class);
                    return true;
                });
                assertThat(prepareStarted.await(10, TimeUnit.SECONDS)).isTrue();
                try {
                    assertThatThrownBy(() -> preparation.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                } finally {
                    releaseChange.countDown();
                }
                assertThat(change.get(10, TimeUnit.SECONDS)).isTrue();
                assertThat(preparation.get(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                releaseChange.countDown();
            }
            if (suspend) {
                assertThat(spaces.findById(new SpaceId(organization, "workspace-default")).orElseThrow().active()).isFalse();
            } else {
                var owner = members.findByIdOrganizationRefAndIdSpaceRefAndIdPersonRef(
                        organization, "workspace-default", "owner:a").orElseThrow();
                assertThat(owner.active()).isFalse();
                assertThat(owner.version()).isEqualTo(1);
            }
            assertThat(members.findByIdOrganizationRefAndIdSpaceRefAndIdPersonRef(
                    organization, "workspace-default", "admin:b").orElseThrow().version()).isZero();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Concurrent Space operation was not released");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    private static DriverManagerDataSource migratedSource() {
        var source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        source.setDriverClassName(POSTGRES.getDriverClassName());
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        JpaTestDatabase.validateSchema(source);
        return source;
    }

    @Test
    void explicitDevelopmentPreparationCreatesOnceAndNeverChangesExistingMemberships() {
        DriverManagerDataSource source = new DriverManagerDataSource();
        source.setDriverClassName(POSTGRES.getDriverClassName());
        source.setUrl(POSTGRES.getJdbcUrl());
        source.setUsername(POSTGRES.getUsername());
        source.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        JpaTestDatabase.validateSchema(source);
        SpaceJpaRepository spaces = JpaTestDatabase.repository(source, SpaceJpaRepository.class);
        SpaceMembershipJpaRepository members = JpaTestDatabase.repository(source, SpaceMembershipJpaRepository.class);
        var prepare = JpaTestDatabase.transactional(source,
                new JpaSpaceProvisioningAdapter(spaces, members, Clock.fixed(NOW, ZoneOffset.UTC)));
        String owner = "acct_55555555555555555555555555555555";
        String peer = "acct_66666666666666666666666666666666";
        String organization = "org:physical-preparation";
        assertThat(prepare.prepare(organization, owner)).isEqualTo(DefaultSpacePreparationPort.Result.CREATED);
        var access = JpaTestDatabase.transactional(source, new JpaSpaceAccessAdapter(spaces, members));
        assertThat(access.allows(organization, "workspace-default", owner, Permission.ADMIN)).isTrue();
        long ownerVersion = members.findByIdOrganizationRefAndIdSpaceRefAndIdPersonRef(
                organization, "workspace-default", owner).orElseThrow().version();
        members.saveAndFlush(SpaceMembershipJpaEntity.active(organization, "workspace-default", peer, "VIEW", NOW));
        assertThat(prepare.prepare(organization, owner)).isEqualTo(DefaultSpacePreparationPort.Result.VERIFIED);
        var restarted = JpaTestDatabase.transactional(source,
                new JpaSpaceProvisioningAdapter(spaces, members, Clock.fixed(NOW.plusSeconds(10), ZoneOffset.UTC)));
        assertThat(restarted.prepare(organization, owner)).isEqualTo(DefaultSpacePreparationPort.Result.VERIFIED);
        assertThat(members.findByIdOrganizationRefAndIdSpaceRefAndIdPersonRef(
                organization, "workspace-default", owner).orElseThrow().version()).isEqualTo(ownerVersion);
        assertThat(access.allows(organization, "workspace-default", peer, Permission.VIEW)).isTrue();
        var revokedOwner = members.findByIdOrganizationRefAndIdSpaceRefAndIdPersonRef(
                organization, "workspace-default", owner).orElseThrow();
        revokedOwner.revoke(NOW.plusSeconds(20));
        members.saveAndFlush(revokedOwner);
        long revokedVersion = members.findByIdOrganizationRefAndIdSpaceRefAndIdPersonRef(
                organization, "workspace-default", owner).orElseThrow().version();
        assertThatThrownBy(() -> restarted.prepare(organization, owner))
                .isInstanceOf(DefaultSpacePreparationPort.Conflict.class);
        assertThat(members.findByIdOrganizationRefAndIdSpaceRefAndIdPersonRef(
                organization, "workspace-default", owner).orElseThrow().version()).isEqualTo(revokedVersion);
        assertThat(access.allows(organization, "workspace-default", owner, Permission.ADMIN)).isFalse();
        assertThat(access.allows(organization, "workspace-default", peer, Permission.VIEW)).isTrue();

        spaces.saveAndFlush(SpaceJpaEntity.active("org:no-owner-grant", "workspace-default", NOW));
        assertThatThrownBy(() -> restarted.prepare("org:no-owner-grant", owner))
                .isInstanceOf(DefaultSpacePreparationPort.Conflict.class);
        assertThat(members.findByIdOrganizationRefAndIdSpaceRefAndIdPersonRef(
                "org:no-owner-grant", "workspace-default", owner)).isEmpty();

        restarted.prepare("org:suspended-preparation", owner);
        var suspended = spaces.findById(new SpaceId("org:suspended-preparation", "workspace-default")).orElseThrow();
        suspended.transition("SUSPENDED", NOW.plusSeconds(1));
        spaces.saveAndFlush(suspended);
        assertThatThrownBy(() -> restarted.prepare("org:suspended-preparation", owner))
                .isInstanceOf(DefaultSpacePreparationPort.Conflict.class);
        assertThat(spaces.findById(new SpaceId("org:suspended-preparation", "workspace-default"))
                .orElseThrow().active()).isFalse();
    }

    @Test
    void currentDurableMembershipControlsVisibilityAcrossTenantRevocationAndRestart() {
        DriverManagerDataSource source = new DriverManagerDataSource();
        source.setDriverClassName(POSTGRES.getDriverClassName());
        source.setUrl(POSTGRES.getJdbcUrl());
        source.setUsername(POSTGRES.getUsername());
        source.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        JpaTestDatabase.validateSchema(source);

        SpaceJpaRepository spaces = JpaTestDatabase.repository(source, SpaceJpaRepository.class);
        SpaceMembershipJpaRepository members =
                JpaTestDatabase.repository(source, SpaceMembershipJpaRepository.class);
        spaces.saveAndFlush(SpaceJpaEntity.active("org:a", "workspace-default", NOW));
        spaces.saveAndFlush(SpaceJpaEntity.active("org:a", "team:second", NOW));
        spaces.saveAndFlush(SpaceJpaEntity.active("org:b", "workspace-default", NOW));
        members.saveAndFlush(SpaceMembershipJpaEntity.active(
                "org:a", "workspace-default", "user:alice", "VIEW,EDIT", NOW));
        members.saveAndFlush(SpaceMembershipJpaEntity.active(
                "org:a", "team:second", "user:alice", "VIEW", NOW));
        members.saveAndFlush(SpaceMembershipJpaEntity.active(
                "org:b", "workspace-default", "user:bob", "VIEW,EDIT,ADMIN", NOW));

        var access = JpaTestDatabase.transactional(source, new JpaSpaceAccessAdapter(spaces, members));
        assertThat(access.visibleSpaceRefs("org:a", "user:alice", "", 100))
                .containsExactly("team:second", "workspace-default");
        assertThat(access.visibleSpaceRefs("org:a", "user:alice", "team:second", 100))
                .containsExactly("workspace-default");
        assertThat(access.allows("org:a", "workspace-default", "user:alice", Permission.EDIT)).isTrue();
        assertThat(access.allows("org:a", "team:second", "user:alice", Permission.EDIT)).isFalse();
        assertThat(access.allows("org:b", "workspace-default", "user:alice", Permission.VIEW)).isFalse();
        assertThat(access.allows("org:a", "workspace-default", "user:bob", Permission.VIEW)).isFalse();

        var membership = members.findByIdOrganizationRefAndIdSpaceRefAndIdPersonRef(
                "org:a", "workspace-default", "user:alice").orElseThrow();
        membership.revoke(NOW.plusSeconds(1));
        members.saveAndFlush(membership);
        var restored = JpaTestDatabase.transactional(source, new JpaSpaceAccessAdapter(spaces, members));
        assertThat(restored.visibleSpaceRefs("org:a", "user:alice", "", 100))
                .containsExactly("team:second");
        assertThat(restored.allows("org:a", "workspace-default", "user:alice", Permission.VIEW)).isFalse();
        assertThat(restored.allows("org:b", "workspace-default", "user:bob", Permission.ADMIN)).isTrue();

        var remainingSpace = spaces.findById(new SpaceId("org:a", "team:second")).orElseThrow();
        remainingSpace.transition("SUSPENDED", NOW.plusSeconds(2));
        spaces.saveAndFlush(remainingSpace);
        assertThat(restored.visibleSpaceRefs("org:a", "user:alice", "", 100)).isEmpty();
        assertThat(restored.allows("org:a", "team:second", "user:alice", Permission.VIEW)).isFalse();
    }

    @Test
    void explicitProvisionIsDurableIdempotentAndCannotOverwriteCurrentGrants() {
        DriverManagerDataSource source = new DriverManagerDataSource();
        source.setDriverClassName(POSTGRES.getDriverClassName());
        source.setUrl(POSTGRES.getJdbcUrl());
        source.setUsername(POSTGRES.getUsername());
        source.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        JpaTestDatabase.validateSchema(source);

        SpaceJpaRepository spaces = JpaTestDatabase.repository(source, SpaceJpaRepository.class);
        SpaceMembershipJpaRepository members = JpaTestDatabase.repository(source, SpaceMembershipJpaRepository.class);
        var provision = JpaTestDatabase.transactional(source,
                new JpaSpaceProvisioningAdapter(spaces, members, Clock.fixed(NOW, ZoneOffset.UTC)));
        String owner = "acct_11111111111111111111111111111111";
        String alice = "acct_22222222222222222222222222222222";
        String bob = "acct_33333333333333333333333333333333";
        assertThat(provision.provision("org:setup", "workspace-default", "Organization workspace", owner, Set.of(alice)))
                .isEqualTo(SpaceProvisioningPort.Result.CREATED);
        assertThat(provision.provision("org:setup", "workspace-default", "Organization workspace", owner, Set.of(alice)))
                .isEqualTo(SpaceProvisioningPort.Result.UNCHANGED);
        var access = JpaTestDatabase.transactional(source, new JpaSpaceAccessAdapter(spaces, members));
        assertThat(access.allows("org:setup", "workspace-default", owner, Permission.ADMIN)).isTrue();
        assertThat(access.allows("org:setup", "workspace-default", alice, Permission.VIEW)).isTrue();
        assertThat(access.allows("org:setup", "workspace-default", alice, Permission.EDIT)).isFalse();
        assertThatThrownBy(() -> provision.provision("org:setup", "workspace-default", "Organization workspace", owner, Set.of(bob)))
                .isInstanceOf(SpaceProvisioningPort.Conflict.class);
        assertThat(access.allows("org:setup", "workspace-default", alice, Permission.VIEW)).isTrue();
        assertThat(access.allows("org:setup", "workspace-default", bob, Permission.VIEW)).isFalse();

        members.findByIdOrganizationRefAndIdSpaceRefAndIdPersonRef(
                "org:setup", "workspace-default", alice).ifPresent(member -> {
            member.revoke(NOW.plusSeconds(1));
            members.saveAndFlush(member);
        });
        assertThatThrownBy(() -> provision.provision("org:setup", "workspace-default", "Organization workspace", owner, Set.of(alice)))
                .isInstanceOf(SpaceProvisioningPort.Conflict.class);
        assertThat(access.allows("org:setup", "workspace-default", alice, Permission.VIEW)).isFalse();
    }

    @Test
    void versionedMemberAdministrationPreservesCurrentRightsAndLastAdministrator() {
        DriverManagerDataSource source = new DriverManagerDataSource();
        source.setDriverClassName(POSTGRES.getDriverClassName());
        source.setUrl(POSTGRES.getJdbcUrl());
        source.setUsername(POSTGRES.getUsername());
        source.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        JpaTestDatabase.validateSchema(source);
        SpaceJpaRepository spaces = JpaTestDatabase.repository(source, SpaceJpaRepository.class);
        SpaceMembershipJpaRepository members = JpaTestDatabase.repository(source, SpaceMembershipJpaRepository.class);
        String owner = "acct_11111111111111111111111111111111";
        String alice = "acct_22222222222222222222222222222222";
        String outsider = "acct_33333333333333333333333333333333";
        JpaTestDatabase.transactional(source,
                new JpaSpaceProvisioningAdapter(spaces, members, Clock.fixed(NOW, ZoneOffset.UTC)))
                .provision("org:membership", "workspace-default", "Organization workspace", owner, Set.of());
        var admin = JpaTestDatabase.transactional(source,
                new JpaSpaceMembershipAdminAdapter(spaces, members,
                        Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC)));
        var access = JpaTestDatabase.transactional(source, new JpaSpaceAccessAdapter(spaces, members));
        var first = admin.grant("org:membership", "workspace-default", owner, alice,
                Set.of(Permission.VIEW), null, true);
        assertThat(first.strongEtag()).isNotBlank();
        assertThat(admin.get("org:membership", "workspace-default", owner, alice).strongEtag())
                .isEqualTo(first.strongEtag());
        assertThat(admin.list("org:membership", "workspace-default", owner, "", 10)
                .stream().map(member -> member.accountRef()))
                .containsExactly(owner, alice);
        assertThat(access.allows("org:membership", "workspace-default", alice, Permission.VIEW)).isTrue();
        assertThat(access.allows("org:membership", "workspace-default", alice, Permission.EDIT)).isFalse();
        assertThatThrownBy(() -> admin.grant("org:membership", "workspace-default", outsider, alice,
                Set.of(Permission.VIEW, Permission.EDIT), first.strongEtag(), false))
                .isInstanceOf(com.massimotter.weave.backend.spaces.port.SpaceMembershipAdministrationPort.Denied.class);
        var second = admin.grant("org:membership", "workspace-default", owner, alice,
                Set.of(Permission.VIEW, Permission.EDIT), first.strongEtag(), false);
        assertThat(second.strongEtag()).isNotEqualTo(first.strongEtag());
        assertThatThrownBy(() -> admin.revoke("org:membership", "workspace-default", owner,
                alice, first.strongEtag()))
                .isInstanceOf(com.massimotter.weave.backend.spaces.port.SpaceMembershipAdministrationPort.Stale.class);
        assertThat(access.allows("org:membership", "workspace-default", alice, Permission.EDIT)).isTrue();
        assertThatThrownBy(() -> admin.revoke("org:membership", "workspace-default", owner,
                owner, "\"sm-0\""))
                .isInstanceOf(com.massimotter.weave.backend.spaces.port.SpaceMembershipAdministrationPort.LastAdministrator.class);
        admin.revoke("org:membership", "workspace-default", owner, alice, second.strongEtag());
        assertThat(access.allows("org:membership", "workspace-default", alice, Permission.VIEW)).isFalse();
        assertThat(admin.list("org:membership", "workspace-default", owner, "", 10)
                .stream().map(member -> member.accountRef())).containsExactly(owner);
        var tombstone = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> admin.get("org:membership", "workspace-default", owner, alice),
                com.massimotter.weave.backend.spaces.port.SpaceMembershipAdministrationPort.Revoked.class);
        assertThat(tombstone.strongEtag()).isNotEqualTo(second.strongEtag());
        assertThatThrownBy(() -> admin.grant("org:membership", "workspace-default", owner,
                alice, Set.of(Permission.VIEW), second.strongEtag(), false))
                .isInstanceOf(com.massimotter.weave.backend.spaces.port.SpaceMembershipAdministrationPort.Stale.class);
        assertThatThrownBy(() -> admin.grant("org:membership", "workspace-default", owner,
                alice, Set.of(Permission.VIEW), null, true))
                .isInstanceOf(com.massimotter.weave.backend.spaces.port.SpaceMembershipAdministrationPort.Stale.class);
        var restored = admin.grant("org:membership", "workspace-default", owner, alice,
                Set.of(Permission.VIEW), tombstone.strongEtag(), false);
        assertThat(restored.strongEtag()).isNotEqualTo(first.strongEtag());
        assertThat(access.allows("org:membership", "workspace-default", alice, Permission.VIEW)).isTrue();
        assertThat(access.allows("org:other", "workspace-default", alice, Permission.VIEW)).isFalse();
        admin.revoke("org:membership", "workspace-default", owner, alice, restored.strongEtag());
        assertThatThrownBy(() -> admin.grant("org:membership", "workspace-default", owner,
                alice, Set.of(Permission.VIEW), tombstone.strongEtag(), false))
                .isInstanceOf(com.massimotter.weave.backend.spaces.port.SpaceMembershipAdministrationPort.Stale.class);
        assertThat(access.allows("org:membership", "workspace-default", alice, Permission.VIEW)).isFalse();
    }
}
