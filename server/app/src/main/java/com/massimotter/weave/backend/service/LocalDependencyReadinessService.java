package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.discovery.api.PlatformStatusResponse;
import com.massimotter.weave.backend.readiness.persistence.JpaPersistenceReadinessProbe;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import com.massimotter.weave.backend.readiness.port.LocalDependencyContribution;
import java.util.Comparator;
import java.util.HashSet;
import org.springframework.stereotype.Service;

@Service
public class LocalDependencyReadinessService {

    private final JpaPersistenceReadinessProbe persistenceReadinessProbe;
    private final List<LocalDependencyContribution> contributions;

    public LocalDependencyReadinessService(
            Optional<JpaPersistenceReadinessProbe> persistenceReadinessProbe,
            List<LocalDependencyContribution> contributions) {
        this.persistenceReadinessProbe = persistenceReadinessProbe.orElse(null);
        var ids = new HashSet<String>();
        for (var contribution : contributions) {
            if ("persistence".equals(contribution.id()) || !ids.add(contribution.id())) {
                throw new IllegalArgumentException("Duplicate local dependency readiness owner: " + contribution.id());
            }
        }
        this.contributions = contributions.stream().sorted(Comparator.comparing(LocalDependencyContribution::id)).toList();
    }

    public List<PlatformStatusResponse.DiagnosticCheck> checks() {
        List<PlatformStatusResponse.DiagnosticCheck> checks = new ArrayList<>();
        if (persistenceReadinessProbe != null) {
            checks.add(persistenceCheck());
        }
        for (var contribution : contributions) {
            var observation = contribution.check();
            if (!contribution.id().equals(observation.id())) {
                throw new IllegalStateException("Local dependency readiness owner returned a different identity");
            }
            checks.add(new PlatformStatusResponse.DiagnosticCheck(
                    observation.id(), observation.label(), observation.status(), observation.readiness(),
                    observation.summary(), observation.remediation()));
        }
        return List.copyOf(checks);
    }

    private PlatformStatusResponse.DiagnosticCheck persistenceCheck() {
        try {
            if (persistenceReadinessProbe.isReady()) {
                return new PlatformStatusResponse.DiagnosticCheck(
                        "persistence",
                        "Persistence",
                        "up",
                        "ready",
                        "Configured persistence is reachable.",
                        null);
            }
        } catch (RuntimeException ignored) {
            // Readiness responses must never disclose persistence URLs, credentials, or provider details.
        }
        return new PlatformStatusResponse.DiagnosticCheck(
                "persistence",
                "Persistence",
                "blocked",
                "blocked",
                "Configured persistence is unavailable.",
                "Restore the configured local persistence dependency and retry readiness.");
    }
}
