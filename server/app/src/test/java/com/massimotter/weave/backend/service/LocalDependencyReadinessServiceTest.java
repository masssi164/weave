package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.readiness.persistence.JpaPersistenceReadinessProbe;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalDependencyReadinessServiceTest {

    @Test
    void omitsPersistenceCheckWhenJpaIsNotConfigured() {
        LocalDependencyReadinessService service = new LocalDependencyReadinessService(
                Optional.empty(), java.util.List.of());

        assertThat(service.checks()).isEmpty();
    }

    @Test
    void reportsReadyWhenJpaProbeSucceeds() {
        JpaPersistenceReadinessProbe probe = mock(JpaPersistenceReadinessProbe.class);
        when(probe.isReady()).thenReturn(true);

        LocalDependencyReadinessService service = new LocalDependencyReadinessService(
                Optional.of(probe), java.util.List.of());

        assertThat(service.checks()).singleElement().satisfies(check -> {
            assertThat(check.key()).isEqualTo("persistence");
            assertThat(check.status()).isEqualTo("up");
            assertThat(check.readiness()).isEqualTo("ready");
            assertThat(check.action()).isNull();
        });
    }

    @Test
    void redactsJpaFailureDetails() {
        JpaPersistenceReadinessProbe probe = mock(JpaPersistenceReadinessProbe.class);
        when(probe.isReady())
                .thenThrow(new IllegalStateException(
                        "jdbc:postgresql://db.internal/weave?password=do-not-expose"));
        LocalDependencyReadinessService service = new LocalDependencyReadinessService(
                Optional.of(probe), java.util.List.of());

        assertThat(service.checks()).singleElement().satisfies(check -> {
            assertThat(check.key()).isEqualTo("persistence");
            assertThat(check.status()).isEqualTo("blocked");
            assertThat(check.readiness()).isEqualTo("blocked");
            assertThat(check.message()).isEqualTo("Configured persistence is unavailable.");
            assertThat(check.action())
                    .isEqualTo("Restore the configured local persistence dependency and retry readiness.");
            assertThat(check.toString())
                    .doesNotContain("db.internal", "password", "do-not-expose", "postgresql");
        });
    }

}
