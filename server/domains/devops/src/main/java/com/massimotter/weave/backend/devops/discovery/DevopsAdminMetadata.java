package com.massimotter.weave.backend.devops.discovery;

import com.massimotter.weave.backend.admin.domain.AdminCapabilityDeclaration;
import com.massimotter.weave.backend.admin.domain.AdminCapabilityDeclaration.ProfileGrant;
import java.util.List;

/** Existing devops capability vocabulary for shared Admin simulation and profile presentation. */
public final class DevopsAdminMetadata {
    private DevopsAdminMetadata() {}

    public static AdminCapabilityDeclaration capabilities() {
        return new AdminCapabilityDeclaration("devops", 90,
                List.of("release_evidence.read", "release_evidence.manage"), List.of("release_evidence.read", "release_evidence.manage"), List.of("release_evidence.read"), List.of("release_evidence.read"),
                List.of(new ProfileGrant("workspace-operator", 20, List.of("release_evidence.read"))));
    }
}
