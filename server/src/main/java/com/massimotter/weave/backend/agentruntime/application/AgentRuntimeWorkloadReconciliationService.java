package com.massimotter.weave.backend.agentruntime.application;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeCell;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeEntitlementState;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadOwnership;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadReconciliationReport;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadReconciliationReport.Blocker;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadReconciliationReport.Counts;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadReconciliationReport.State;
import com.massimotter.weave.backend.agentruntime.port.RuntimeCellRepository;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadCredentialStore;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityAdmin;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityInventory;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityInventory.ClientObservation;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityInventory.ManagementState;
import com.massimotter.weave.backend.config.ProviderHealthProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Single-flight reconciliation of the authoritative ARC binding store, SecretRefs, and Keycloak namespace.
 * Public snapshots never contain organization, person, cell, provider, subject, or credential identifiers.
 */
public final class AgentRuntimeWorkloadReconciliationService {
    private static final String SCHEDULED_AUDIT_REF = "audit:arc-workload-reconciliation";

    private final RuntimeCellRepository cells;
    private final RuntimeWorkloadIdentityAdmin identityAdmin;
    private final RuntimeWorkloadIdentityInventory identityInventory;
    private final RuntimeWorkloadReconciliationEvaluator evaluator;
    private final ProviderHealthProperties timing;
    private final MeterRegistry meters;
    private final Clock clock;
    private final LongSupplier jitterSource;
    private final AtomicBoolean reconciliationFlight = new AtomicBoolean();
    private final AtomicInteger consecutiveUnavailable = new AtomicInteger();
    private final AtomicReference<RuntimeWorkloadReconciliationReport> current;

    public AgentRuntimeWorkloadReconciliationService(
            RuntimeCellRepository cells,
            RuntimeWorkloadIdentityAdmin identityAdmin,
            RuntimeWorkloadIdentityInventory identityInventory,
            RuntimeWorkloadCredentialStore credentials,
            ProviderHealthProperties timing,
            MeterRegistry meters) {
        this(
                cells,
                identityAdmin,
                identityInventory,
                credentials,
                timing,
                meters,
                Clock.systemUTC(),
                () -> ThreadLocalRandom.current().nextLong());
    }

    AgentRuntimeWorkloadReconciliationService(
            RuntimeCellRepository cells,
            RuntimeWorkloadIdentityAdmin identityAdmin,
            RuntimeWorkloadIdentityInventory identityInventory,
            RuntimeWorkloadCredentialStore credentials,
            ProviderHealthProperties timing,
            MeterRegistry meters,
            Clock clock,
            LongSupplier jitterSource) {
        this.cells = Objects.requireNonNull(cells, "cells");
        this.identityAdmin = Objects.requireNonNull(identityAdmin, "identityAdmin");
        this.identityInventory = Objects.requireNonNull(identityInventory, "identityInventory");
        this.evaluator = new RuntimeWorkloadReconciliationEvaluator(
                Objects.requireNonNull(credentials, "credentials"));
        this.timing = Objects.requireNonNull(timing, "timing");
        this.meters = Objects.requireNonNull(meters, "meters");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.jitterSource = Objects.requireNonNull(jitterSource, "jitterSource");

        Instant now = clock.instant();
        String unobserved = RuntimeWorkloadOwnership.fingerprint("agent-runtime-workload-unobserved");
        this.current = new AtomicReference<>(new RuntimeWorkloadReconciliationReport(
                RuntimeWorkloadReconciliationReport.SCHEMA_VERSION,
                RuntimeWorkloadReconciliationReport.GUARDED_CLAIM,
                State.BLOCKED,
                now,
                now.plusSeconds(1),
                unobserved,
                unobserved,
                RuntimeWorkloadOwnership.fingerprint("agent-runtime-workload-awaiting-observation"),
                Counts.empty(),
                null,
                Set.of(Blocker.AWAITING_OBSERVATION)));
        registerMetrics();
    }

    @Scheduled(initialDelay = 1_000, fixedDelay = 5_000)
    public void reconcileWhenDue() {
        if (!clock.instant().isBefore(current.get().nextReconcileAt())) {
            reconcileNow(SCHEDULED_AUDIT_REF);
        }
    }

