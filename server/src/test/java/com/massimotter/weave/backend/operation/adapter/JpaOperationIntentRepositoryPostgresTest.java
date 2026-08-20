package com.massimotter.weave.backend.operation.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.operation.application.OperationIntentService;
import com.massimotter.weave.backend.operation.application.OperationIntentService.BeginCommand;
import com.massimotter.weave.backend.operation.application.OperationIntentService.IdempotencyKeyConflictException;
import com.massimotter.weave.backend.operation.domain.OperationIntent.HumanActor;
import com.massimotter.weave.backend.operation.domain.OperationIntent.ProtocolProjection;
import com.massimotter.weave.backend.operation.domain.OperationIntent.State;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import com.massimotter.weave.backend.testing.JpaTestDatabase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@Tag("postgres")
class JpaOperationIntentRepositoryPostgresTest {

    private static final String DIGEST_A = "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String DIGEST_B = "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void intentOutboxAndEquivalentRetryShareOneDurableEffect() {
        DriverManagerDataSource dataSource = dataSource();
        JpaTestDatabase.initializeSchema(dataSource);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        var repository = OperationIntentJpaTestFactory.create(dataSource);
        var service = new OperationIntentService(
                repository,
                Clock.fixed(Instant.parse("2026-07-21T12:00:00Z"), ZoneOffset.UTC));

        var first = service.begin(command(DIGEST_A));
        var retry = service.begin(command(DIGEST_A));
        var dispatching = service.markDispatching(first.intent());
        var succeeded = service.succeed(dispatching, DIGEST_B, "audit:files:write:1");

        assertThat(first.retry()).isFalse();
        assertThat(retry.retry()).isTrue();
        assertThat(retry.intent().operationRef()).isEqualTo(first.intent().operationRef());
        assertThat(succeeded.state()).isEqualTo(State.SUCCEEDED);
        assertThat(jdbc.queryForObject("select count(*) from weave_operation_intents", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from weave_operation_outbox", Integer.class)).isEqualTo(3);
    }

    @Test
    void conflictingArgumentsForOneKeyFailClosed() {
        DriverManagerDataSource dataSource = dataSource();
        JpaTestDatabase.initializeSchema(dataSource);
        var repository = OperationIntentJpaTestFactory.create(dataSource);
        var service = new OperationIntentService(repository, Clock.systemUTC());

        service.begin(command(DIGEST_A));

        assertThatThrownBy(() -> service.begin(command(DIGEST_B)))
                .isInstanceOf(IdempotencyKeyConflictException.class)
                .hasMessageContaining("different canonical arguments");
    }

    private BeginCommand command(String argumentsDigest) {
        return new BeginCommand(
                "files-webdav-idempotency-0001",
                "org:example",
                new HumanActor("person:alice", "subject:alice"),
                "files",
                new ProtocolProjection("webdav", "PUT", "weave-webdav-core-v2"),
                DIGEST_A,
                argumentsDigest,
                List.of("file:document"),
                "policy:3",
                "entitlement:7",
                4);
    }

    private DriverManagerDataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(POSTGRES.getDriverClassName());
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return dataSource;
    }
}
