package com.massimotter.weave.backend.service.files;

import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.files.application.NativeFilesMutationRepository;
import com.massimotter.weave.backend.files.application.NativeFilesMutationRepository.RecoveryPage;
import com.massimotter.weave.backend.files.domain.FilesAuthority.Lifecycle;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.OperationKind;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.Sealed;
import com.massimotter.weave.backend.files.port.FilesProviderPort.FilesRequestScope;
import com.massimotter.weave.backend.files.port.FilesStreamingContentPort.Ingress;
import com.massimotter.weave.backend.files.port.NativeFilesContentStore;
import com.massimotter.weave.backend.files.port.NativeFilesDurableMutationPort;
import com.massimotter.weave.backend.files.port.NativeFilesDurableMutationPort.Put;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Bounded relationally-authoritative recovery of committed PUT plans with retained ingress. */
@Service
@ConditionalOnProperty(
        name = "weave.files.provider",
        havingValue = "weave-native",
        matchIfMissing = true)
public final class NativeFilesStreamingRecoveryDispatcher {

    static final int RECOVERY_BATCH_LIMIT = 16;

    private final NativeFilesMutationRepository mutations;
    private final NativeFilesContentStore contentStore;
    private final NativeFilesDurableMutationPort adapter;
    private final AuditEventPublisher audit;
    private final AtomicReference<String> afterOperationRef = new AtomicReference<>();

    public NativeFilesStreamingRecoveryDispatcher(
            NativeFilesMutationRepository mutations,
            @Lazy NativeFilesContentStore contentStore,
            NativeFilesDurableMutationPort adapter,
            AuditEventPublisher audit) {
        this.mutations = Objects.requireNonNull(mutations, "mutations must not be null");
        this.contentStore = Objects.requireNonNull(contentStore, "contentStore must not be null");
        this.adapter = Objects.requireNonNull(adapter, "adapter must not be null");
        this.audit = Objects.requireNonNull(audit, "audit must not be null");
    }

    @Scheduled(initialDelay = 5_000, fixedDelay = 5_000)
    public void dispatchScheduledBatch() {
        dispatchBatch();
    }

    public RecoveryResult dispatchBatch() {
        int recovered = 0;
        int retained = 0;
        String after = afterOperationRef.get();
        RecoveryPage page = mutations.recoverablePutMutations(after, RECOVERY_BATCH_LIMIT);
        if (page.scannedCount() == 0 && after != null) {
            afterOperationRef.set(null);
            page = mutations.recoverablePutMutations(null, RECOVERY_BATCH_LIMIT);
        }
        if (page.scannedCount() > 0) {
            afterOperationRef.set(page.lastScannedOperationRef());
        }
        var candidates = page.candidates();
        for (var candidate : candidates) {
            boolean completed = false;
            try (Ingress ingress = contentStore.reopen(candidate.intent().operationRef())) {
                Sealed plan = candidate.plan();
                var activeTargets = plan.targets().stream()
                        .filter(target -> target.resultLifecycleState() == Lifecycle.ACTIVE)
                        .toList();
                if (plan.operationKind() != OperationKind.PUT || activeTargets.size() != 1) {
                    throw new IllegalStateException("native Files PUT recovery plan is invalid");
                }
                var target = activeTargets.getFirst();
                Put put = new Put(
                        new FilePath(target.targetPath()),
                        ingress.content(),
                        plan.ifMatchCondition(),
                        plan.ifNoneMatchCondition());
                FilesRequestScope scope = new FilesRequestScope(
                        plan.organizationRef(),
                        plan.spaceRef(),
                        plan.providerBindingRevision());
                String auditRef = NativeFilesOperationAudit.publish(audit, candidate.intent());
                adapter.execute(candidate.intent(), scope, plan, put, auditRef, null);
                ingress.releaseIfTerminal();
                completed = true;
                recovered++;
            } catch (RuntimeException unavailableOrStillUncertain) {
                retained++;
            }
            if (completed) {
                contentStore.remove(candidate.intent().operationRef());
            }
        }
        return new RecoveryResult(candidates.size(), recovered, retained);
    }

    /** Support-safe counters only; no operation ref, path, binding, digest, or provider ref. */
    public record RecoveryResult(int candidateCount, int recoveredCount, int retainedCount) {
    }
}
