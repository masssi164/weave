package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.calendar.port.CalendarProviderPort;
import com.massimotter.weave.backend.chat.port.ChatSouthboundProvider;
import com.massimotter.weave.backend.config.ProviderHealthProperties;
import com.massimotter.weave.backend.files.port.FilesProviderPort;
import com.massimotter.weave.backend.model.admin.ProviderCapabilityHealthResponse;
import com.massimotter.weave.backend.portability.ProviderCapabilityProbeResult;
import com.massimotter.weave.backend.portability.ProviderCapabilityState;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ProviderCapabilityHealthService {

    static final String SCHEMA_VERSION = "provider-capability-health-v1";

    private final ProviderHealthProperties properties;
    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private final LongSupplier jitterSource;
    private final Map<String, ProbeTarget> targets;
    private final AtomicBoolean probeFlight = new AtomicBoolean();

    @Autowired
    public ProviderCapabilityHealthService(
            ObjectProvider<FilesProviderPort> filesProvider,
            ObjectProvider<CalendarProviderPort> calendarProvider,
            ObjectProvider<ChatSouthboundProvider> chatProvider,
            ProviderHealthProperties properties,
            MeterRegistry meterRegistry) {
        this(
                filesProvider.getIfUnique(),
                calendarProvider.getIfUnique(),
                chatProvider.getIfUnique(),
                properties,
                meterRegistry,
                Clock.systemUTC(),
                () -> ThreadLocalRandom.current().nextLong());
    }

    ProviderCapabilityHealthService(
            FilesProviderPort filesProvider,
            CalendarProviderPort calendarProvider,
            ChatSouthboundProvider chatProvider,
            ProviderHealthProperties properties,
            MeterRegistry meterRegistry,
            Clock clock,
            LongSupplier jitterSource) {
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
        this.jitterSource = jitterSource;
        Instant now = clock.instant();
        Map<String, ProbeTarget> configuredTargets = new LinkedHashMap<>();
        configuredTargets.put("files", target(
                "files",
                filesProvider == null ? null : filesProvider::healthProbe,
                safelyConfigured(filesProvider),
                now));
        configuredTargets.put("calendar", target(
                "calendar",
                calendarProvider == null ? null : calendarProvider::healthProbe,
                safelyConfigured(calendarProvider),
                now));
        configuredTargets.put("chat", target(
                "chat",
                chatProvider == null ? null : chatProvider::healthProbe,
                safelyConfigured(chatProvider),
                now));
        this.targets = Collections.unmodifiableMap(configuredTargets);
        this.targets.values().forEach(this::registerGauges);
    }

    @Scheduled(initialDelay = 1_000, fixedDelay = 5_000)
    public void refreshDueProviders() {
        Instant now = clock.instant();
        if (targets.values().stream().noneMatch(target -> target.due(now))) {
            return;
        }
        if (!probeFlight.compareAndSet(false, true)) {
            return;
        }
        try {
            for (ProbeTarget target : targets.values()) {
                Instant probeTime = clock.instant();
                if (target.due(probeTime)) {
                    probe(target, probeTime);
                }
            }
        } finally {
            probeFlight.set(false);
        }
    }

    public ProviderCapabilityHealthResponse supportSafeSnapshot() {
        Instant now = clock.instant();
        List<ProviderCapabilityHealthResponse.CapabilityHealth> capabilities = targets.values().stream()
                .map(target -> view(target.current.get(), now))
                .toList();
        return new ProviderCapabilityHealthResponse(SCHEMA_VERSION, now, true, capabilities);
    }

    public Optional<ProviderCapabilityHealthResponse.CapabilityHealth> cached(String capability) {
        ProbeTarget target = targets.get(capability);
        return target == null
                ? Optional.empty()
                : Optional.of(view(target.current.get(), clock.instant()));
    }

    private ProbeTarget target(
            String capability,
            Supplier<ProviderCapabilityProbeResult> probe,
            boolean configured,
            Instant now) {
        ProviderCapabilityState state = configured
                ? ProviderCapabilityState.DEGRADED
                : ProviderCapabilityState.UNAVAILABLE;
        String code = configured
                ? capability + "-health-awaiting-probe"
                : capability + "-storage-not-configured";
        Instant observedAt = configured ? null : now;
        Instant nextProbeAt = configured ? now : now.plus(properties.minimumInterval());
        CachedObservation initial = new CachedObservation(
                capability,
                state,
                code,
                correlationRef(capability),
                observedAt,
                nextProbeAt,
                null,
                0,
                0,
                0);
        Supplier<ProviderCapabilityProbeResult> safeProbe = probe == null
                ? () -> ProviderCapabilityProbeResult.unavailable(capability + "-adapter-unavailable")
                : probe;
        return new ProbeTarget(capability, safeProbe, new AtomicReference<>(initial));
    }

    private void probe(ProbeTarget target, Instant startedAt) {
        CachedObservation previous = target.current.get();
        long startedNanos = System.nanoTime();
        ProviderCapabilityProbeResult result;
        try {
            result = target.probe.get();
            if (result == null) {
                result = ProviderCapabilityProbeResult.degraded(target.capability + "-health-probe-failed");
            }
        } catch (RuntimeException exception) {
            result = ProviderCapabilityProbeResult.degraded(target.capability + "-health-probe-failed");
        }
        long latencyNanos = Math.max(0, System.nanoTime() - startedNanos);
        boolean available = result.state() == ProviderCapabilityState.AVAILABLE;
        int failures = available ? 0 : Math.min(previous.consecutiveFailures + 1, 30);
        Duration delay = available
                ? properties.minimumInterval()
                : maximum(exponentialBackoff(failures), result.retryAfter());
        Instant backoffUntil = available ? null : safePlus(startedAt, delay);
        Instant nextProbeAt = safePlus(safePlus(startedAt, delay), jitter());
        long transitions = previous.observedAt != null && previous.state != result.state()
                ? previous.readinessTransitions + 1
                : previous.readinessTransitions;
        CachedObservation next = new CachedObservation(
                target.capability,
                result.state(),
                result.supportSafeCode(),
                correlationRef(target.capability),
                startedAt,
                nextProbeAt,
                backoffUntil,
                failures,
                TimeUnit.NANOSECONDS.toMillis(latencyNanos),
                transitions);
        target.current.set(next);
        recordMetrics(target.capability, previous, next, latencyNanos);
    }

    private Duration exponentialBackoff(int consecutiveFailures) {
        int exponent = Math.max(0, Math.min(consecutiveFailures - 1, 20));
        Duration calculated;
        try {
            calculated = properties.minimumInterval().multipliedBy(1L << exponent);
        } catch (ArithmeticException exception) {
            return properties.maximumBackoff();
        }
        return calculated.compareTo(properties.maximumBackoff()) > 0
                ? properties.maximumBackoff()
                : calculated;
    }

    private Duration jitter() {
        long maximumMillis = properties.jitter().toMillis();
        if (maximumMillis <= 0) {
            return Duration.ZERO;
        }
        long selected = Math.floorMod(jitterSource.getAsLong(), maximumMillis + 1);
        return Duration.ofMillis(selected);
    }

    private Duration maximum(Duration first, Duration second) {
        if (second == null || second.compareTo(first) <= 0) {
            return first;
        }
        return second;
    }

    private Instant safePlus(Instant instant, Duration duration) {
        try {
            return instant.plus(duration);
        } catch (RuntimeException exception) {
            return instant.plus(properties.maximumBackoff());
        }
    }

    private ProviderCapabilityHealthResponse.CapabilityHealth view(CachedObservation observation, Instant now) {
        Long ageSeconds = observation.observedAt == null
                ? null
                : Math.max(0, Duration.between(observation.observedAt, now).toSeconds());
        boolean stale = isStale(observation, now);
        ProviderCapabilityState effectiveState = effectiveState(observation, stale);
        String effectiveCode = stale && observation.observedAt != null
                ? observation.capability + "-health-cache-stale"
                : observation.supportSafeCode;
        return new ProviderCapabilityHealthResponse.CapabilityHealth(
                observation.capability,
                effectiveState.value(),
                effectiveCode,
                observation.correlationRef,
                observation.observedAt,
                observation.nextProbeAt,
                observation.backoffUntil,
                ageSeconds,
                stale,
                observation.consecutiveFailures,
                observation.probeLatencyMillis,
                observation.readinessTransitions);
    }

    private void registerGauges(ProbeTarget target) {
        for (ProviderCapabilityState state : ProviderCapabilityState.values()) {
            Timer.builder("weave.provider.health.probe.latency")
                    .tag("capability", target.capability)
                    .tag("status", state.value())
                    .register(meterRegistry);
        }
        Gauge.builder("weave.provider.health.status", target,
                        value -> stateValue(effectiveState(value.current.get(), isStale(value.current.get(), clock.instant()))))
                .description("Provider capability state: available=2, degraded=1, unavailable=0")
                .tag("capability", target.capability)
                .register(meterRegistry);
        Gauge.builder("weave.provider.health.consecutive.failures", target,
                        value -> value.current.get().consecutiveFailures)
                .tag("capability", target.capability)
                .register(meterRegistry);
        Gauge.builder("weave.provider.health.backoff.until.epoch.seconds", target,
                        value -> epochSeconds(value.current.get().backoffUntil))
                .tag("capability", target.capability)
                .register(meterRegistry);
        Gauge.builder("weave.provider.health.cached.age.seconds", target,
                        value -> cachedAgeSeconds(value.current.get()))
                .tag("capability", target.capability)
                .register(meterRegistry);
        Gauge.builder("weave.provider.health.readiness.transitions", target,
                        value -> value.current.get().readinessTransitions)
                .tag("capability", target.capability)
                .register(meterRegistry);
    }

    private void recordMetrics(
            String capability,
            CachedObservation previous,
            CachedObservation next,
            long latencyNanos) {
        Timer.builder("weave.provider.health.probe.latency")
                .tag("capability", capability)
                .tag("status", next.state.value())
                .register(meterRegistry)
                .record(latencyNanos, TimeUnit.NANOSECONDS);
        if (previous.observedAt != null && previous.state != next.state) {
            Counter.builder("weave.provider.health.readiness.transition")
                    .tag("capability", capability)
                    .tag("from", previous.state.value())
                    .tag("to", next.state.value())
                    .register(meterRegistry)
                    .increment();
        }
    }

    private double cachedAgeSeconds(CachedObservation observation) {
        return observation.observedAt == null
                ? -1
                : Math.max(0, Duration.between(observation.observedAt, clock.instant()).toSeconds());
    }

    private double epochSeconds(Instant value) {
        return value == null ? 0 : value.getEpochSecond();
    }

    private double stateValue(ProviderCapabilityState state) {
        return switch (state) {
            case AVAILABLE -> 2;
            case DEGRADED -> 1;
            case UNAVAILABLE -> 0;
        };
    }

    private ProviderCapabilityState effectiveState(CachedObservation observation, boolean stale) {
        return stale && observation.state == ProviderCapabilityState.AVAILABLE
                ? ProviderCapabilityState.DEGRADED
                : observation.state;
    }

    private boolean isStale(CachedObservation observation, Instant now) {
        return observation.observedAt == null
                || Duration.between(observation.observedAt, now).compareTo(properties.staleAfter()) > 0;
    }

    private String correlationRef(String capability) {
        return "provider-health:" + capability + ":" + UUID.randomUUID();
    }

    private boolean safelyConfigured(FilesProviderPort provider) {
        try {
            return provider != null && provider.configured();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private boolean safelyConfigured(CalendarProviderPort provider) {
        try {
            return provider != null && provider.configured();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private boolean safelyConfigured(ChatSouthboundProvider provider) {
        try {
            return provider != null && provider.configured();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private record ProbeTarget(
            String capability,
            Supplier<ProviderCapabilityProbeResult> probe,
            AtomicReference<CachedObservation> current) {

        boolean due(Instant now) {
            return !now.isBefore(current.get().nextProbeAt);
        }
    }

    private record CachedObservation(
            String capability,
            ProviderCapabilityState state,
            String supportSafeCode,
            String correlationRef,
            Instant observedAt,
            Instant nextProbeAt,
            Instant backoffUntil,
            int consecutiveFailures,
            long probeLatencyMillis,
            long readinessTransitions) {
    }
}
