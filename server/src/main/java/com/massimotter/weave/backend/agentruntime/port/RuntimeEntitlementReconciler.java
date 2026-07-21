package com.massimotter.weave.backend.agentruntime.port;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeCell;

/** Refreshes or fences one authoritative runtime cell against current IDM entitlement. */
public interface RuntimeEntitlementReconciler {
    RuntimeCell reconcileEntitlement(RuntimeCell expectedCell, String auditRef);
}
