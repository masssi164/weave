package com.massimotter.weave.backend.discovery.port;

import com.massimotter.weave.backend.discovery.api.PlatformStatusResponse;

/** Installed Matrix facade discovery, including its support-safe security boundary. */
public interface MatrixPlatformContribution {
    String advertisedBaseUrl();

    PlatformStatusResponse.MatrixStatus status(PlatformStatusResponse.DiagnosticStatus auth);
}
