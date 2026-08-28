package com.massimotter.weave.backend.runner.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.runner.application.RunnerCapabilityRegistry;
import com.massimotter.weave.backend.runner.application.RunnerCapabilityRegistry.CapabilityContract;
import com.massimotter.weave.backend.runner.application.RunnerCapabilityRegistry.PublicBundlePublication;
import com.massimotter.weave.backend.runner.application.RunnerCapabilityRegistry.PublicationDisposition;
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
class JpaRunnerCapabilityRegistryPostgresTest {

    private static final Instant NOW = Instant.parse("2026-08-28T20:00:00Z");
    private static final String PUBLIC_BUNDLE_DIGEST = digest('b');
    private static final String CONTRACT_V1 = digest('1');
    private static final String CONTRACT_V2 = digest('2');
    private static final String INPUT_DIGEST = digest('a');
    private static final String OUTPUT_DIGEST = digest('c');

    @Test
    void twoRunnersShareOnePublicDefinitionAndRemainSeparateOfferings() {
        DataSource dataSource = JpaTestDatabase.entityFirstDataSource("runner-capability-shared");
        RunnerCapabilityRegistry registry = registry(dataSource);
        CapabilityContract contract = contract("1.0.0", "Internal CMDB lookup", CONTRACT_V1);

        var first = registry.publish(publication("runner_catalog_a1", contract, 4, 2, NOW));
        var second = registry.publish(publication("runner_catalog_b1", contract, 8, 5, NOW.plusSeconds(1)));

        assertThat(first.catalogRevision()).isEqualTo(1);
        assertThat(first.disposition()).isEqualTo(PublicationDisposition.CREATED);
        assertThat(second.catalogRevision()).isEqualTo(1);
        assertThat(second.disposition()).isEqualTo(PublicationDisposition.CREATED);

        var catalog = registry.catalog("org:catalog-test");
        assertThat(catalog.revision()).isEqualTo(1);
        assertThat(catalog.definitions()).hasSize(1);
        assertThat(catalog.definitions().getFirst().contract()).isEqualTo(contract);

        var offerings = registry.offerings("org:catalog-test", contract.capability());
        assertThat(offerings)
                .extracting(value -> value.runnerId().value())
                .containsExactly("runner_catalog_a1", "runner_catalog_b1");
        assertThat(offerings).allMatch(value -> value.active() && value.available());
    }

    @Test
    void sameCoordinateWithDifferentPublicContractFailsClosed() {
        DataSource dataSource = JpaTestDatabase.entityFirstDataSource("runner-capability-conflict");
        RunnerCapabilityRegistry registry = registry(dataSource);
        CapabilityContract original = contract("1.0.0", "Internal CMDB lookup", CONTRACT_V1);
        CapabilityContract conflicting = contract("1.0.0", "Production CMDB lookup", CONTRACT_V2);

        registry.publish(publication("runner_catalog_a1", original, 4, 2, NOW));

        assertThatThrownBy(() -> registry.publish(
                        publication("runner_catalog_b1", conflicting, 4, 2, NOW.plusSeconds(1))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("different public capability contract");

        assertThat(registry.catalog("org:catalog-test").definitions()).hasSize(1);
        assertThat(registry.offerings("org:catalog-test", original.capability())).hasSize(1);
    }

    @Test
    void availabilityUpdatesDoNotChangeCatalogRevisionAndOmittedOfferingsDeactivate() {
        DataSource dataSource = JpaTestDatabase.entityFirstDataSource("runner-capability-lifecycle");
        RunnerCapabilityRegistry registry = registry(dataSource);
        CapabilityContract v1 = contract("1.0.0", "Internal CMDB lookup", CONTRACT_V1);
        PublicBundlePublication first = publication("runner_catalog_a1", v1, 4, 2, NOW);

        assertThat(registry.publish(first).catalogRevision()).isEqualTo(1);

        PublicBundlePublication availabilityUpdate =
                publication("runner_catalog_a1", v1, 4, 1, NOW.plusSeconds(10));
        var updated = registry.publish(availabilityUpdate);
        var replayed = registry.publish(availabilityUpdate);

        assertThat(updated.catalogRevision()).isEqualTo(1);
        assertThat(updated.disposition()).isEqualTo(PublicationDisposition.UPDATED);
        assertThat(replayed.catalogRevision()).isEqualTo(1);
        assertThat(replayed.disposition()).isEqualTo(PublicationDisposition.IDEMPOTENT_REPLAY);
        assertThat(registry.offerings("org:catalog-test", v1.capability()).getFirst().availableSlots())
                .isEqualTo(1);

        CapabilityContract v2 = contract("2.0.0", "Internal CMDB lookup v2", CONTRACT_V2);
        var newVersion = registry.publish(
                publication("runner_catalog_a1", v2, 4, 4, NOW.plusSeconds(20)));

        assertThat(newVersion.catalogRevision()).isEqualTo(2);
        assertThat(registry.catalog("org:catalog-test").definitions()).hasSize(2);
        assertThat(registry.offerings("org:catalog-test", v1.capability()).getFirst().active())
                .isFalse();
        assertThat(registry.offerings("org:catalog-test", v2.capability()).getFirst().active())
                .isTrue();
    }

    private RunnerCapabilityRegistry registry(DataSource dataSource) {
        return JpaTestDatabase.transactional(
                dataSource,
                new JpaRunnerCapabilityRegistry(JpaTestDatabase.entityManager(dataSource)));
    }

    private PublicBundlePublication publication(
            String runnerId,
            CapabilityContract contract,
            int capacity,
            int availableSlots,
            Instant observedAt) {
        return new PublicBundlePublication(
                new RunnerId(runnerId),
                "org:catalog-test",
                "internal.cmdb",
                "1.0.0",
                PUBLIC_BUNDLE_DIGEST,
                List.of(contract),
                RunnerState.ONLINE,
                capacity,
                availableSlots,
                observedAt);
    }

    private CapabilityContract contract(String version, String title, String contractDigest) {
        CapabilityDescriptor descriptor = new CapabilityDescriptor(
                new CapabilityRef(new CapabilityId("internal.cmdb.lookup"), version),
                title,
                "Returns one bounded internal asset record.",
                CapabilityEffect.READ_ONLY,
                "{\"additionalProperties\":false,\"type\":\"object\"}",
                INPUT_DIGEST,
                "{\"additionalProperties\":false,\"type\":\"object\"}",
                OUTPUT_DIGEST,
                Duration.ofSeconds(60),
                4096,
                Set.of("cmdb-report"));
        return new CapabilityContract(descriptor, contractDigest);
    }

    private static String digest(char value) {
        return "sha256:" + String.valueOf(value).repeat(64);
    }
}
