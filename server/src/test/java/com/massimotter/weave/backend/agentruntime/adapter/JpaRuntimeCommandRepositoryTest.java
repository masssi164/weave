package com.massimotter.weave.backend.agentruntime.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeCommandReceipt;
import com.massimotter.weave.backend.agentruntime.port.RuntimeCommandConflictException;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class JpaRuntimeCommandRepositoryTest {
    private static final Instant NOW = Instant.parse("2026-07-20T09:00:00Z");

    private JpaRuntimeCommandRepository repository;

    @BeforeEach
    void setUp() {
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("arc-command-" + UUID.randomUUID() + ";MODE=PostgreSQL")
                .build();
        new ResourceDatabasePopulator(new ClassPathResource(
                "db/migration/V011__agent_runtime_control_foundation.sql"))
                .execute(database);
        new JdbcTemplate(database).execute(
                "alter table weave_agent_runtime_commands add column version bigint not null default 0");
        repository = AgentRuntimeJpaTestFactory.create(database).commands();
    }

    @Test
    void claimingTheSameCommandIsIdempotentAndCompletionIsStable() {
        RuntimeCommandReceipt first = claim("PROVISION");
        RuntimeCommandReceipt repeated = claim("PROVISION");

        assertThat(repeated).isEqualTo(first);
        RuntimeCommandReceipt completed = repository.complete(first, 7, NOW.plusSeconds(1));
        RuntimeCommandReceipt completedAgain = repository.complete(repeated, 7, NOW.plusSeconds(2));
        assertThat(completed.status()).isEqualTo(RuntimeCommandReceipt.Status.COMPLETED);
        assertThat(completedAgain.runtimeVersion()).isEqualTo(7);
    }

    @Test
    void anIdempotencyKeyCannotBeReusedForAnotherCommandOrCompletion() {
        RuntimeCommandReceipt receipt = claim("PROVISION");

        assertThatThrownBy(() -> claim("DEPROVISION"))
                .isInstanceOf(RuntimeCommandConflictException.class);
        assertThatThrownBy(() -> repository.claim(
                "org:example", "person:example", "idempotency-key-0001", "PROVISION",
                "cell:different", "audit:example", NOW))
                .isInstanceOf(RuntimeCommandConflictException.class);
        repository.complete(receipt, 7, NOW.plusSeconds(1));
        assertThatThrownBy(() -> repository.complete(receipt, 8, NOW.plusSeconds(2)))
                .isInstanceOf(RuntimeCommandConflictException.class);
    }

    @Test
    void aFailedCommandCanBeReconciledWithTheSameIdentity() {
        RuntimeCommandReceipt receipt = claim("PROVISION");
        RuntimeCommandReceipt failed = repository.fail(receipt, "keycloak-unavailable", NOW.plusSeconds(1));
        RuntimeCommandReceipt reclaimed = claim("PROVISION");
        RuntimeCommandReceipt completed = repository.complete(reclaimed, 1, NOW.plusSeconds(2));

        assertThat(failed.status()).isEqualTo(RuntimeCommandReceipt.Status.FAILED);
        assertThat(reclaimed.cellRef()).isEqualTo(receipt.cellRef());
        assertThat(completed.status()).isEqualTo(RuntimeCommandReceipt.Status.COMPLETED);
        assertThat(completed.failureCode()).isNull();
    }

    private RuntimeCommandReceipt claim(String command) {
        return repository.claim(
                "org:example", "person:example", "idempotency-key-0001", command,
                "cell:example", "audit:example", NOW);
    }
}
