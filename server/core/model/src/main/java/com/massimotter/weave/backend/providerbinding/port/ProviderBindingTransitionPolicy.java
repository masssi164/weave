package com.massimotter.weave.backend.providerbinding.port;

/** A domain's integrity precondition for replacing an active provider binding. */
public interface ProviderBindingTransitionPolicy {
    String domain();

    void validateReplacement(boolean hasMappings);
}
