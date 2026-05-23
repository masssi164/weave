package com.massimotter.weave.backend.model.office;

import java.time.Instant;
import java.util.List;

public record OfficeLaunchResponse(
        String sessionId,
        String launchMode,
        String providerKey,
        Instant expiresAt,
        List<String> grantedPermissions) {

    public OfficeLaunchResponse {
        grantedPermissions = grantedPermissions == null ? List.of() : List.copyOf(grantedPermissions);
    }
}
