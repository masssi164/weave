package com.massimotter.weave.backend.calendar.domain;

import com.massimotter.weave.backend.providerbinding.port.ProviderBindingTransitionPolicy;

/** Preserves canonical identity until provider reconciliation is explicitly supported. */
public final class CalendarProviderBindingPolicy implements ProviderBindingTransitionPolicy {
    @Override
    public String domain() { return "calendar"; }

    @Override
    public void validateReplacement(boolean hasMappings) {
        if (hasMappings) throw new BindingTransitionBlockedException();
    }

    public static final class BindingTransitionBlockedException extends RuntimeException {
        public BindingTransitionBlockedException() { super("Calendar binding transition requires verified Event identity carry-forward"); }
    }
}
