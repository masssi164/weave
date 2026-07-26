package com.massimotter.weave.backend.agentruntime.domain;

import java.util.Locale;

public enum RuntimeCellState {
  ABSENT,
  PROVISIONING,
  STOPPED,
  STARTING,
  MATERIALIZING,
  READY,
  BUSY,
  SYNCING,
  DEGRADED,
  SUSPENDED,
  REVOKING,
  DELETING,
  DELETED;

  public String wireValue() {
    return name().toLowerCase(Locale.ROOT);
  }
}
