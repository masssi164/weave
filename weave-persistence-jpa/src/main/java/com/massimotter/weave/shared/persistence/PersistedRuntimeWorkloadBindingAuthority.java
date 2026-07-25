package com.massimotter.weave.shared.persistence;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeCell;
import com.massimotter.weave.backend.agentruntime.port.RuntimeCellRepository;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadBindingAuthority;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityException;
import java.util.Objects;

/**
 * MCP-side read-only binding authority over state persisted by weave-server.
 * It never creates, enables, repairs, or rotates Keycloak clients.
 */
public final class PersistedRuntimeWorkloadBindingAuthority implements RuntimeWorkloadBindingAuthority {
    private final RuntimeCellRepository cells;

    public PersistedRuntimeWorkloadBindingAuthority(RuntimeCellRepository cells) {
        this.cells = Objects.requireNonNull(cells, "cells");
    }

    @Override
    public void requireCurrentBinding(CurrentBindingCommand command) {
        RuntimeCell current = cells.findByWorkload(
                        command.binding().issuer(),
                        command.binding().subject())
                .orElseThrow(PersistedRuntimeWorkloadBindingAuthority::denied);
        if (!current.organizationRef().equals(command.organizationRef())
                || !current.personRef().equals(command.personRef())
                || !current.cellRef().equals(command.cellRef())
                || !current.workloadBinding().equals(command.binding())) {
            throw denied();
        }
    }

    private static RuntimeWorkloadIdentityException denied() {
        return new RuntimeWorkloadIdentityException("The persisted workload binding is not current");
    }
}
