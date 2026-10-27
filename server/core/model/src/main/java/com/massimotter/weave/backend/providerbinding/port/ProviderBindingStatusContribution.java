package com.massimotter.weave.backend.providerbinding.port;

import com.massimotter.weave.backend.providerbinding.domain.ProviderBindingStatus;

/** One installed domain's authorized admin readback of its current provider binding. */
public interface ProviderBindingStatusContribution {
    String domain();
    ProviderBindingStatus status(String organization);
}
