package com.massimotter.weave.backend.agentruntime.domain;

import com.massimotter.weave.backend.security.workload.domain.MemberIdentityBinding;

public record RuntimeProvisioningPlan(
        String workspaceRevision,
        String workspaceManifestRef,
        String runtimeStateStoreRef,
        RuntimeWorkloadBinding.AuthenticationMethod authenticationMethod) {
    public RuntimeProvisioningPlan {
        MemberIdentityBinding.requireText(workspaceRevision, "workspaceRevision");
        MemberIdentityBinding.requireText(workspaceManifestRef, "workspaceManifestRef");
        if (runtimeStateStoreRef == null || !runtimeStateStoreRef.startsWith("runtime-state://")) {
            throw new IllegalArgumentException("runtimeStateStoreRef must use runtime-state://");
        }
        if (authenticationMethod == null) {
            throw new IllegalArgumentException("workload authentication method is required");
        }
    }
}
