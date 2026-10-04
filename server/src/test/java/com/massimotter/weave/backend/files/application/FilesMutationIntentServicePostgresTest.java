package com.massimotter.weave.backend.files.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.massimotter.weave.backend.files.adapter.JpaFilesMutationRepository.CorruptFilesMutationException;
import com.massimotter.weave.backend.files.application.FilesMutationIntentService.Command;
import com.massimotter.weave.backend.files.application.FilesMutationIntentService.ProviderBindingUnavailableException;
import com.massimotter.weave.backend.operation.adapter.OperationIntentJpaTestFactory;
import com.massimotter.weave.backend.operation.application.OperationIntentService;
import com.massimotter.weave.backend.operation.domain.OperationIntent.State;
import com.massimotter.weave.backend.providerbinding.adapter.JpaProviderBindingRepository;
import com.massimotter.weave.backend.providerbinding.adapter.ProviderBindingJpaTestFactory;
import com.massimotter.weave.backend.providerbinding.application.FilesProviderBindingBootstrap;
import com.massimotter.weave.backend.providerbinding.application.FilesProviderBindingBootstrap.ProviderBindingBootstrapConflictException;
import com.massimotter.weave.backend.providerbinding.application.ProviderBindingBootstrapProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
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
        assertThat(first.retry()).isFalse();
        assertThat(succeeded.intent().state()).isEqualTo(State.SUCCEEDED);
        assertThat(retry.retry()).isTrue();
        assertThat(retry.intent().operationRef()).isEqualTo(first.intent().operationRef());
        assertThat(fixture.jdbc().queryForObject(
                "select count(*) from weave_operation_intents where organization_ref = ?",
                Integer.class,
                fixture.organizationRef())).isEqualTo(1);
    }

    @Test
    void explicitRetryReloadsThePinnedBindingAfterTheActiveBindingChanges() {
        Fixture fixture = fixture();
        fixture.bindings().activate(
                fixture.organizationRef(), "files", 0, "nextcloud-webdav", "secretref:files:nextcloud", fixture.now());

        var first = fixture.service().begin(command(fixture, "human-supplied-idempotency-0002", "/Team/plan.md"));
        fixture.bindings().activate(
                fixture.organizationRef(), "files", 1, "weave-native", "secretref:files:native",
                fixture.now().plusSeconds(1));

        var retry = fixture.service().begin(command(fixture, "human-supplied-idempotency-0002", "/Team/plan.md"));

        assertThat(retry.retry()).isTrue();
        assertThat(retry.intent().operationRef()).isEqualTo(first.intent().operationRef());
        assertThat(retry.binding().revision()).isEqualTo(1);
        assertThat(retry.binding().adapterKey()).isEqualTo("nextcloud-webdav");
        assertThat(fixture.bindings().current(fixture.organizationRef(), "files"))
                .get()
                .extracting(binding -> binding.revision(), binding -> binding.adapterKey())
                .containsExactly(2L, "weave-native");
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
    void publicNativeBeginFailsClosedWhenBootstrapDidNotProvisionTheScope() {
        Fixture fixture = fixture();
        fixture.bindings().activate(
                fixture.organizationRef(),
                "files",
                0,
                "weave-native",
                "profile:weave-native",
                fixture.now());
        NativeFilesMutationRepository nativeMutations = mock(NativeFilesMutationRepository.class);
        FilesMutationIntentService nativeService = new FilesMutationIntentService(
                fixture.intents(),
                fixture.bindings(),
                nativeMutations);
        var prepared = nativeService.prepare(command(
                fixture,
                "native-missing-head-idempotency-0001",
                "/Team/unconfigured.md"));
        FilesScope scope = new FilesScope(fixture.organizationRef(), "workspace-default");
        when(nativeMutations.begin(eq(prepared.candidate()), eq(scope), any()))
                .thenThrow(new CorruptFilesMutationException("native Files stream head is missing"));

        assertThatThrownBy(() -> nativeService.beginNative(
                prepared,
                scope,
                () -> {
                    throw new AssertionError("an unprovisioned scope must fail before planning");
                }))
                .isInstanceOf(CorruptFilesMutationException.class)
                .hasMessageContaining("stream head is missing");

        verify(nativeMutations).begin(eq(prepared.candidate()), eq(scope), any());
        assertThat(fixture.jdbc().queryForObject(
                "select count(*) from weave_operation_intents where organization_ref = ?",
                Integer.class,
                fixture.organizationRef())).isZero();
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
        JpaTestDatabase.initializeSchema(dataSource);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        // PostgreSQL truncates this nanosecond value to microsecond precision on round-trip.
        // A successful insert must still be reported as new rather than mistaken for a retry.
        Instant now = Instant.parse("2026-07-22T02:00:00.123456789Z");
        var bindings = ProviderBindingJpaTestFactory.create(dataSource);
        var intents = new OperationIntentService(
                OperationIntentJpaTestFactory.create(dataSource),
                Clock.fixed(now, ZoneOffset.UTC));
        String organizationRef = "org:test:" + UUID.randomUUID();
        return new Fixture(
                new FilesMutationIntentService(intents, bindings),
                intents,
                bindings,
                jdbc,
                now,
                organizationRef);
    }

    private record Fixture(
            FilesMutationIntentService service,
            OperationIntentService intents,
            JpaProviderBindingRepository bindings,
            JdbcTemplate jdbc,
            Instant now,
            String organizationRef) {
    }
}
