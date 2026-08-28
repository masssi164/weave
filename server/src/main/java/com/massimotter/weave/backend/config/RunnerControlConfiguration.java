package com.massimotter.weave.backend.config;

import com.massimotter.weave.backend.runner.adapter.JpaRunnerTaskStore;
import com.massimotter.weave.backend.runner.adapter.JpaRunnerWorkloadIdentityDirectory;
import com.massimotter.weave.backend.runner.application.InMemoryRunnerTaskAvailabilitySignal;
import com.massimotter.weave.backend.runner.application.RunnerTaskAvailabilitySignal;
import com.massimotter.weave.backend.runner.application.RunnerTaskClaimService;
import com.massimotter.weave.backend.runner.application.RunnerTaskQueue;
import com.massimotter.weave.backend.runner.application.RunnerTaskStore;
import com.massimotter.weave.backend.runner.application.RunnerWorkloadIdentityDirectory;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Minimal Engine composition for durable private Runner task control. */
@Configuration(proxyBeanMethods = false)
public class RunnerControlConfiguration {

    private static final Duration TASK_LEASE_DURATION = Duration.ofSeconds(60);

    @Bean
    RunnerTaskStore runnerTaskStore(EntityManager entityManager) {
        return new JpaRunnerTaskStore(entityManager);
    }

    @Bean
    RunnerWorkloadIdentityDirectory runnerWorkloadIdentityDirectory(
            EntityManager entityManager) {
        return new JpaRunnerWorkloadIdentityDirectory(entityManager);
    }

    @Bean
    RunnerTaskAvailabilitySignal runnerTaskAvailabilitySignal() {
        return new InMemoryRunnerTaskAvailabilitySignal();
    }

    @Bean
    RunnerTaskQueue runnerTaskQueue(
            RunnerTaskStore taskStore,
            RunnerTaskAvailabilitySignal availabilitySignal) {
        return new RunnerTaskQueue(taskStore, availabilitySignal, Clock.systemUTC());
    }

    @Bean
    RunnerTaskClaimService runnerTaskClaimService(RunnerTaskQueue taskQueue) {
        return new RunnerTaskClaimService(
                taskQueue,
                Clock.systemUTC(),
                TASK_LEASE_DURATION);
    }
}
