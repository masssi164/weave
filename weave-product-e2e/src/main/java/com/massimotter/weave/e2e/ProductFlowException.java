package com.massimotter.weave.e2e;

/** Sanitized failure: messages contain operation names/statuses, never credentials or action URLs. */
final class ProductFlowException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  ProductFlowException(String message) {
    super(message);
  }

  ProductFlowException(String message, Throwable cause) {
    super(message, cause);
  }
}
