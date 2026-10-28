package com.massimotter.weave.backend.decisions.discovery;

import com.massimotter.weave.backend.admin.domain.AdminCapabilityDeclaration;
import com.massimotter.weave.backend.admin.domain.AdminCapabilityDeclaration.ProfileGrant;
import java.util.List;

/** Existing decisions capability vocabulary for shared Admin simulation and profile presentation. */
public final class DecisionsAdminMetadata {
    private DecisionsAdminMetadata() {}

    public static AdminCapabilityDeclaration capabilities() {
        return new AdminCapabilityDeclaration("decisions", 70,
                List.of("decisions.read", "decisions.record"), List.of("decisions.read", "decisions.record"), List.of(), List.of("decisions.read"),
                List.of());
    }
}
