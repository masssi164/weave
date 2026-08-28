package com.massimotter.weave.backend.runner.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.runner.application.RunnerCapabilityRegistry;
import com.massimotter.weave.backend.runner.application.RunnerCapabilityRegistry.AvailabilityDisposition;
import com.massimotter.weave.backend.runner.application.RunnerCapabilityRegistry.AvailabilityObservation;
import com.massimotter.weave.backend.runner.application.RunnerCapabilityRegistry.CapabilityContract;
import com.massimotter.weave.backend.runner.application.RunnerCapabilityRegistry.PublicBundlePublication;
import com.massimotter.weave.backend.runner.domain.RunnerControl.CapabilityDescriptor;
import com.massimotter.weave.backend.runner.domain.RunnerControl.CapabilityEffect;
import com.massimotter.weave.backend.runner.domain.RunnerControl.CapabilityId;
import com.massimotter.weave.backend.runner.domain.RunnerControl.CapabilityRef;
import com.massimotter.weave.backend.runner.domain.RunnerControl.RunnerId;
import com.massimotter.weave.backend.runner.domain.RunnerControl.RunnerState;
import com.massimotter.weave.backend.testing.JpaTestDatabase;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("postgres")
class JpaRunnerAvailabilityRegistryPostgresTest {

    private static final RunnerId RUNNER = new RunnerId("runner_live_registration_01");
    private static final CapabilityRef CAPABILITY =
            new CapabilityRef(new CapabilityId("internal.cmdb.lookup"), "1.0.0");
    private static final String BUNDLE = digest('b');
    private static final String CONTRACT = digest('c');
    private static final Instant PUBLISHED = Instant.parse("2026-08-28T22:00:00Z");

    @Test
    void heartbeatCreatesDurableSessionAndActivatesPublishedOfferings() {
        DataSource dataSource = JpaTestDatabase.entityFirstDataSource("runner-live-session");
        RunnerCapabilityRegistry registry = registry(dataSource);
        registry.publish(publication());
        assertThat(registry.catalog("org:live-test").revision()).isEqualTo(1);
        assertThat(registry.offerings("org:live-test", CAPABILITY).getFirst().available()).isFalse();

        AvailabilityObservation heartbeat = new AvailabilityObservation(
                RUNNER,
                "org:live-test",
                BUNDLE,
                "1.2.3",
                RunnerState.ONLINE,
                4,
                1,
                PUBLISHED.plusSeconds(10));
        var created = registry.observeAvailability(heartbeat);
        var replay = registry.observeAvailability(heartbeat);

        assertThat(created.disposition()).isEqualTo(AvailabilityDisposition.CREATED);
        assertThat(created.updatedOfferings()).isEqualTo(1);
        assertThat(created.availableSlots()).isEqualTo(3);
        assertThat(replay.disposition()).isEqualTo(AvailabilityDisposition.IDEMPOTENT_REPLAY);
        assertThat(registry.catalog("org:live-test").revision()).isEqualTo(1);

        var session = registry.session("org:live-test", RUNNER).orElseThrow();
        assertThat(session.runnerVersion()).isEqualTo("1.2.3");
        assertThat(session.availableSlots()).isEqualTo(3);
        assertThat(session.publicBundleDigest()).isEqualTo(BUNDLE);
        assertThat(registry.offerings("org:live-test", CAPABILITY).getFirst().available()).isTrue();
    }

    @Test
    void staleUnknownAndConflictingHeartbeatsFailClosed() {
        DataSource dataSource = JpaTestDatabase.entityFirstDataSource("runner-live-negative");
        RunnerCapabilityRegistry registry = registry(dataSource);
        registry.publish(publication());
        AvailabilityObservation current = new AvailabilityObservation(
                RUNNER,
                "org:live-test",
                BUNDLE,
                "1.2.3",
                RunnerState.ONLINE,
                2,
                0,
                PUBLISHED.plusSeconds(10));
        registry.observeAvailability(current);

        assertThatThrownBy(() -> registry.observeAvailability(new AvailabilityObservation(
                        RUNNER,
                        "org:live-test",
                        BUNDLE,
                        "1.2.3",
                        RunnerState.ONLINE,
                        2,
                        1,
                        PUBLISHED.plusSeconds(10))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("conflicting");
        assertThatThrownBy(() -> registry.observeAvailability(new AvailabilityObservation(
                        RUNNER,
                        "org:live-test",
                        BUNDLE,
                        "1.2.3",
                        RunnerState.ONLINE,
                        2,
                        0,
                        PUBLISHED.plusSeconds(5))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("stale");
        assertThatThrownBy(() -> registry.observeAvailability(new AvailabilityObservation(
                        RUNNER,
                        "org:live-test",
                        digest('x'),
                        "1.2.3",
                        RunnerState.ONLINE,
                        2,
                        0,
                        PUBLISHED.plusSeconds(20))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unpublished");
    }

    @Test
    void offlineHeartbeatMakesTheOfferingUnavailableWithoutChangingTheCatalog() {
        DataSource dataSource = JpaTestDatabase.entityFirstDataSource("runner-live-offline");
        RunnerCapabilityRegistry registry = registry(dataSource);
        registry.publish(publication());
        registry.observeAvailability(new AvailabilityObservation(
                RUNNER,
                "org:live-test",
                BUNDLE,
                "1.2.3",
                RunnerState.ONLINE,
                2,
                0,
                PUBLISHED.plusSeconds(10)));

        var result = registry.observeAvailability(new AvailabilityObservation(
                RUNNER,
                "org:live-test",
                BUNDLE,
                "1.2.3",
                RunnerState.OFFLINE,
                2,
                2,
                PUBLISHED.plusSeconds(20)));

        assertThat(result.disposition()).isEqualTo(AvailabilityDisposition.UPDATED);
        assertThat(registry.catalog("org:live-test").revision()).isEqualTo(1);
        assertThat(registry.offerings("org:live-test", CAPABILITY).getFirst().available()).isFalse();
    }

    private RunnerCapabilityRegistry registry(DataSource dataSource) {
        return JpaTestDatabase.transactional(
                dataSource,
                new JpaRunnerCapabilityRegistry(JpaTestDatabase.entityManager(dataSource)));
    }

    private PublicBundlePublication publication() {
        CapabilityDescriptor descriptor = new CapabilityDescriptor(
                CAPABILITY,
                "Internal CMDB lookup",
                "Returns one bounded internal asset record.",
                CapabilityEffect.READ_ONLY,
                "{\"additionalProperties\":false,\"type\":\"object\"}",
                digest('i'),
                "{\"additionalProperties\":false,\"type\":\"object\"}",
                digest('o'),
                Duration.ofSeconds(60),
                4096,
                Set.of("cmdb-report"));
        return new PublicBundlePublication(
                RUNNER,
                "org:live-test",
                "internal.cmdb",
                "1.0.0",
                BUNDLE,
                List.of(new CapabilityContract(descriptor, CONTRACT)),
                RunnerState.ENROLLING,
                1,
                0,
                PUBLISHED);
    }

    private static String digest(char value) {
        return "sha256:" + String.valueOf(value).repeat(64);
    }
}
