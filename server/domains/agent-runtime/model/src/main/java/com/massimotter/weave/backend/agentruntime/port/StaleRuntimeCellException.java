package com.massimotter.weave.backend.agentruntime.port;

public final class StaleRuntimeCellException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public StaleRuntimeCellException(String message) {
    super(message);
  }
}
