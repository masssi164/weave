package com.massimotter.weave.backend.agentruntime.readiness;

import com.massimotter.weave.backend.agentruntime.port.RuntimeStateStore;
import com.massimotter.weave.backend.readiness.domain.LocalDependencyObservation;
import com.massimotter.weave.backend.readiness.port.LocalDependencyContribution;
import java.util.Objects;

/** Owns the external encrypted-state readiness projection, evaluated only on request. */
public final class RuntimeStateReadiness implements LocalDependencyContribution {
    private final RuntimeStateStore store;

    public RuntimeStateReadiness(RuntimeStateStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public String id() {
        return "agent-runtime-state";
    }

    @Override
    public LocalDependencyObservation check() {
        if (store.readiness().ready()) {
            return new LocalDependencyObservation(id(), "Agent runtime state", "up", "ready",
                    "Encrypted external runtime state is available in the guarded self-hosted profile.", null);
        }
        return new LocalDependencyObservation(id(), "Agent runtime state", "blocked", "blocked",
                "Encrypted external runtime state is unavailable.",
                "Restore and reconcile the runtime-state database and wrapping-key SecretRef set.");
    }
}
