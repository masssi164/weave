package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.readiness.domain.LocalDependencyObservation;
import com.massimotter.weave.backend.readiness.port.LocalDependencyContribution;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalDependencyContributionContractTest {
    @Test
    void checksAreDeferredAndOrderedByTheirStableOwningIdentity() {
        var calls = new AtomicInteger();
        var service = new LocalDependencyReadinessService(Optional.empty(),
                List.of(contribution("second", calls), contribution("first", calls)));
        assertThat(calls.get()).isZero();
        assertThat(service.checks()).extracting(check -> check.key()).containsExactly("first", "second");
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void duplicateOwnersCannotSilentlyReplaceEachOtherOrTheSharedPersistenceCheck() {
        var calls = new AtomicInteger();
        assertThatThrownBy(() -> new LocalDependencyReadinessService(Optional.empty(),
                List.of(contribution("same", calls), contribution("same", calls))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Duplicate");
        assertThatThrownBy(() -> new LocalDependencyReadinessService(Optional.empty(),
                List.of(contribution("persistence", calls))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Duplicate");
        assertThat(calls.get()).isZero();
    }

    private LocalDependencyContribution contribution(String id, AtomicInteger calls) {
        return new LocalDependencyContribution() {
            public String id() { return id; }
            public LocalDependencyObservation check() {
                calls.incrementAndGet();
                return new LocalDependencyObservation(id, "Local capability", "up", "ready", "Available", null);
            }
        };
    }
}
