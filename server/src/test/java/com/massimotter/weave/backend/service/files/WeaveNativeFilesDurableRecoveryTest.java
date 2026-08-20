package com.massimotter.weave.backend.service.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.massimotter.weave.backend.config.WeaveNativeFilesProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.files.adapter.FilesAuthorityJpaTestFactory;
import com.massimotter.weave.backend.files.adapter.JpaFilesMutationRepository.AuthorizationDeniedException;
import com.massimotter.weave.backend.files.adapter.JpaFilesMutationRepository.CorruptFilesMutationException;
import com.massimotter.weave.backend.files.application.FilesMutationIntentService;
import com.massimotter.weave.backend.files.application.FilesMutationTargetCodec;
import com.massimotter.weave.backend.files.application.NativeFilesMutationRepository;
import com.massimotter.weave.backend.files.application.NativeFilesMutationRepository.CommitOutcome;
import com.massimotter.weave.backend.files.application.NativeFilesMutationRepository.CommitProbe;
import com.massimotter.weave.backend.files.application.NativeFilesMutationRepository.FinalizationResult;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileWrite;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.Sealed;
import com.massimotter.weave.backend.files.port.FilesProviderPort.FilesRequestScope;
import com.massimotter.weave.backend.files.port.NativeFilesDurableMutationPort.Put;
import com.massimotter.weave.backend.operation.domain.OperationIntent;
import com.massimotter.weave.backend.operation.domain.OperationIntent.HumanActor;
import com.massimotter.weave.backend.operation.domain.OperationIntent.ProtocolProjection;
import com.massimotter.weave.backend.operation.domain.OperationIntent.Reconciliation;
import com.massimotter.weave.backend.operation.domain.OperationIntent.ReconciliationOutcome;
import com.massimotter.weave.backend.operation.domain.OperationIntent.State;
import com.massimotter.weave.backend.testing.JpaTestDatabase;
import jakarta.persistence.PersistenceException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

class WeaveNativeFilesDurableRecoveryTest {

    private static final Instant NOW = Instant.parse("2026-08-20T12:00:00Z");
    private static final FilesRequestScope SCOPE =
            new FilesRequestScope("org:recovery", "space:recovery", 7);

    @TempDir
    Path directory;

