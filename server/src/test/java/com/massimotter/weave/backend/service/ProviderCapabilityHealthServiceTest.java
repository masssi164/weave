package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.config.ProviderHealthProperties;
import com.massimotter.weave.backend.files.port.FilesProviderPort;
import com.massimotter.weave.backend.portability.ProviderCapabilityProbeResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProviderCapabilityHealthServiceTest {

    @Test
    void snapshotsUseOnlyTheCacheAndTheMinimumProbeIntervalIsSixtySeconds() {
        MutableClock clock = clock();
        FilesProviderPort files = configuredFiles();
        when(files.healthProbe()).thenReturn(ProviderCapabilityProbeResult.available("files-storage-ready"));
        ProviderCapabilityHealthService service = service(files, clock, new SimpleMeterRegistry());

        assertThat(service.supportSafeSnapshot().capabilities())
                .filteredOn(capability -> capability.capability().equals("files"))
                .singleElement()
                .satisfies(capability -> {
                    assertThat(capability.state()).isEqualTo("degraded");
                    assertThat(capability.stale()).isTrue();
                    assertThat(capability.supportSafeCode()).isEqualTo("files-health-awaiting-probe");
                });
        verify(files, times(0)).healthProbe();

        service.refreshDueProviders();
        service.supportSafeSnapshot();
        service.supportSafeSnapshot();
        service.refreshDueProviders();

        verify(files, times(1)).healthProbe();
        assertThat(service.cached("files")).hasValueSatisfying(capability -> {
            assertThat(capability.state()).isEqualTo("available");
            assertThat(capability.stale()).isFalse();
        });

        clock.advance(Duration.ofSeconds(59));
        service.refreshDueProviders();
        verify(files, times(1)).healthProbe();

        clock.advance(Duration.ofSeconds(1));
        service.refreshDueProviders();
        verify(files, times(2)).healthProbe();
    }

    @Test
    void concurrentRefreshesCollapseIntoOneSingleFlightProbe() throws Exception {
        MutableClock clock = clock();
        FilesProviderPort files = configuredFiles();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return ProviderCapabilityProbeResult.available("files-storage-ready");
        }).when(files).healthProbe();
        ProviderCapabilityHealthService service = service(files, clock, new SimpleMeterRegistry());

        Thread first = new Thread(service::refreshDueProviders);
        Thread concurrent = new Thread(service::refreshDueProviders);
        first.start();
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        concurrent.start();
        concurrent.join(5_000);
        release.countDown();
        first.join(5_000);

        assertThat(first.isAlive()).isFalse();
        assertThat(concurrent.isAlive()).isFalse();
        verify(files, times(1)).healthProbe();
    }

    @Test
    void retryAfterAndExponentialBackoffSuppressRetriesUntilRecovery() {
        MutableClock clock = clock();
        FilesProviderPort files = configuredFiles();
        when(files.healthProbe()).thenReturn(
                ProviderCapabilityProbeResult.degraded("files-storage-rate-limited", Duration.ofSeconds(180)),
                ProviderCapabilityProbeResult.degraded("files-storage-unavailable"),
                ProviderCapabilityProbeResult.available("files-storage-ready"));
        ProviderCapabilityHealthService service = service(files, clock, new SimpleMeterRegistry());

        service.refreshDueProviders();
        Instant firstBackoff = service.cached("files").orElseThrow().backoffUntil();
        assertThat(firstBackoff).isEqualTo(clock.instant().plusSeconds(180));
        assertThat(service.cached("files").orElseThrow().consecutiveFailures()).isEqualTo(1);

        clock.advance(Duration.ofSeconds(179));
        service.refreshDueProviders();
        verify(files, times(1)).healthProbe();

        clock.advance(Duration.ofSeconds(1));
        service.refreshDueProviders();
        assertThat(service.cached("files").orElseThrow().backoffUntil())
                .isEqualTo(clock.instant().plusSeconds(120));
        assertThat(service.cached("files").orElseThrow().consecutiveFailures()).isEqualTo(2);

        clock.advance(Duration.ofSeconds(120));
        service.refreshDueProviders();
        assertThat(service.cached("files").orElseThrow()).satisfies(capability -> {
            assertThat(capability.state()).isEqualTo("available");
            assertThat(capability.consecutiveFailures()).isZero();
            assertThat(capability.backoffUntil()).isNull();
            assertThat(capability.readinessTransitions()).isEqualTo(1);
        });
        verify(files, times(3)).healthProbe();
    }

    @Test
    void staleResultsDegradeLocallyAndUnsafeProviderTextIsRedacted() {
        MutableClock clock = clock();
        FilesProviderPort files = configuredFiles();
        when(files.healthProbe()).thenReturn(
                ProviderCapabilityProbeResult.available("files-storage-ready"),
                ProviderCapabilityProbeResult.degraded("https://files.example.test user=human secret=token"));
        ProviderCapabilityHealthService service = service(files, clock, new SimpleMeterRegistry());

        service.refreshDueProviders();
        clock.advance(Duration.ofMinutes(6));
        assertThat(service.cached("files").orElseThrow()).satisfies(capability -> {
            assertThat(capability.state()).isEqualTo("degraded");
            assertThat(capability.stale()).isTrue();
            assertThat(capability.supportSafeCode()).isEqualTo("files-health-cache-stale");
            assertThat(capability.correlationRef()).startsWith("provider-health:files:");
        });

        service.refreshDueProviders();
        assertThat(service.cached("files").orElseThrow()).satisfies(capability -> {
            assertThat(capability.supportSafeCode()).isEqualTo("provider-capability-degraded");
            assertThat(capability.toString())
                    .doesNotContain("files.example.test")
                    .doesNotContain("human")
                    .doesNotContain("token");
        });
    }

    @Test
    void publishesLatencyStatusFailureBackoffAgeAndTransitionMetrics() {
        MutableClock clock = clock();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        FilesProviderPort files = configuredFiles();
        when(files.healthProbe()).thenReturn(ProviderCapabilityProbeResult.degraded("files-storage-unavailable"));
        ProviderCapabilityHealthService service = service(files, clock, registry);

        service.refreshDueProviders();

        assertThat(registry.find("weave.provider.health.probe.latency")
                .tag("capability", "files")
                .tag("status", "degraded")
                .timer()).isNotNull();
        assertThat(registry.find("weave.provider.health.status").tag("capability", "files").gauge().value())
                .isEqualTo(1);
        assertThat(registry.find("weave.provider.health.consecutive.failures").tag("capability", "files").gauge().value())
                .isEqualTo(1);
        assertThat(registry.find("weave.provider.health.backoff.until.epoch.seconds").tag("capability", "files").gauge().value())
                .isGreaterThan(0);
        assertThat(registry.find("weave.provider.health.cached.age.seconds").tag("capability", "files").gauge().value())
                .isZero();
        assertThat(registry.find("weave.provider.health.readiness.transitions").tag("capability", "files").gauge().value())
                .isZero();
    }

    private ProviderCapabilityHealthService service(
            FilesProviderPort files,
            MutableClock clock,
            SimpleMeterRegistry registry) {
        return new ProviderCapabilityHealthService(
                files,
                null,
                new ProviderHealthProperties(
                        Duration.ofSeconds(60),
                        Duration.ZERO,
                        Duration.ofMinutes(5),
                        Duration.ofMinutes(15)),
                registry,
                clock,
                () -> 0);
    }

    private FilesProviderPort configuredFiles() {
        FilesProviderPort files = mock(FilesProviderPort.class);
        when(files.configured()).thenReturn(true);
        return files;
    }

    private MutableClock clock() {
        return new MutableClock(Instant.parse("2026-07-12T08:00:00Z"));
    }

    private static final class MutableClock extends Clock {

        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
