package com.massimotter.weave.backend.files.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.files.application.FilesMutationIntentService.Command;
import com.massimotter.weave.backend.files.application.FilesMutationIntentService.ProviderBindingUnavailableException;
import com.massimotter.weave.backend.operation.adapter.JdbcOperationIntentRepository;
import com.massimotter.weave.backend.operation.application.OperationIntentService;
import com.massimotter.weave.backend.operation.domain.OperationIntent.State;
import com.massimotter.weave.backend.providerbinding.adapter.JdbcProviderBindingRepository;
import com.massimotter.weave.backend.providerbinding.application.FilesProviderBindingBootstrap;
import com.massimotter.weave.backend.providerbinding.application.FilesProviderBindingBootstrap.ProviderBindingBootstrapConflictException;
import com.massimotter.weave.backend.providerbinding.application.ProviderBindingBootstrapProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class FilesMutationIntentServicePostgresTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void pinsBindingAndReusesOneIntentAcrossEquivalentRetries() {
        Fixture fixture = fixture();
        fixture.bindings().activate(
                fixture.organizationRef(), "files", 0, "nextcloud-webdav", "secretref:files:nextcloud", fixture.now());

        var first = fixture.service().begin(command(fixture, "human-supplied-idempotency-0001", "/Team/plan.md"));
        var dispatching = fixture.service().dispatch(first);
        var succeeded = fixture.service().succeed(dispatching, "etag:v1", "audit:files:put:1");
        var retry = fixture.service().begin(command(fixture, "human-supplied-idempotency-0001", "/Team/plan.md"));

        assertThat(first.binding().adapterKey()).isEqualTo("nextcloud-webdav");
        assertThat(first.binding().revision()).isEqualTo(1);
        assertThat(succeeded.intent().state()).isEqualTo(State.SUCCEEDED);
        assertThat(retry.retry()).isTrue();
        assertThat(retry.intent().operationRef()).isEqualTo(first.intent().operationRef());
        assertThat(fixture.jdbc().queryForObject(
                "select count(*) from weave_operation_intents where organization_ref = ?",
                Integer.class,
                fixture.organizationRef())).isEqualTo(1);
    }

    @Test
    void failsClosedWithoutAnActiveFilesBinding() {
        Fixture fixture = fixture();

        assertThatThrownBy(() -> fixture.service().begin(command(fixture, null, "/Team/plan.md")))
                .isInstanceOf(ProviderBindingUnavailableException.class)
                .hasMessageContaining(fixture.organizationRef());
        assertThat(fixture.jdbc().queryForObject(
                "select count(*) from weave_operation_intents where organization_ref = ?",
                Integer.class,
                fixture.organizationRef())).isZero();
    }

    @Test
    void ambiguousDispatchEntersDurableReconciliation() {
        Fixture fixture = fixture();
        fixture.bindings().activate(
                fixture.organizationRef(), "files", 0, "nextcloud-webdav", "secretref:files:nextcloud", fixture.now());

        var dispatching = fixture.service().dispatch(fixture.service().begin(command(fixture, null, "/Team/plan.md")));
        var ambiguous = fixture.service().ambiguous(dispatching, "support-safe:timeout");
        var reconciling = fixture.service().reconcile(ambiguous);

        assertThat(ambiguous.intent().state()).isEqualTo(State.AMBIGUOUS);
        assertThat(reconciling.intent().state()).isEqualTo(State.RECONCILING);
        assertThat(reconciling.intent().reconciliation().attempts()).isEqualTo(1);
    }

    @Test
    void dogfoodBindingBootstrapIsIdempotentAndNeverOverwritesAuthority() {
        Fixture fixture = fixture();
        var properties = new ProviderBindingBootstrapProperties(
                true, fixture.organizationRef(), "nextcloud-webdav", "secretref:files:nextcloud");
        var bootstrap = new FilesProviderBindingBootstrap(
                fixture.bindings(), properties, Clock.fixed(fixture.now(), ZoneOffset.UTC));

        var first = bootstrap.reconcile();
        var second = bootstrap.reconcile();

        assertThat(first.revision()).isEqualTo(1);
        assertThat(second).isEqualTo(first);
        assertThat(fixture.jdbc().queryForObject(
                "select count(*) from weave_provider_bindings where organization_ref = ?",
                Integer.class,
                fixture.organizationRef())).isEqualTo(1);

        var conflicting = new FilesProviderBindingBootstrap(
                fixture.bindings(),
                new ProviderBindingBootstrapProperties(
                        true, fixture.organizationRef(), "weave-s3-minio", "secretref:files:minio"),
                Clock.fixed(fixture.now().plusSeconds(1), ZoneOffset.UTC));
        assertThatThrownBy(conflicting::reconcile)
                .isInstanceOf(ProviderBindingBootstrapConflictException.class);
        assertThat(fixture.bindings().current(fixture.organizationRef(), "files")).contains(first);
    }

    private Command command(Fixture fixture, String idempotencyKey, String path) {
        return new Command(
                idempotencyKey,
                fixture.organizationRef(),
                "person:alice",
                "subject:alice",
                "PUT",
                "{\"path\":\"" + path + "\",\"contentDigest\":\"sha256:abc\"}",
                List.of("file:stable-plan"),
                "policy:3",
                "entitlement:7");
    }

    private Fixture fixture() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(POSTGRES.getDriverClassName());
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        var transactions = new DataSourceTransactionManager(dataSource);
        Instant now = Instant.parse("2026-07-22T02:00:00Z");
        var bindings = new JdbcProviderBindingRepository(jdbc, transactions);
        var intents = new OperationIntentService(
                new JdbcOperationIntentRepository(
                        jdbc, new ObjectMapper().findAndRegisterModules(), transactions),
                Clock.fixed(now, ZoneOffset.UTC));
        String organizationRef = "org:test:" + UUID.randomUUID();
        return new Fixture(new FilesMutationIntentService(intents, bindings), bindings, jdbc, now, organizationRef);
    }

    private record Fixture(
            FilesMutationIntentService service,
            JdbcProviderBindingRepository bindings,
            JdbcTemplate jdbc,
            Instant now,
            String organizationRef) {
    }
}
