package com.massimotter.weave.backend.model.agentruntime;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadOwnership;
import com.massimotter.weave.backend.agentruntime.domain.WeaverWorkloadPrincipal;
import java.time.Instant;
import java.util.List;

/** Support-safe private projection; it never contains a member subject or bearer token. */
public record McpWorkloadContextResponse(
        String schema,
        String authorizationRef,
        String organizationRef,
        String cellRef,
        String workloadClientId,
        String workloadRefHash,
        String runtimeProfileId,
        String runtimeProfileHash,
        String entitlementRevision,
        Instant authorizationExpiresAt,
        List<String> grantedScopes,
        List<String> visibleToolClasses) {

    public static McpWorkloadContextResponse from(WeaverWorkloadPrincipal principal) {
        String workloadRef = RuntimeWorkloadOwnership.fingerprint(
                principal.issuer() + "\u0000" + principal.workloadSubject() + "\u0000"
                        + principal.workloadClientId());
        String authorizationRef = "mcp-authz:" + RuntimeWorkloadOwnership.fingerprint(
                principal.cellRef() + "\u0000" + principal.runtimeProfileHash() + "\u0000"
                        + principal.entitlementRevision() + "\u0000" + principal.authorizationExpiresAt())
                .substring(7);
        return new McpWorkloadContextResponse(
                "weave.mcp-workload-context/v2",
                authorizationRef,
                principal.organizationRef(),
                principal.cellRef(),
                principal.workloadClientId(),
                workloadRef,
                principal.runtimeProfileId(),
                principal.runtimeProfileHash(),
                principal.entitlementRevision(),
                principal.authorizationExpiresAt(),
                principal.scopes().stream().sorted().toList(),
                principal.visibleToolClasses().stream().sorted().toList());
    }
}