    @Test
    void unknownFinalizationBecomesAmbiguousAndRetryResumesTheSamePlan() {
        Fixture fixture = fixture("unknown");
        RuntimeException uncertain = new RuntimeException("injected uncertain commit");
        OperationIntent ambiguous = state(
                fixture.intent(),
                State.AMBIGUOUS,
                new Reconciliation(0, ReconciliationOutcome.PENDING, null, null));
        OperationIntent reconciling = state(
                fixture.intent(),
                State.RECONCILING,
                new Reconciliation(1, ReconciliationOutcome.PENDING, NOW, null));
        OperationIntent succeeded = terminal(fixture.intent(), State.SUCCEEDED);
        when(fixture.repository().requireSealed(fixture.plan().operationRef()))
                .thenReturn(fixture.plan());
        when(fixture.repository().probe(fixture.plan().operationRef()))
                .thenReturn(
                        probe(CommitOutcome.NOT_COMMITTED, fixture.intent()),
                        probe(CommitOutcome.NOT_COMMITTED, fixture.intent()),
                        probe(CommitOutcome.NOT_COMMITTED, ambiguous));
        when(fixture.repository().markAmbiguous(eq(fixture.intent()), anyString()))
                .thenReturn(ambiguous);
        when(fixture.repository().beginReconciliation(ambiguous)).thenReturn(reconciling);
        when(fixture.repository().finalizeSuccess(
                any(), eq(fixture.plan()), anyString(), anyString(), any()))
                .thenThrow(uncertain)
                .thenReturn(new FinalizationResult(succeeded, 1, 1));

        assertThatThrownBy(() -> fixture.adapter().execute(
                fixture.intent(), SCOPE, fixture.plan(), fixture.put(), "audit:recovery", null))
                .isInstanceOfSatisfying(ApiErrorException.class, failure -> {
                    assertThat(failure.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(failure.code()).isEqualTo("files-native-finalization-outcome-unknown");
                });

        var recovered = fixture.adapter().execute(
                ambiguous, SCOPE, fixture.plan(), fixture.put(), "audit:recovery", null);

        assertThat(recovered.item().path()).isEqualTo(fixture.put().write().path());
        verify(fixture.repository()).markAmbiguous(eq(fixture.intent()), anyString());
        verify(fixture.repository()).beginReconciliation(ambiguous);
        verify(fixture.repository(), never()).recordFailure(
                any(), eq(false), anyString(), anyString());
    }

    @Test
    void postCommitExceptionReturnsOnlyAfterTheExactProbeConfirmsSuccess() {
        Fixture fixture = fixture("post-commit");
        OperationIntent succeeded = terminal(fixture.intent(), State.SUCCEEDED);
        when(fixture.repository().requireSealed(fixture.plan().operationRef()))
                .thenReturn(fixture.plan());
        when(fixture.repository().probe(fixture.plan().operationRef()))
                .thenReturn(
                        probe(CommitOutcome.NOT_COMMITTED, fixture.intent()),
                        new CommitProbe(CommitOutcome.SUCCEEDED, succeeded, 1L, 1L));
        when(fixture.repository().finalizeSuccess(
                any(), eq(fixture.plan()), anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("injected after commit"));

        var result = fixture.adapter().execute(
                fixture.intent(), SCOPE, fixture.plan(), fixture.put(), "audit:post-commit", null);

        assertThat(result.item().path()).isEqualTo(fixture.put().write().path());
        verify(fixture.repository(), never()).markAmbiguous(any(), anyString());
        verify(fixture.repository(), never()).recordFailure(
                any(), eq(false), anyString(), anyString());
    }

    @Test
    void concurrentFinalizerSuccessIsReprobedBeforeAnAmbiguousRetryReturns() {
        Fixture fixture = fixture("concurrent-success");
        OperationIntent ambiguous = state(
                fixture.intent(),
                State.AMBIGUOUS,
                new Reconciliation(0, ReconciliationOutcome.PENDING, null, null));
        OperationIntent succeeded = terminal(fixture.intent(), State.SUCCEEDED);
        when(fixture.repository().requireSealed(fixture.plan().operationRef()))
                .thenReturn(fixture.plan());
        when(fixture.repository().probe(fixture.plan().operationRef()))
                .thenReturn(
                        probe(CommitOutcome.NOT_COMMITTED, ambiguous),
                        new CommitProbe(CommitOutcome.SUCCEEDED, succeeded, 1L, 1L));
        when(fixture.repository().beginReconciliation(ambiguous)).thenReturn(succeeded);

        var result = fixture.adapter().execute(
                ambiguous, SCOPE, fixture.plan(), fixture.put(), "audit:concurrent-success", null);

        assertThat(result.item().path()).isEqualTo(fixture.put().write().path());
        verify(fixture.repository()).beginReconciliation(ambiguous);
        verify(fixture.repository(), never()).finalizeSuccess(any(), any(), anyString(), anyString(), any());
    }

    @Test
    void concurrentFinalizerFailureIsReprobedBeforeAnAmbiguousRetryStops() {
        Fixture fixture = fixture("concurrent-failure");
        OperationIntent ambiguous = state(
                fixture.intent(),
                State.AMBIGUOUS,
                new Reconciliation(0, ReconciliationOutcome.PENDING, null, null));
        OperationIntent failed = terminal(fixture.intent(), State.FAILED);
        when(fixture.repository().requireSealed(fixture.plan().operationRef()))
                .thenReturn(fixture.plan());
        when(fixture.repository().probe(fixture.plan().operationRef()))
                .thenReturn(
                        probe(CommitOutcome.NOT_COMMITTED, ambiguous),
                        probe(CommitOutcome.TERMINAL_FAILURE, failed));
        when(fixture.repository().beginReconciliation(ambiguous)).thenReturn(failed);

        assertThatThrownBy(() -> fixture.adapter().execute(
                ambiguous, SCOPE, fixture.plan(), fixture.put(), "audit:concurrent-failure", null))
                .isInstanceOfSatisfying(ApiErrorException.class, failure -> {
                    assertThat(failure.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(failure.code()).isEqualTo("files-native-operation-terminal");
                });

        verify(fixture.repository()).beginReconciliation(ambiguous);
        verify(fixture.repository(), never()).finalizeSuccess(any(), any(), anyString(), anyString(), any());
    }

    @Test
    void provenAuthorizationNonCommitRecordsDeniedWithTheReservedOutboxTransition() {
        Fixture fixture = fixture("authorization-denied");
        OperationIntent denied = terminal(fixture.intent(), State.DENIED);
        when(fixture.repository().requireSealed(fixture.plan().operationRef()))
                .thenReturn(fixture.plan());
        when(fixture.repository().probe(fixture.plan().operationRef()))
                .thenReturn(
                        probe(CommitOutcome.NOT_COMMITTED, fixture.intent()),
                        probe(CommitOutcome.NOT_COMMITTED, fixture.intent()));
        when(fixture.repository().finalizeSuccess(
                any(), eq(fixture.plan()), anyString(), anyString(), any()))
                .thenThrow(new AuthorizationDeniedException(fixture.plan().operationRef()));
        when(fixture.repository().recordFailure(
                eq(fixture.intent()), eq(true), anyString(), eq("audit:denied")))
                .thenReturn(denied);

        assertThatThrownBy(() -> fixture.adapter().execute(
                fixture.intent(), SCOPE, fixture.plan(), fixture.put(), "audit:denied", null))
                .isInstanceOfSatisfying(ApiErrorException.class, failure -> {
                    assertThat(failure.status()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(failure.code()).isEqualTo("files-forbidden");
                });

        verify(fixture.repository()).recordFailure(
                eq(fixture.intent()), eq(true), anyString(), eq("audit:denied"));
        verify(fixture.repository(), never()).markAmbiguous(any(), anyString());
    }

    @Test
    void provenPersistenceNonCommitRecordsFailedInsteadOfInventingAmbiguity() {
        Fixture fixture = fixture("persistence-failed");
        OperationIntent failed = terminal(fixture.intent(), State.FAILED);
        when(fixture.repository().requireSealed(fixture.plan().operationRef()))
                .thenReturn(fixture.plan());
        when(fixture.repository().probe(fixture.plan().operationRef()))
                .thenReturn(
                        probe(CommitOutcome.NOT_COMMITTED, fixture.intent()),
                        probe(CommitOutcome.NOT_COMMITTED, fixture.intent()));
        when(fixture.repository().finalizeSuccess(
                any(), eq(fixture.plan()), anyString(), anyString(), any()))
                .thenThrow(new PersistenceException("injected proven non-commit"));
        when(fixture.repository().recordFailure(
                eq(fixture.intent()), eq(false), anyString(), eq("audit:persistence-failed")))
                .thenReturn(failed);

        assertThatThrownBy(() -> fixture.adapter().execute(
                fixture.intent(),
                SCOPE,
                fixture.plan(),
                fixture.put(),
                "audit:persistence-failed",
                null))
                .isInstanceOfSatisfying(ApiErrorException.class, failure -> {
                    assertThat(failure.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(failure.code()).isEqualTo("files-native-persistence-failed");
                });

        verify(fixture.repository()).recordFailure(
                eq(fixture.intent()), eq(false), anyString(), eq("audit:persistence-failed"));
        verify(fixture.repository(), never()).markAmbiguous(any(), anyString());
    }

    @Test
    void committedFailureSettlementIsAcceptedAfterItsCommitResponseIsLost() {
        Fixture fixture = fixture("settlement-post-commit");
        OperationIntent failed = terminal(fixture.intent(), State.FAILED);
        when(fixture.repository().requireSealed(fixture.plan().operationRef()))
                .thenReturn(fixture.plan());
        when(fixture.repository().probe(fixture.plan().operationRef()))
                .thenReturn(
                        probe(CommitOutcome.NOT_COMMITTED, fixture.intent()),
                        probe(CommitOutcome.NOT_COMMITTED, fixture.intent()),
                        probe(CommitOutcome.TERMINAL_FAILURE, failed));
        when(fixture.repository().finalizeSuccess(
                any(), eq(fixture.plan()), anyString(), anyString(), any()))
                .thenThrow(new PersistenceException("injected finalization non-commit"));
        when(fixture.repository().recordFailure(
                eq(fixture.intent()), eq(false), anyString(), eq("audit:settlement-post-commit")))
                .thenThrow(new PersistenceException("injected response loss after settlement commit"));

        assertThatThrownBy(() -> fixture.adapter().execute(
                fixture.intent(),
                SCOPE,
                fixture.plan(),
                fixture.put(),
                "audit:settlement-post-commit",
                null))
                .isInstanceOfSatisfying(ApiErrorException.class, failure -> {
                    assertThat(failure.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(failure.code()).isEqualTo("files-native-persistence-failed");
                });

        verify(fixture.repository()).recordFailure(
                eq(fixture.intent()), eq(false), anyString(), eq("audit:settlement-post-commit"));
    }

    @Test
    void concurrentSuccessReturnedByFailureSettlementWinsTheRace() {
        Fixture fixture = fixture("settlement-success-race");
        OperationIntent succeeded = terminal(fixture.intent(), State.SUCCEEDED);
        when(fixture.repository().requireSealed(fixture.plan().operationRef()))
                .thenReturn(fixture.plan());
        when(fixture.repository().probe(fixture.plan().operationRef()))
                .thenReturn(
                        probe(CommitOutcome.NOT_COMMITTED, fixture.intent()),
                        probe(CommitOutcome.NOT_COMMITTED, fixture.intent()));
        when(fixture.repository().finalizeSuccess(
                any(), eq(fixture.plan()), anyString(), anyString(), any()))
                .thenThrow(new PersistenceException("injected concurrent finalization race"));
        when(fixture.repository().recordFailure(
                eq(fixture.intent()), eq(false), anyString(), eq("audit:settlement-success-race")))
                .thenReturn(succeeded);

        var result = fixture.adapter().execute(
                fixture.intent(),
                SCOPE,
                fixture.plan(),
                fixture.put(),
                "audit:settlement-success-race",
                null);

        assertThat(result.item().path()).isEqualTo(fixture.put().write().path());
        verify(fixture.repository(), never()).markAmbiguous(any(), anyString());
    }

    @Test
    void retryStopsBeforeBlobEffectsWhenTheProvisionedHeadIsMissing() {
        Fixture fixture = fixture("missing-head-retry");
        when(fixture.repository().requireSealed(fixture.plan().operationRef()))
                .thenReturn(fixture.plan());
        when(fixture.repository().probe(fixture.plan().operationRef()))
                .thenThrow(new CorruptFilesMutationException(
                        "the Files stream head is missing before blob retry"));

        assertThatThrownBy(() -> fixture.adapter().execute(
                        fixture.intent(),
                        SCOPE,
                        fixture.plan(),
                        fixture.put(),
                        "audit:missing-head-retry",
                        null))
                .isInstanceOf(CorruptFilesMutationException.class);
        assertThat(fixture.blobs().inventory(
                new com.massimotter.weave.backend.files.port.BlobStorePort.BlobScope(
                        SCOPE.organizationRef(), SCOPE.spaceRef()),
                10)).isEmpty();
        verify(fixture.repository(), never()).finalizeSuccess(
                any(), any(), anyString(), anyString(), any());
    }

    private Fixture fixture(String suffix) {
        var authority = FilesAuthorityJpaTestFactory.create(
                JpaTestDatabase.entityFirstDataSource("native_recovery_" + suffix));
        var repository = mock(NativeFilesMutationRepository.class);
        var blobs = new FilesystemBlobStore(new WeaveNativeFilesProperties(
                directory.resolve(suffix), 1024 * 1024, 100));
        var codec = new FilesMutationTargetCodec(
                JsonMapper.builder().findAndAddModules().build());
        var adapter = new WeaveNativeFilesAdapter(
                authority,
                blobs,
                Clock.fixed(NOW, ZoneOffset.UTC),
                100,
                repository,
                codec);
        FilePath path = new FilePath("/" + suffix + ".txt");
        byte[] content = ("content-" + suffix).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Put put = new Put(new FileWrite(path, content, "text/plain"));
        OperationIntent intent = intent(suffix, path, content);
        Sealed plan = adapter.plan(intent, SCOPE, put);
        return new Fixture(adapter, repository, blobs, intent, plan, put);
    }

    private OperationIntent intent(String suffix, FilePath path, byte[] content) {
        String canonicalArguments = String.join(
                "\n",
                path.value(),
                "text/plain",
                "",
                "",
                "lock-token:none",
                FilesMutationIntentService.digest(content));
        return new OperationIntent(
                "operation:recovery:" + suffix,
                "idempotency-recovery-" + suffix,
                SCOPE.organizationRef(),
                new HumanActor("person:alice", "subject:alice"),
                "files",
                new ProtocolProjection("webdav", "webdav-put", "weave.webdav.files/v1"),
                FilesMutationIntentService.digest("webdav-put"),
                FilesMutationIntentService.digest(canonicalArguments),
                List.of(
                        "file-path:" + FilesMutationIntentService.digest(path.value()),
                        "lock-token:none"),
                "policy:recovery",
                "entitlement:recovery",
                SCOPE.providerBindingRevision(),
                State.CREATED,
                "outbox:recovery:" + suffix,
                null,
                null,
                null,
                null,
                NOW,
                NOW);
    }

    private CommitProbe probe(CommitOutcome outcome, OperationIntent intent) {
        return new CommitProbe(outcome, intent, null, null);
    }

    private OperationIntent state(
            OperationIntent source,
            State state,
            Reconciliation reconciliation) {
        return new OperationIntent(
                source.operationRef(), source.idempotencyKey(), source.organizationRef(), source.actor(),
                source.domain(), source.projection(), source.actionDigest(), source.canonicalArgumentsDigest(),
                source.objectRefs(), source.policyRevision(), source.entitlementRevision(),
                source.providerBindingRevision(), state, source.outboxRef(),
                FilesMutationIntentService.digest("correlation"), reconciliation, null, null,
                source.createdAt(), source.updatedAt().plusSeconds(1));
    }

    private OperationIntent terminal(OperationIntent source, State state) {
        return new OperationIntent(
                source.operationRef(), source.idempotencyKey(), source.organizationRef(), source.actor(),
                source.domain(), source.projection(), source.actionDigest(), source.canonicalArgumentsDigest(),
                source.objectRefs(), source.policyRevision(), source.entitlementRevision(),
                source.providerBindingRevision(), state, source.outboxRef(), null, null,
                FilesMutationIntentService.digest("result"), "audit:result",
                source.createdAt(), source.updatedAt().plusSeconds(2));
    }

    private record Fixture(
            WeaveNativeFilesAdapter adapter,
            NativeFilesMutationRepository repository,
            FilesystemBlobStore blobs,
            OperationIntent intent,
            Sealed plan,
            Put put) {
    }
}
