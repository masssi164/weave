package com.massimotter.weave.backend.admin.domain;

import com.massimotter.weave.backend.admin.domain.AdminCapabilityDeclaration.ProfileGrant;
import java.util.List;

/** Shared administration and current external-client entitlement remain platform policy. */
public final class CoreAdminMetadata {
    private CoreAdminMetadata() {}

    public static AdminCapabilityDeclaration capabilities() {
        return new AdminCapabilityDeclaration("platform", 100,
                List.of("admin_control_plane.readiness_read", "admin.policy.edit", "admin.provider.configure",
                        "operator.support_bundle.create", "agent-runtime.entitled"),
                List.of("admin_control_plane.readiness_read", "admin.policy.edit", "admin.provider.configure"),
                List.of("admin_control_plane.readiness_read", "operator.support_bundle.create"), List.of(),
                List.of(new ProfileGrant("workspace-admin", 100, List.of("admin.provider.configure", "admin.policy.edit")),
                        new ProfileGrant("workspace-operator", 10,
                                List.of("admin_control_plane.readiness_read", "operator.support_bundle.create")),
                        new ProfileGrant("group:/capabilities/weaver", 10, List.of("agent-runtime.entitled"))));
    }
}