    public RuntimeWorkloadReconciliationReport reconcileNow(String auditRef) {
        requireAuditRef(auditRef);
        if (!reconciliationFlight.compareAndSet(false, true)) {
            return supportSafeSnapshot();
        }
        long startedNanos = System.nanoTime();
        Counter.builder("weave.agent.runtime.workload.reconcile.attempts").register(meters).increment();
        try {
            RuntimeWorkloadReconciliationReport report = reconcile(auditRef, clock.instant());
            current.set(report);
            consecutiveUnavailable.set(0);
            recordResult(report.state(), startedNanos);
            return report;
        } catch (RuntimeException unavailable) {
            int failures = Math.min(30, consecutiveUnavailable.incrementAndGet());
            RuntimeWorkloadReconciliationReport previous = current.get();
            Instant now = clock.instant();
            Duration delay = maximumBackoff(failures);
            Counts prior = previous.counts();
            Counts failedCounts = new Counts(
                    prior.authoritativeActiveCells(),
                    prior.authoritativeInactiveCells(),
                    prior.providerReservedClients(),
                    prior.activeConverged(),
                    prior.inactiveConverged(),
                    prior.missingClients(),
                    prior.disabledActiveClients(),
                    prior.enabledInactiveClients(),
                    prior.orphanedClients(),
                    prior.duplicateClientBindings(),
                    prior.crossBoundClients(),
                    prior.unownedClients(),
                    prior.malformedClients(),
                    prior.missingCredentials(),
                    prior.invalidCredentialStates(),
                    prior.credentialRotationOverlaps(),
                    prior.reconcileFailures() + 1);
            RuntimeWorkloadReconciliationReport report = new RuntimeWorkloadReconciliationReport(
                    RuntimeWorkloadReconciliationReport.SCHEMA_VERSION,
                    RuntimeWorkloadReconciliationReport.GUARDED_CLAIM,
                    State.UNAVAILABLE,
                    now,
                    safePlus(now, delay.plus(jitter())),
                    previous.authoritativeRevision(),
                    previous.providerRevision(),
                    correlation(auditRef, now, previous.authoritativeRevision(), previous.providerRevision()),
                    failedCounts,
                    previous.oldestActiveCredentialAgeSeconds(),
                    Set.of(Blocker.PROVIDER_UNAVAILABLE, Blocker.RECONCILE_FAILURE));
            current.set(report);
            recordResult(State.UNAVAILABLE, startedNanos);
            return report;
        } finally {
            reconciliationFlight.set(false);
        }
    }

    /** Returns only cached evidence and never performs an authenticated provider request. */
    public RuntimeWorkloadReconciliationReport supportSafeSnapshot() {
        RuntimeWorkloadReconciliationReport report = current.get();
        Instant now = clock.instant();
        Duration age = Duration.between(report.observedAt(), now);
        if (!age.isNegative() && age.compareTo(timing.staleAfter()) <= 0) {
            return report;
        }
        EnumSet<Blocker> blockers = report.blockers().isEmpty()
                ? EnumSet.noneOf(Blocker.class)
                : EnumSet.copyOf(report.blockers());
        blockers.add(Blocker.STALE_OBSERVATION);
        return new RuntimeWorkloadReconciliationReport(
                report.schemaVersion(),
                report.capabilityClaim(),
                report.state() == State.UNAVAILABLE ? State.UNAVAILABLE : State.BLOCKED,
                report.observedAt(),
                report.nextReconcileAt(),
                report.authoritativeRevision(),
                report.providerRevision(),
                report.correlationRef(),
                report.counts(),
                report.oldestActiveCredentialAgeSeconds(),
                blockers);
    }

    private RuntimeWorkloadReconciliationReport reconcile(String auditRef, Instant now) {
        List<RuntimeCell> authoritative = cells.findAll().stream()
                .sorted(Comparator.comparing(RuntimeCell::cellRef))
                .toList();
        String authoritativeRevision = evaluator.authoritativeRevision(authoritative);
        RuntimeWorkloadIdentityInventory.Snapshot before = identityInventory.scan();
        Map<String, RuntimeCell> expected = evaluator.expectedByClient(authoritative);
        Map<String, List<ClientObservation>> observed = evaluator.group(before.clients());
        int reconcileFailures = quarantineUnsafe(expected, observed, auditRef);

        for (RuntimeCell cell : authoritative) {
            List<ClientObservation> candidates = observed.getOrDefault(
                    cell.workloadBinding().clientId(), List.of());
            try {
                if (cell.entitlementState() == RuntimeEntitlementState.ENTITLED) {
                    if (candidates.size() == 1 && evaluator.ownershipMatches(candidates.getFirst(), cell)) {
                        identityAdmin.reconcileBinding(new RuntimeWorkloadIdentityAdmin.ReconcileBindingCommand(
                                cell.organizationRef(), cell.personRef(), cell.cellRef(),
                                cell.workloadBinding(), auditRef));
                    }
                } else if (candidates.isEmpty()
                        || (candidates.size() == 1 && evaluator.ownershipMatches(candidates.getFirst(), cell))) {
                    identityAdmin.disableBinding(new RuntimeWorkloadIdentityAdmin.DisableBindingCommand(
                            cell.organizationRef(), cell.personRef(), cell.cellRef(),
                            cell.workloadBinding(), auditRef));
                }
            } catch (RuntimeException failedBinding) {
                reconcileFailures++;
            }
        }

        RuntimeWorkloadIdentityInventory.Snapshot after = identityInventory.scan();
        RuntimeWorkloadReconciliationEvaluator.Evaluation evaluation =
                evaluator.evaluate(authoritative, after, reconcileFailures, now);
        Duration nextDelay = timing.minimumInterval().plus(jitter());
        State state = evaluation.blockers().isEmpty() ? State.CONVERGED : State.BLOCKED;
        return new RuntimeWorkloadReconciliationReport(
                RuntimeWorkloadReconciliationReport.SCHEMA_VERSION,
                RuntimeWorkloadReconciliationReport.GUARDED_CLAIM,
                state,
                now,
                safePlus(now, nextDelay),
                authoritativeRevision,
                after.revision(),
                correlation(auditRef, now, authoritativeRevision, after.revision()),
                evaluation.counts(),
                evaluation.oldestCredentialAgeSeconds(),
                evaluation.blockers());
    }

