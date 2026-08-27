package com.massimotter.weave.backend.runner.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.massimotter.weave.backend.runner.domain.RunnerControl.ArtifactManifest;
import com.massimotter.weave.backend.runner.domain.RunnerControl.CapabilityDescriptor;
import com.massimotter.weave.backend.runner.domain.RunnerControl.CapabilityEffect;
import com.massimotter.weave.backend.runner.domain.RunnerControl.CapabilityId;
import com.massimotter.weave.backend.runner.domain.RunnerControl.CapabilityRef;
import com.massimotter.weave.backend.runner.domain.RunnerControl.Evidence;
import com.massimotter.weave.backend.runner.domain.RunnerControl.ObservationBatch;
import com.massimotter.weave.backend.runner.domain.RunnerControl.ObservationSourceKind;
import com.massimotter.weave.backend.runner.domain.RunnerControl.ObservedEntity;
import com.massimotter.weave.backend.runner.domain.RunnerControl.ObservedRelation;
import com.massimotter.weave.backend.runner.domain.RunnerControl.ResourceGrant;
import com.massimotter.weave.backend.runner.domain.RunnerControl.RunnerId;
import com.massimotter.weave.backend.runner.domain.RunnerControl.RunnerRegistration;
import com.massimotter.weave.backend.runner.domain.RunnerControl.RunnerState;
import com.massimotter.weave.backend.runner.domain.RunnerControl.StaleTaskLeaseException;
import com.massimotter.weave.backend.runner.domain.RunnerControl.TaskLease;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RunnerControlTest {

    private static final String DIGEST =
            "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final Instant NOW = Instant.parse("2026-08-27T12:00:00Z");

    @Test
    void registrationPublishesOnlyPublicCapabilityMetadata() {
        CapabilityRef reference = new CapabilityRef(new CapabilityId("internal.cmdb.lookup"), "1.2.0");
        CapabilityDescriptor descriptor = new CapabilityDescriptor(
                reference,
                "CMDB lookup",
                "Looks up one internal configuration item.",
                CapabilityEffect.READ_ONLY,
                DIGEST,
                DIGEST,
                Duration.ofMinutes(2),
                1024 * 1024,
                Set.of("cmdb-report"));

        RunnerRegistration registration = new RunnerRegistration(
                new RunnerId("runner_example_01"),
                "org:example",
                "0.1.0",
                DIGEST,
                List.of(descriptor),
                RunnerState.ONLINE,
                1,
                NOW,
                Map.of("environment", "production"));

        assertTrue(registration.offers(reference));
        assertFalse(registration.offers(
                new CapabilityRef(new CapabilityId("internal.cmdb.lookup"), "2.0.0")));
    }

    @Test
    void duplicateCapabilityCoordinatesAreRejected() {
        CapabilityDescriptor descriptor = descriptor("internal.cmdb.lookup", "1.0.0");

        assertThrows(
                IllegalArgumentException.class,
                () -> new RunnerRegistration(
                        new RunnerId("runner_example_01"),
                        "org:example",
                        "0.1.0",
                        DIGEST,
                        List.of(descriptor, descriptor),
                        RunnerState.ONLINE,
                        1,
                        NOW,
                        Map.of()));
    }

    @Test
    void leaseIsRunnerAndFenceBound() {
        UUID taskId = UUID.fromString("00000000-0000-0000-0000-000000000042");
        UUID leaseId = UUID.fromString("00000000-0000-0000-0000-000000000043");
        TaskLease lease = new TaskLease(
                taskId,
                leaseId,
                7,
                new RunnerId("runner_example_01"),
                new CapabilityRef(new CapabilityId("internal.cmdb.lookup"), "1.0.0"),
                DIGEST,
                1,
                "task-42-attempt-1",
                Map.of("configurationItem", "nextcloud"),
                List.of(URI.create("weave://space/home-core")),
                List.of(new ResourceGrant(
                        URI.create("weave://resource/home-core"),
                        Set.of("read"),
                        "revision-12")),
                NOW,
                NOW.plusSeconds(60),
                NOW.plusSeconds(300),
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");

        assertTrue(lease.activeAt(NOW.plusSeconds(20)));
        assertFalse(lease.activeAt(NOW.plusSeconds(60)));
        lease.requireFence(leaseId, 7);
        assertThrows(StaleTaskLeaseException.class, () -> lease.requireFence(leaseId, 6));
    }

    @Test
    void artifactTraversalIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ArtifactManifest(
                        "../secret.txt",
                        "secret.txt",
                        "report",
                        "text/plain",
                        10,
                        DIGEST));
    }

    @Test
    void observationRelationsMustReferenceEntitiesFromTheSameBatch() {
        ObservedEntity repository = new ObservedEntity(
                "repo:home-core",
                "repository",
                "home-core",
                Set.of(),
                Map.of("language", "java"),
                List.of(new Evidence("DECLARATION", "capability://topology", null)));
        ObservedRelation dangling = new ObservedRelation(
                "repo:home-core",
                "deploys",
                "service:nextcloud",
                1.0,
                Map.of(),
                List.of());

        assertThrows(
                IllegalArgumentException.class,
                () -> new ObservationBatch(
                        new RunnerId("runner_example_01"),
                        "internal.topology",
                        "1.0.0",
                        ObservationSourceKind.DECLARATION,
                        "home-core",
                        NOW,
                        Duration.ofMinutes(5),
                        List.of(repository),
                        List.of(dangling),
                        null));
    }

    @Test
    void observationExpiryIsDerivedFromEvidenceTtl() {
        ObservedEntity repository = new ObservedEntity(
                "repo:home-core",
                "repository",
                "home-core",
                Set.of("git:home-core"),
                Map.of(),
                List.of(new Evidence("DECLARATION", "capability://topology", DIGEST)));
        ObservedEntity service = new ObservedEntity(
                "service:nextcloud",
                "service",
                "Nextcloud",
                Set.of(),
                Map.of(),
                List.of());
        ObservationBatch batch = new ObservationBatch(
                new RunnerId("runner_example_01"),
                "internal.topology",
                "1.0.0",
                ObservationSourceKind.DECLARATION,
                "home-core",
                NOW,
                Duration.ofMinutes(5),
                List.of(repository, service),
                List.of(new ObservedRelation(
                        repository.localKey(),
                        "deploys",
                        service.localKey(),
                        1.0,
                        Map.of(),
                        List.of())),
                DIGEST);

        assertEquals(NOW.plusSeconds(300), batch.expiresAt());
    }

    private static CapabilityDescriptor descriptor(String id, String version) {
        return new CapabilityDescriptor(
                new CapabilityRef(new CapabilityId(id), version),
                id,
                "",
                CapabilityEffect.READ_ONLY,
                DIGEST,
                DIGEST,
                Duration.ofMinutes(1),
                4096,
                Set.of());
    }
}
