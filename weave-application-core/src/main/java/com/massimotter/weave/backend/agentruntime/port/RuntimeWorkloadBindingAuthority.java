package com.massimotter.weave.backend.agentruntime.port;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadBinding;
import java.util.Objects;

/** Read-only authority for a current workload-to-cell binding. */
public interface RuntimeWorkloadBindingAuthority {
  void requireCurrentBinding(CurrentBindingCommand command);

  record CurrentBindingCommand(
      String organizationRef,
      String personRef,
      String cellRef,
      RuntimeWorkloadBinding binding,
      String auditRef) {
    public CurrentBindingCommand {
      requireText(organizationRef, "organizationRef");
      requireText(personRef, "personRef");
      requireText(cellRef, "cellRef");
      Objects.requireNonNull(binding, "binding");
      requireText(auditRef, "auditRef");
    }

    private static void requireText(String value, String field) {
      if (value == null || value.isBlank()) {
        throw new IllegalArgumentException(field + " is required");
      }
    }
  }
}
