package com.massimotter.weave.backend.agentruntime.port;

public final class RuntimeCommandConflictException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public RuntimeCommandConflictException(String message) {
    super(message);
  }
}
