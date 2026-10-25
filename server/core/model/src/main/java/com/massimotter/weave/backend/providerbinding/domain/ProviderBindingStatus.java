package com.massimotter.weave.backend.providerbinding.domain;

/** Support-safe view of current binding authority; excludes private configuration and object IDs. */
public record ProviderBindingStatus(BindingState bindingState, Long bindingRevision,
        String adapterKey, Readiness readiness) {
    public enum BindingState { ACTIVE, NO_ACTIVE_BINDING }
    public enum Readiness { CONFIGURED, NOT_CONFIGURED, UNAVAILABLE }
}
