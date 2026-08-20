package com.massimotter.weave.backend.service.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.files.application.NativeFilesMutationRepository;
import com.massimotter.weave.backend.files.application.NativeFilesMutationRepository.RecoveryCandidate;
import com.massimotter.weave.backend.files.application.NativeFilesMutationRepository.RecoveryPage;
import com.massimotter.weave.backend.files.domain.FilesAuthority.Lifecycle;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.EntityTagCondition;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.OperationKind;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.Sealed;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.Target;
import com.massimotter.weave.backend.files.port.FilesStreamingContentPort.Ingress;
import com.massimotter.weave.backend.files.port.NativeFilesContentStore;
import com.massimotter.weave.backend.files.port.NativeFilesDurableMutationPort;
import com.massimotter.weave.backend.files.port.ReplayableFileContent;
import com.massimotter.weave.backend.operation.domain.OperationIntent;
import com.massimotter.weave.backend.operation.domain.OperationIntent.HumanActor;
import com.massimotter.weave.backend.operation.domain.OperationIntent.ProtocolProjection;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class NativeFilesStreamingRecoveryDispatcherTest {

    private static final String DIGEST = "sha256:" + "a".repeat(64);

    @Test
    void recoversRelationalCandidateFromRetainedIngressWithoutRequestBody() {
        NativeFilesMutationRepository mutations = mock(NativeFilesMutationRepository.class);
        NativeFilesContentStore contentStore = mock(NativeFilesContentStore.class);
        NativeFilesDurableMutationPort adapter = mock(NativeFilesDurableMutationPort.class);
        AuditEventPublisher audit = mock(AuditEventPublisher.class);
        Ingress ingress = mock(Ingress.class);
        ReplayableFileContent content = mock(ReplayableFileContent.class);
        OperationIntent intent = intent("operation-1");
        Sealed plan = plan("operation-1");
        when(mutations.recoverablePutMutations(null, 16))
                .thenReturn(page(new RecoveryCandidate(intent, plan)));
        when(contentStore.reopen("operation-1")).thenReturn(ingress);
        when(ingress.content()).thenReturn(content);
        when(ingress.releaseIfTerminal()).thenReturn(true);
        when(contentStore.remove("operation-1")).thenReturn(true);

        var result = new NativeFilesStreamingRecoveryDispatcher(
                mutations, contentStore, adapter, audit).dispatchBatch();

        assertThat(result).isEqualTo(
                new NativeFilesStreamingRecoveryDispatcher.RecoveryResult(1, 1, 0));
        verify(adapter).execute(
                eq(intent),
                any(),
                eq(plan),
                any(NativeFilesDurableMutationPort.Put.class),
                eq("files-operation-intent:operation-1"),
                eq(null));
        verify(audit).publish(any());
        verify(ingress).releaseIfTerminal();
        verify(contentStore).remove("operation-1");
        verify(ingress).close();
    }

    @Test
    void unavailableRetainedIngressLeavesRelationalCandidateForLaterRetry() {
        NativeFilesMutationRepository mutations = mock(NativeFilesMutationRepository.class);
        NativeFilesContentStore contentStore = mock(NativeFilesContentStore.class);
        NativeFilesDurableMutationPort adapter = mock(NativeFilesDurableMutationPort.class);
        AuditEventPublisher audit = mock(AuditEventPublisher.class);
        OperationIntent intent = intent("operation-2");
        Sealed plan = plan("operation-2");
        when(mutations.recoverablePutMutations(null, 16))
                .thenReturn(page(new RecoveryCandidate(intent, plan)));
        when(contentStore.reopen("operation-2"))
                .thenThrow(new IllegalStateException("temporarily unavailable"));

        var result = new NativeFilesStreamingRecoveryDispatcher(
                mutations, contentStore, adapter, audit).dispatchBatch();

        assertThat(result).isEqualTo(
                new NativeFilesStreamingRecoveryDispatcher.RecoveryResult(1, 0, 1));
        verify(adapter, never()).execute(any(), any(), any(), any(), any(), any());
        verify(contentStore, never()).remove(any());
    }

    @Test
    void stableCursorAdvancesPastARepeatedlyUnavailableEarlierOperation() {
        NativeFilesMutationRepository mutations = mock(NativeFilesMutationRepository.class);
        NativeFilesContentStore contentStore = mock(NativeFilesContentStore.class);
        NativeFilesDurableMutationPort adapter = mock(NativeFilesDurableMutationPort.class);
        AuditEventPublisher audit = mock(AuditEventPublisher.class);
        RecoveryCandidate blocked = new RecoveryCandidate(intent("operation-1"), plan("operation-1"));
        RecoveryCandidate healthy = new RecoveryCandidate(intent("operation-2"), plan("operation-2"));
        when(mutations.recoverablePutMutations(null, 16)).thenReturn(page(blocked));
        when(mutations.recoverablePutMutations("operation-1", 16)).thenReturn(page(healthy));
        when(contentStore.reopen("operation-1"))
                .thenThrow(new IllegalStateException("temporarily unavailable"));
        Ingress ingress = mock(Ingress.class);
        when(contentStore.reopen("operation-2")).thenReturn(ingress);
        when(ingress.content()).thenReturn(mock(ReplayableFileContent.class));

        NativeFilesStreamingRecoveryDispatcher dispatcher =
                new NativeFilesStreamingRecoveryDispatcher(mutations, contentStore, adapter, audit);

        assertThat(dispatcher.dispatchBatch().retainedCount()).isEqualTo(1);
        assertThat(dispatcher.dispatchBatch().recoveredCount()).isEqualTo(1);
        verify(adapter).execute(
                eq(healthy.intent()),
                any(),
                eq(healthy.plan()),
                any(NativeFilesDurableMutationPort.Put.class),
                eq("files-operation-intent:operation-2"),
                eq(null));
    }

    @Test
    void rawCursorAdvancesPastAFullPageOfCorruptCandidates() {
        NativeFilesMutationRepository mutations = mock(NativeFilesMutationRepository.class);
        NativeFilesContentStore contentStore = mock(NativeFilesContentStore.class);
        NativeFilesDurableMutationPort adapter = mock(NativeFilesDurableMutationPort.class);
        AuditEventPublisher audit = mock(AuditEventPublisher.class);
        String corruptPageCursor = "operation-0016-corrupt";
        RecoveryCandidate healthy = new RecoveryCandidate(
                intent("operation-0017-healthy"),
                plan("operation-0017-healthy"));
        when(mutations.recoverablePutMutations(null, 16))
                .thenReturn(new RecoveryPage(List.of(), corruptPageCursor, 16));
        when(mutations.recoverablePutMutations(corruptPageCursor, 16))
                .thenReturn(page(healthy));
        Ingress ingress = mock(Ingress.class);
        when(contentStore.reopen("operation-0017-healthy")).thenReturn(ingress);
        when(ingress.content()).thenReturn(mock(ReplayableFileContent.class));

        NativeFilesStreamingRecoveryDispatcher dispatcher =
                new NativeFilesStreamingRecoveryDispatcher(mutations, contentStore, adapter, audit);

        assertThat(dispatcher.dispatchBatch()).isEqualTo(
                new NativeFilesStreamingRecoveryDispatcher.RecoveryResult(0, 0, 0));
        assertThat(dispatcher.dispatchBatch().recoveredCount()).isEqualTo(1);
        verify(mutations).recoverablePutMutations(corruptPageCursor, 16);
        verify(adapter).execute(
                eq(healthy.intent()),
                any(),
                eq(healthy.plan()),
                any(NativeFilesDurableMutationPort.Put.class),
                eq("files-operation-intent:operation-0017-healthy"),
                eq(null));
    }

    private static RecoveryPage page(RecoveryCandidate... candidates) {
        List<RecoveryCandidate> values = List.of(candidates);
        return new RecoveryPage(
                values,
                values.isEmpty() ? null : values.getLast().intent().operationRef(),
                values.size());
    }

    private static OperationIntent intent(String operationRef) {
        Instant now = Instant.parse("2026-08-20T00:00:00Z");
        return new OperationIntent(
                operationRef,
                "streaming-recovery-idempotency-key",
                "organization-1",
                new HumanActor("person-1", "subject-1"),
                "files",
                new ProtocolProjection("webdav", "webdav-put", "weave.webdav.files/v1"),
                DIGEST,
                DIGEST,
                List.of("file-path:" + DIGEST),
                "policy-1",
                "entitlement-1",
                7,
                OperationIntent.State.CREATED,
                "outbox-" + operationRef,
                null,
                null,
                null,
                null,
                now,
                now);
    }

    private static Sealed plan(String operationRef) {
        Sealed plan = mock(Sealed.class);
        Target target = mock(Target.class);
        when(plan.operationRef()).thenReturn(operationRef);
        when(plan.organizationRef()).thenReturn("organization-1");
        when(plan.spaceRef()).thenReturn("workspace-default");
        when(plan.providerBindingRevision()).thenReturn(7L);
        when(plan.operationKind()).thenReturn(OperationKind.PUT);
        when(plan.ifMatchCondition()).thenReturn(EntityTagCondition.notSupplied());
        when(plan.ifNoneMatchCondition()).thenReturn(EntityTagCondition.notSupplied());
        when(plan.targets()).thenReturn(List.of(target));
        when(target.resultLifecycleState()).thenReturn(Lifecycle.ACTIVE);
        when(target.targetPath()).thenReturn("/documents/recovered.bin");
        return plan;
    }
}
