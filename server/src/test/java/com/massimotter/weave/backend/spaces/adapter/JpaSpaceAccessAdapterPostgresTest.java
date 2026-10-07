package com.massimotter.weave.backend.spaces.adapter;

import com.massimotter.weave.backend.spaces.port.SpaceAccessPort.Permission;
import com.massimotter.weave.backend.spaces.port.SpaceProvisioningPort;
import com.massimotter.weave.backend.testing.JpaTestDatabase;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Set;
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
        assertThat(provision.provision("org:setup", "workspace-default", owner, Set.of(alice)))
                .isEqualTo(SpaceProvisioningPort.Result.CREATED);
        assertThat(provision.provision("org:setup", "workspace-default", owner, Set.of(alice)))
                .isEqualTo(SpaceProvisioningPort.Result.UNCHANGED);
        var access = JpaTestDatabase.transactional(source, new JpaSpaceAccessAdapter(spaces, members));
        assertThat(access.allows("org:setup", "workspace-default", owner, Permission.ADMIN)).isTrue();
        assertThat(access.allows("org:setup", "workspace-default", alice, Permission.VIEW)).isTrue();
        assertThat(access.allows("org:setup", "workspace-default", alice, Permission.EDIT)).isFalse();
        assertThatThrownBy(() -> provision.provision("org:setup", "workspace-default", owner, Set.of(bob)))
                .isInstanceOf(SpaceProvisioningPort.Conflict.class);
        assertThat(access.allows("org:setup", "workspace-default", alice, Permission.VIEW)).isTrue();
        assertThat(access.allows("org:setup", "workspace-default", bob, Permission.VIEW)).isFalse();

        members.findByIdOrganizationRefAndIdSpaceRefAndIdPersonRef(
                "org:setup", "workspace-default", alice).ifPresent(member -> {
            member.revoke(NOW.plusSeconds(1));
            members.saveAndFlush(member);
        });
        assertThatThrownBy(() -> provision.provision("org:setup", "workspace-default", owner, Set.of(alice)))
                .isInstanceOf(SpaceProvisioningPort.Conflict.class);
        assertThat(access.allows("org:setup", "workspace-default", alice, Permission.VIEW)).isFalse();
    }
}
