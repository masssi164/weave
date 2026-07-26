package com.massimotter.weave.backend.agentruntime.domain;

public record RuntimeProvisioningPlan(
        String workspaceRevision,
        String workspaceManifestRef,
        String runtimeStateStoreRef,
        RuntimeWorkloadBinding.AuthenticationMethod authenticationMethod) {
    public RuntimeProvisioningPlan {
        RuntimeMemberBinding.requireText(workspaceRevision, "workspaceRevision");
        RuntimeMemberBinding.requireText(workspaceManifestRef, "workspaceManifestRef");
        if (runtimeStateStoreRef == null || !runtimeStateStoreRef.startsWith("runtime-state://")) {
            throw new IllegalArgumentException("runtimeStateStoreRef must use runtime-state://");
        }
        if (authenticationMethod == null) {
            throw new IllegalArgumentException("workload authentication method is required");
        }
    }
}
