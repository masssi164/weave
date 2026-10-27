package com.massimotter.weave.backend.files.domain;

import com.massimotter.weave.backend.providerbinding.port.ProviderBindingTransitionPolicy;

/** Preserves canonical identity until provider reconciliation is explicitly supported. */
public final class FilesProviderBindingPolicy implements ProviderBindingTransitionPolicy {
    @Override
    public String domain() { return "files"; }

    @Override
    public void validateReplacement(boolean hasMappings) {
        if (hasMappings) throw new BindingTransitionBlockedException();
    }

    public static final class BindingTransitionBlockedException extends RuntimeException {
        public BindingTransitionBlockedException() { super("Files binding transition requires verified mapping and resource identity carry-forward"); }
    }
}
