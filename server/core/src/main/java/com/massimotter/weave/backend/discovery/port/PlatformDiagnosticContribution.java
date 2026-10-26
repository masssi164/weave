package com.massimotter.weave.backend.discovery.port;

import com.massimotter.weave.backend.discovery.api.PlatformStatusResponse;

/** One installed module's public, support-safe platform observation. */
public interface PlatformDiagnosticContribution {
    String id();

    int order();

    String label();

    PlatformStatusResponse.DiagnosticStatus status(PlatformStatusResponse.DiagnosticStatus auth);
}
