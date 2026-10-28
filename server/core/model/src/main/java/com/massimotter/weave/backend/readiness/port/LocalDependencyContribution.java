package com.massimotter.weave.backend.readiness.port;

import com.massimotter.weave.backend.readiness.domain.LocalDependencyObservation;

/** A domain-owned local readiness check, evaluated on demand rather than during wiring. */
public interface LocalDependencyContribution {
    String id();
    LocalDependencyObservation check();
}
