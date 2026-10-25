package com.massimotter.weave.backend.agentruntime.domain;

import com.massimotter.weave.backend.security.workload.domain.WorkloadReferences;

/** Stable one-way ownership markers shared by the control store and identity adapter. */
public final class RuntimeWorkloadOwnership {
    private RuntimeWorkloadOwnership() {}

    public static String ownerFingerprint(
            String organizationRef,
            String personRef,
            String cellRef,
            String clientId) {
        return WorkloadReferences.fingerprint(organizationRef + "\u0000" + personRef + "\u0000" + cellRef + "\u0000" + clientId);
    }

}
