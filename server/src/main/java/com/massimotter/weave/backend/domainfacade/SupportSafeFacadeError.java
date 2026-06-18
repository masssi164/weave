package com.massimotter.weave.backend.domainfacade;

/**
 * Support-safe error taxonomy for canonical non-chat facades.
 *
 * These values are public product states. Provider adapters may keep richer diagnostics internally, but member/runtime
 * callers must not receive raw provider errors, secrets, endpoints, or downstream payloads.
 */
public enum SupportSafeFacadeError {
    NOT_CONFIGURED("not_configured"),
    DEGRADED("degraded"),
    POLICY_BLOCKED("policy_blocked"),
    UNAVAILABLE("unavailable"),
    PROVIDER_FAILURE("provider_failure"),
    CONTEXT_FORBIDDEN("context_forbidden"),
    UNSUPPORTED("unsupported");

    private final String value;

    SupportSafeFacadeError(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
