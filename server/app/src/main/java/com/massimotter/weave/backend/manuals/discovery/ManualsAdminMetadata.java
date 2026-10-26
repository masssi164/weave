package com.massimotter.weave.backend.manuals.discovery;

import com.massimotter.weave.backend.admin.domain.AdminCapabilityDeclaration;
import com.massimotter.weave.backend.admin.domain.AdminCapabilityDeclaration.ProfileGrant;
import java.util.List;

/** Existing manuals capability vocabulary for shared Admin simulation and profile presentation. */
public final class ManualsAdminMetadata {
    private ManualsAdminMetadata() {}

    public static AdminCapabilityDeclaration capabilities() {
        return new AdminCapabilityDeclaration("manuals", 80,
                List.of("manuals.read", "manuals.admin"), List.of("manuals.read", "manuals.admin"), List.of("manuals.admin", "manuals.read"), List.of("manuals.read"),
                List.of(new ProfileGrant("workspace-operator", 30, List.of("manuals.admin"))));
    }
}
