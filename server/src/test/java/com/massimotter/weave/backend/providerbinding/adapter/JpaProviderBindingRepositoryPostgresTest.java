package com.massimotter.weave.backend.providerbinding.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.providerbinding.domain.ProviderBinding;
import com.massimotter.weave.backend.providerbinding.domain.ProviderBinding.State;
import com.massimotter.weave.backend.providerbinding.domain.ProviderObjectMapping;
import com.massimotter.weave.backend.testing.JpaTestDatabase;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class JpaProviderBindingRepositoryPostgresTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void nanosecondClockObservationPersistsAtDatabasePrecisionWithoutLosingTimeGuards() {
        var repository = ProviderBindingJpaTestFactory.create(migratedDataSource());
        Instant first = Instant.parse("2026-10-05T11:00:00.123456789Z");
        Instant later = Instant.parse("2026-10-05T11:01:00.987654321Z");
        var original = new ProviderObjectMapping("org:clock", "files", 1,
                "file:clock-proof", "file:provider-proof", "weave-user-http-create", first, first);

        var created = repository.saveMapping(original);
        assertThat(created.firstObservedAt()).isEqualTo(Instant.parse("2026-10-05T11:00:00.123456Z"));
        assertThat(repository.mappingByCanonicalId("org:clock", "files", 1, "file:clock-proof"))
                .contains(created);
        assertThat(repository.saveMapping(original)).isEqualTo(created);

        var updated = repository.saveMapping(new ProviderObjectMapping("org:clock", "files", 1,
                "file:clock-proof", "file:provider-proof", "weave-user-http-create", first, later));
        assertThat(updated.lastObservedAt()).isEqualTo(Instant.parse("2026-10-05T11:01:00.987654Z"));
        assertThatThrownBy(() -> repository.saveMapping(original))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot move backwards");
        assertThatThrownBy(() -> repository.saveMapping(new ProviderObjectMapping("org:clock", "files", 1,
                "file:clock-proof", "file:provider-proof", "weave-user-http-create",
                first.plusNanos(1000), later)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be rewritten");
    }

    @Test
    void mappedFilesBindingCannotRotateOrReplaceWithoutVerifiedIdentityCarryForward() {
        DriverManagerDataSource dataSource = migratedDataSource();
        var repository = ProviderBindingJpaTestFactory.create(dataSource);
        Instant now = Instant.parse("2026-07-21T13:00:00Z");

        var nextcloud = repository.activate(
                "org:example", "files", 0, "nextcloud-webdav", "secretref:files:nextcloud", now);
        var mapping = repository.saveMapping(new ProviderObjectMapping(
                "org:example", "files", nextcloud.revision(), "file:stable-1", "nextcloud-fileid:42",
                "nextcloud-oc-fileid", now, now));
        assertThatThrownBy(() -> repository.activate(
                "org:example", "files", nextcloud.revision(), "nextcloud-webdav",
                "secretref:files:rotated-credential", now.plusSeconds(1)))
                .isInstanceOf(JpaProviderBindingRepository.FilesBindingIdentityTransitionBlockedException.class);
        assertThatThrownBy(() -> repository.activate(
                "org:example", "files", nextcloud.revision(), "weave-s3-minio", "secretref:files:minio",
                now.plusSeconds(1)))
                .isInstanceOf(JpaProviderBindingRepository.FilesBindingIdentityTransitionBlockedException.class);

        assertThat(nextcloud.revision()).isEqualTo(1);
        assertThat(repository.revision("org:example", "files", 1)).get()
                .extracting(binding -> binding.state()).isEqualTo(State.ACTIVE);
        assertThat(repository.current("org:example", "files")).contains(nextcloud);
        assertThat(repository.mappingByProviderRef("org:example", "files", 1, "nextcloud-fileid:42"))
                .contains(mapping);
        assertThat(repository.mappingByProviderRef("org:example", "files", 2, "nextcloud-fileid:42"))
                .isEmpty();

        assertThatThrownBy(() -> repository.activate(
                "org:example", "files", 0, "nextcloud-webdav", "secretref:stale", now.plusSeconds(2)))
                .isInstanceOf(JpaProviderBindingRepository.StaleProviderBindingException.class);

        DriverManagerDataSource restartedDataSource = new DriverManagerDataSource();
        restartedDataSource.setDriverClassName(POSTGRES.getDriverClassName());
        restartedDataSource.setUrl(dataSource.getUrl());
        restartedDataSource.setUsername(POSTGRES.getUsername());
        restartedDataSource.setPassword(POSTGRES.getPassword());
        var restartedAdapter = ProviderBindingJpaTestFactory.create(restartedDataSource);
        assertThat(restartedAdapter.current("org:example", "files")).contains(nextcloud);
        assertThat(restartedAdapter.mappingByCanonicalId("org:example", "files", 1, "file:stable-1"))
                .contains(mapping);
    }

    @Test
    void mappedCalendarBindingCannotRotateOrReplaceWithoutVerifiedIdentityCarryForward() {
        DriverManagerDataSource dataSource = migratedDataSource();
        var repository = ProviderBindingJpaTestFactory.create(dataSource);
        Instant now = Instant.parse("2026-10-07T09:00:00Z");

        var nativeCalendar = repository.activate("org:calendar", "calendar", 0,
                "weave-native", "configuration:calendar:deployment", now);
        var mapping = repository.saveMapping(new ProviderObjectMapping(
                "org:calendar", "calendar", nativeCalendar.revision(), "event:stable-1",
                "calendar:workspace.external-event", "calendar-user-api", now, now));
        assertThatThrownBy(() -> repository.activate("org:calendar", "calendar", nativeCalendar.revision(),
                "weave-native", "configuration:calendar:rotated", now.plusSeconds(1)))
                .isInstanceOf(JpaProviderBindingRepository.CalendarBindingIdentityTransitionBlockedException.class);
        assertThatThrownBy(() -> repository.activate("org:calendar", "calendar", nativeCalendar.revision(),
                "nextcloud-caldav", "configuration:calendar:replacement", now.plusSeconds(1)))
                .isInstanceOf(JpaProviderBindingRepository.CalendarBindingIdentityTransitionBlockedException.class);

        DriverManagerDataSource restartedDataSource = new DriverManagerDataSource();
        restartedDataSource.setDriverClassName(POSTGRES.getDriverClassName());
        restartedDataSource.setUrl(dataSource.getUrl());
        restartedDataSource.setUsername(POSTGRES.getUsername());
        restartedDataSource.setPassword(POSTGRES.getPassword());
        var restarted = ProviderBindingJpaTestFactory.create(restartedDataSource);
        assertThat(restarted.current("org:calendar", "calendar")).contains(nativeCalendar);
        assertThat(restarted.revision("org:calendar", "calendar", 2)).isEmpty();
        assertThat(restarted.mappingByCanonicalId("org:calendar", "calendar", 1, "event:stable-1"))
                .contains(mapping);
    }

    @Test
    void chatAuthorityIsOrganizationScopedDurableAndCannotTransitionWithoutReconciliation() {
        DriverManagerDataSource dataSource = migratedDataSource();
        var repository = ProviderBindingJpaTestFactory.create(dataSource);
        Instant now = Instant.parse("2026-10-07T10:00:00Z");

        var first = repository.activate("org:chat-a", "chat", 0, "weave-native", "profile:weave-native", now);
        var second = repository.activate("org:chat-b", "chat", 0, "matrix-synapse", "profile:matrix-synapse", now);
        assertThat(repository.current("org:chat-a", "chat")).contains(first);
        assertThat(repository.current("org:chat-b", "chat")).contains(second);
        assertThat(repository.current("org:foreign", "chat")).isEmpty();

        assertThatThrownBy(() -> repository.activate("org:chat-a", "chat", first.revision(),
                "matrix-synapse", "profile:matrix-synapse", now.plusSeconds(1)))
                .isInstanceOf(JpaProviderBindingRepository.ChatBindingTransitionBlockedException.class);
        assertThatThrownBy(() -> repository.activate("org:chat-a", "chat", first.revision(),
                "weave-native", "profile:weave-native", now.plusSeconds(2)))
                .isInstanceOf(JpaProviderBindingRepository.ChatBindingTransitionBlockedException.class);

        DriverManagerDataSource restartedDataSource = new DriverManagerDataSource();
        restartedDataSource.setDriverClassName(POSTGRES.getDriverClassName());
        restartedDataSource.setUrl(dataSource.getUrl());
        restartedDataSource.setUsername(POSTGRES.getUsername());
        restartedDataSource.setPassword(POSTGRES.getPassword());
        var restarted = ProviderBindingJpaTestFactory.create(restartedDataSource);
        assertThat(restarted.current("org:chat-a", "chat")).contains(first);
        assertThat(restarted.current("org:chat-b", "chat")).contains(second);
        assertThat(restarted.revision("org:chat-a", "chat", 2)).isEmpty();
    }

    @Test
    void stagedMappingsStayOffTheLiveRouteAndConcurrentStaleActivationFailsClosed() throws Exception {
        DriverManagerDataSource dataSource = migratedDataSource();
        var repository = ProviderBindingJpaTestFactory.create(dataSource);
        Instant now = Instant.parse("2026-10-04T08:00:00Z");
        ProviderBinding source = repository.activate(
                "org:alpha", "files", 0, "nextcloud-webdav", "secretref:alpha:source", now);
        ProviderBinding otherOrganization = repository.activate(
                "org:beta", "files", 0, "nextcloud-webdav", "secretref:beta:source", now);
        ProviderObjectMapping stagedTarget = repository.saveMapping(new ProviderObjectMapping(
                "org:alpha", "files", 2, "file:stable", "native-object:17", "staged-copy", now, now));

        assertThat(repository.current("org:alpha", "files")).contains(source);
        assertThat(repository.current("org:beta", "files")).contains(otherOrganization);
        assertThat(repository.mappingByCanonicalId("org:beta", "files", 2, "file:stable")).isEmpty();
        assertThat(repository.mappingByCanonicalId("org:alpha", "files", 2, "file:stable"))
                .contains(stagedTarget);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            Callable<Object> activate = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("concurrent activation start timed out");
                }
                try {
                    return repository.activate("org:alpha", "files", source.revision(),
                            "weave-native", "secretref:alpha:target", now.plusSeconds(1));
                } catch (RuntimeException failure) {
                    return failure;
                }
            };
            var first = workers.submit(activate);
            var second = workers.submit(activate);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Object> outcomes = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
            assertThat(outcomes).filteredOn(ProviderBinding.class::isInstance).hasSize(1);
            assertThat(outcomes).filteredOn(JpaProviderBindingRepository.StaleProviderBindingException.class::isInstance)
                    .hasSize(1);
        }

        assertThat(repository.current("org:alpha", "files")).get()
                .satisfies(binding -> {
                    assertThat(binding.revision()).isEqualTo(2);
                    assertThat(binding.adapterKey()).isEqualTo("weave-native");
                });
        assertThat(repository.current("org:beta", "files")).contains(otherOrganization);
        assertThat(new JdbcTemplate(dataSource).queryForObject("""
                select count(*) from weave_provider_bindings
                where organization_ref = ? and domain_key = ? and binding_state = 'ACTIVE'
                """, Integer.class, "org:alpha", "files")).isEqualTo(1);
        assertThatThrownBy(() -> repository.activate("org:beta", "files", 2,
                "weave-native", "secretref:wrong-organization", now.plusSeconds(2)))
                .isInstanceOf(JpaProviderBindingRepository.StaleProviderBindingException.class);
    }

    @Test
    void concurrentFilesDiscoveryCannotAssignTwoCanonicalIdsToOneProviderObject() throws Exception {
        DriverManagerDataSource dataSource = migratedDataSource();
        var repository = ProviderBindingJpaTestFactory.create(dataSource);
        Instant now = Instant.parse("2026-10-04T12:00:00Z");
        var binding = repository.activate(
                "org:files-race", "files", 0, "nextcloud-webdav", "secretref:files-race", now);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var workers = Executors.newFixedThreadPool(2)) {
            List<Callable<Object>> attempts = List.of("file:first", "file:second").stream()
                    .<Callable<Object>>map(fileId -> () -> {
                        ready.countDown();
                        if (!start.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("concurrent mapping start timed out");
                        }
                        try {
                            return repository.saveMapping(new ProviderObjectMapping(
                                    "org:files-race", "files", binding.revision(), fileId,
                                    "nextcloud-fileid:42", "authorized-discovery", now, now));
                        } catch (RuntimeException failure) {
                            return failure;
                        }
                    }).toList();
            var first = workers.submit(attempts.get(0));
            var second = workers.submit(attempts.get(1));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Object> outcomes = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
            assertThat(outcomes).filteredOn(ProviderObjectMapping.class::isInstance).hasSize(1);
            assertThat(outcomes).filteredOn(RuntimeException.class::isInstance).hasSize(1);
        }

        assertThat(new JdbcTemplate(dataSource).queryForObject("""
                select count(*) from weave_provider_object_mappings
                where organization_ref = ? and domain_key = ? and binding_revision = ?
                  and provider_object_ref = ?
                """, Integer.class, "org:files-race", "files", binding.revision(), "nextcloud-fileid:42"))
                .isEqualTo(1);
        assertThat(repository.mappingByProviderRef(
                "org:files-race", "files", binding.revision(), "nextcloud-fileid:42"))
                .isPresent();
    }

    @Test
    void flywayRejectsPreexistingDuplicateActiveBindingsWithoutChangingThem() {
        DriverManagerDataSource dataSource = isolatedDataSource();
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("6")).load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("""
                insert into weave_provider_bindings
                    (binding_revision, domain_key, organization_ref, activated_at_utc,
                     active_slot, adapter_key, configuration_ref, binding_state, version)
                values (?, 'files', 'org:duplicate', now(), true, 'nextcloud-webdav',
                        'secretref:source', 'ACTIVE', 0)
                """, 1L);
        jdbc.update("""
                insert into weave_provider_bindings
                    (binding_revision, domain_key, organization_ref, activated_at_utc,
                     active_slot, adapter_key, configuration_ref, binding_state, version)
                values (?, 'files', 'org:duplicate', now(), true, 'weave-native',
                        'secretref:target', 'ACTIVE', 0)
                """, 2L);

        assertThatThrownBy(() -> Flyway.configure().dataSource(dataSource)
                .locations("classpath:db/migration").load().migrate())
                .hasMessageContaining("V7__provider_binding_active_slot");
        assertThat(jdbc.queryForObject("select count(*) from weave_provider_bindings where organization_ref = 'org:duplicate'",
                Integer.class)).isEqualTo(2);
    }

    @Test
    void flywayUpgradeKeepsExistingBindingAndAllowsOneSuccessor() {
        DriverManagerDataSource dataSource = isolatedDataSource();
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("6")).load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("""
                insert into weave_provider_bindings
                    (binding_revision, domain_key, organization_ref, activated_at_utc,
                     active_slot, adapter_key, configuration_ref, binding_state, version)
                values (1, 'files', 'org:upgrade', now(), true, 'nextcloud-webdav',
                        'secretref:source', 'ACTIVE', 0)
                """);

        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        var repository = ProviderBindingJpaTestFactory.create(dataSource);
        assertThat(repository.current("org:upgrade", "files")).get()
                .satisfies(binding -> {
                    assertThat(binding.revision()).isEqualTo(1);
                    assertThat(binding.adapterKey()).isEqualTo("nextcloud-webdav");
                });

        repository.activate("org:upgrade", "files", 1, "weave-native", "secretref:target",
                Instant.parse("2026-10-04T08:00:00Z"));
        assertThat(repository.revision("org:upgrade", "files", 1)).get()
                .extracting(ProviderBinding::state).isEqualTo(State.RETIRED);
        assertThat(repository.current("org:upgrade", "files")).get()
                .extracting(ProviderBinding::revision).isEqualTo(2L);
    }

    private DriverManagerDataSource migratedDataSource() {
        DriverManagerDataSource dataSource = isolatedDataSource();
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        JpaTestDatabase.validateSchema(dataSource);
        return dataSource;
    }

    private DriverManagerDataSource isolatedDataSource() {
        String schema = "provider_binding_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("create schema " + schema);
        } catch (java.sql.SQLException failure) {
            throw new IllegalStateException("PostgreSQL provider-binding test schema could not be created", failure);
        }
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(POSTGRES.getDriverClassName());
        String separator = POSTGRES.getJdbcUrl().contains("?") ? "&" : "?";
        dataSource.setUrl(POSTGRES.getJdbcUrl() + separator + "currentSchema=" + schema);
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return dataSource;
    }
}