    private int quarantineUnsafe(
            Map<String, RuntimeCell> expected,
            Map<String, List<ClientObservation>> observed,
            String auditRef) {
        int failures = 0;
        for (Map.Entry<String, List<ClientObservation>> group : observed.entrySet()) {
            RuntimeCell cell = expected.get(group.getKey());
            boolean ambiguous = group.getValue().size() != 1;
            for (ClientObservation observation : group.getValue()) {
                boolean unsafe = cell == null || ambiguous || !evaluator.ownershipMatches(observation, cell);
                if (unsafe && observation.enabled() && observation.managementState() == ManagementState.MANAGED) {
                    try {
                        identityInventory.quarantineManaged(
                                new RuntimeWorkloadIdentityInventory.QuarantineManagedCommand(
                                        observation.providerRef(), observation.clientId(),
                                        observation.ownerFingerprint(), auditRef));
                    } catch (RuntimeException failedQuarantine) {
                        failures++;
                    }
                }
            }
        }
        return failures;
    }

    private String correlation(String auditRef, Instant observedAt, String authority, String provider) {
        return RuntimeWorkloadOwnership.fingerprint(
                auditRef + "\u0000" + observedAt + "\u0000" + authority + "\u0000" + provider);
    }

    private Duration maximumBackoff(int consecutiveFailures) {
        int exponent = Math.max(0, Math.min(consecutiveFailures - 1, 20));
        try {
            Duration calculated = timing.minimumInterval().multipliedBy(1L << exponent);
            return calculated.compareTo(timing.maximumBackoff()) > 0
                    ? timing.maximumBackoff()
                    : calculated;
        } catch (ArithmeticException overflow) {
            return timing.maximumBackoff();
        }
    }

    private Duration jitter() {
        long maximumMillis = timing.jitter().toMillis();
        if (maximumMillis <= 0) {
            return Duration.ZERO;
        }
        return Duration.ofMillis(Math.floorMod(jitterSource.getAsLong(), maximumMillis + 1));
    }

    private Instant safePlus(Instant instant, Duration duration) {
        try {
            return instant.plus(duration);
        } catch (RuntimeException overflow) {
            return instant.plus(timing.maximumBackoff());
        }
    }

    private void registerMetrics() {
        gauge("active-converged", report -> report.counts().activeConverged());
        gauge("inactive-converged", report -> report.counts().inactiveConverged());
        gauge("orphaned", report -> report.counts().orphanedClients());
        gauge("cross-bound", report -> report.counts().crossBoundClients());
        gauge("reconcile-failures", report -> report.counts().reconcileFailures());
        Gauge.builder("weave.agent.runtime.workload.credential.rotation.age.seconds", current,
                        value -> value.get().oldestActiveCredentialAgeSeconds() == null
                                ? -1
                                : value.get().oldestActiveCredentialAgeSeconds())
                .register(meters);
    }

    private void gauge(
            String state,
            java.util.function.ToDoubleFunction<RuntimeWorkloadReconciliationReport> value) {
        Gauge.builder("weave.agent.runtime.workload.clients", current, reference -> value.applyAsDouble(reference.get()))
                .tag("state", state)
                .register(meters);
    }

    private void recordResult(State state, long startedNanos) {
        long duration = Math.max(0, System.nanoTime() - startedNanos);
        String result = state.name().toLowerCase(java.util.Locale.ROOT);
        Counter.builder("weave.agent.runtime.workload.reconcile.results")
                .tag("result", result)
                .register(meters)
                .increment();
        Timer.builder("weave.agent.runtime.workload.reconcile.duration")
                .tag("result", result)
                .register(meters)
                .record(duration, TimeUnit.NANOSECONDS);
    }

    private static void requireAuditRef(String auditRef) {
        if (auditRef == null || auditRef.isBlank() || auditRef.length() > 255) {
            throw new IllegalArgumentException("A bounded audit reference is required");
        }
    }

}
