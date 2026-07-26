package com.massimotter.weave.backend.agentruntime.port;

public final class InvalidRuntimeWorkloadTokenException extends RuntimeException {
  private static final long serialVersionUID = 1L;
  private final String code;

  public InvalidRuntimeWorkloadTokenException(String code) {
    super("Agent Runtime workload token rejected: " + code);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
