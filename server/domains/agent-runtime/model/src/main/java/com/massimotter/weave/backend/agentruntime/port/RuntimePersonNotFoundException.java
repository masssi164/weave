package com.massimotter.weave.backend.agentruntime.port;

public final class RuntimePersonNotFoundException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public RuntimePersonNotFoundException(String message) {
    super(message);
  }
}
