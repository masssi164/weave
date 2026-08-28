package com.massimotter.weave.backend.runner.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.massimotter.weave.backend.runner.application.RunnerCapabilityRegistry;
import com.massimotter.weave.backend.runner.application.RunnerCapabilityRegistry.CapabilityContract;
import com.massimotter.weave.backend.runner.application.RunnerCapabilityRegistry.PublicBundlePublication;
import com.massimotter.weave.backend.runner.application.RunnerTaskStore;
import com.massimotter.weave.backend.runner.application.RunnerTaskStore.Claim;
import com.massimotter.weave.backend.runner.application.RunnerTaskStore.Lease;
import com.massimotter.weave.backend.runner.application.RunnerTaskStore.NewTask;
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
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("postgres")
class JpaRunnerContractSchedulingPostgresTest {

    private static final Instant NOW = Instant.parse("2026-08-28T21:00:00Z");
    private static final CapabilityRef CAPABILITY =
            new CapabilityRef(new CapabilityId("internal.cmdb.lookup"), "1.0.0");
    private static final RunnerId RUNNER_A = new RunnerId("runner_contract_a1");
    private static final RunnerId RUNNER_B = new RunnerId("runner_contract_b1");
    private static final String CONTRACT_DIGEST = digest('1');
    private static final String OTHER_CONTRACT_DIGEST = digest('2');
    private static final String PUBLIC_BUNDLE_A = digest('a');
    private static final String PUBLIC_BUNDLE_B = digest('b');

    @Test
    void equivalentCapabilityInDifferentPublicBundlesCanClaimTheSameTask() {
        DataSource dataSource = JpaTestDatabase.entityFirstDataSource("runner-contract-failover");
        RunnerCapabilityRegistry registry = registry(dataSource);
        RunnerTaskStore tasks = tasks(dataSource);
        CapabilityContract contract = contract(CONTRACT_DIGEST);
        registry.publish(publication(RUNNER_A, PUBLIC_BUNDLE_A, contract, RunnerState.ONLINE, 1));
        registry.publish(publication(RUNNER_B, PUBLIC_BUNDLE_B, contract, RunnerState.ONLINE, 1));
        tasks.enqueue(task(CONTRACT_DIGEST));

        Lease lease = tasks.claim(claim(RUNNER_B, PUBLIC_BUNDLE_B)).orElseThrow();

        assertThat(lease.runnerId()).isEqualTo(RUNNER_B);
        assertThat(lease.capabilityContractDigest()).isEqualTo(CONTRACT_DIGEST);
        assertThat(lease.publicBundleDigest()).isEqualTo(PUBLIC_BUNDLE_B);
    }

    @Test
    void selfReportedCapabilityCannotClaimWithoutPersistedOffering() {
        DataSource dataSource = JpaTestDatabase.entityFirstDataSource("runner-contract-unregistered");
        RunnerTaskStore tasks = tasks(dataSource);
        tasks.enqueue(task(CONTRACT_DIGEST));

        assertThat(tasks.claim(claim(RUNNER_A, CONTRACT_DIGEST))).isEmpty();
    }

    @Test
    void offlineOrZeroSlotOfferingCannotClaimWork() {
        DataSource offlineDataSource =
                JpaTestDatabase.entityFirstDataSource("runner-contract-offline");
        RunnerCapabilityRegistry offlineRegistry = registry(offlineDataSource);
        RunnerTaskStore offlineTasks = tasks(offlineDataSource);
        CapabilityContract contract = contract(CONTRACT_DIGEST);
        offlineRegistry.publish(publication(
                RUNNER_A,
                CONTRACT_DIGEST,
                contract,
                RunnerState.OFFLINE,
                1));
        offlineTasks.enqueue(task(CONTRACT_DIGEST));
        assertThat(offlineTasks.claim(claim(RUNNER_A, CONTRACT_DIGEST))).isEmpty();

        DataSource fullDataSource =
                JpaTestDatabase.entityFirstDataSource("runner-contract-no-slots");
        RunnerCapabilityRegistry fullRegistry = registry(fullDataSource);
        RunnerTaskStore fullTasks = tasks(fullDataSource);
        fullRegistry.publish(publication(
                RUNNER_A,
                CONTRACT_DIGEST,
                contract,
                RunnerState.ONLINE,
                0));
        fullTasks.enqueue(task(CONTRACT_DIGEST));
        assertThat(fullTasks.claim(claim(RUNNER_A, CONTRACT_DIGEST))).isEmpty();
    }

    @Test
    void aDifferentPublicContractCannotClaimTheTask() {
        DataSource dataSource = JpaTestDatabase.entityFirstDataSource("runner-contract-mismatch");
        RunnerCapabilityRegistry registry = registry(dataSource);
        RunnerTaskStore tasks = tasks(dataSource);
        registry.publish(publication(
                RUNNER_A,
                PUBLIC_BUNDLE_A,
                contract(OTHER_CONTRACT_DIGEST),
                RunnerState.ONLINE,
                1));
        tasks.enqueue(task(CONTRACT_DIGEST));

        assertThat(tasks.claim(claim(RUNNER_A, PUBLIC_BUNDLE_A))).isEmpty();
    }

    private RunnerCapabilityRegistry registry(DataSource dataSource) {
        return JpaTestDatabase.transactional(
                dataSource,
                new JpaRunnerCapabilityRegistry(JpaTestDatabase.entityManager(dataSource)));
    }

    private RunnerTaskStore tasks(DataSource dataSource) {
        return JpaTestDatabase.transactional(
                dataSource,
                new JpaRunnerTaskStore(JpaTestDatabase.entityManager(dataSource)));
    }

    private CapabilityContract contract(String contractDigest) {
        return new CapabilityContract(
                new CapabilityDescriptor(
                        CAPABILITY,
                        "Internal CMDB lookup",
                        "Returns one bounded internal asset record.",
                        CapabilityEffect.READ_ONLY,
                        "{\"additionalProperties\":false,\"type\":\"object\"}",
                        digest('c'),
                        "{\"additionalProperties\":false,\"type\":\"object\"}",
                        digest('d'),
                        Duration.ofSeconds(60),
                        4096,
                        Set.of("cmdb-report")),
                contractDigest);
    }

    private PublicBundlePublication publication(
            RunnerId runnerId,
            String publicBundleDigest,
            CapabilityContract contract,
            RunnerState state,
            int availableSlots) {
        return new PublicBundlePublication(
                runnerId,
                "org:scheduling-test",
                "internal.cmdb",
                "1.0.0",
                publicBundleDigest,
                List.of(contract),
                state,
                1,
                availableSlots,
                NOW);
    }

    private NewTask task(String capabilityContractDigest) {
        UUID taskId = UUID.randomUUID();
        return new NewTask(
                taskId,
                "org:scheduling-test",
                CAPABILITY,
                capabilityContractDigest,
                "contract-task-idempotency-" + taskId,
                "{\"assetId\":\"A-42\"}",
                "[]",
                "[]",
                0,
                NOW,
                NOW,
                NOW.plusSeconds(300),
                null);
    }

    private Claim claim(RunnerId runnerId, String publicBundleDigest) {
        return new Claim(
                "org:scheduling-test",
                runnerId,
                publicBundleDigest,
                NOW,
                Duration.ofSeconds(30));
    }

    private static String digest(char value) {
        return "sha256:" + String.valueOf(value).repeat(64);
    }
}
