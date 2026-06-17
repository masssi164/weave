package com.massimotter.weave.mcp;

import org.springframework.http.HttpHeaders;

public record RuntimeHeaders(String authorization, String orgId, String userRef, String runtimeProfile, String approvalReceiptRef) {
    boolean valid() { return authorization != null && authorization.startsWith("Bearer ") && runtimeProfile != null && !runtimeProfile.isBlank(); }
    void copyTo(HttpHeaders headers) {
        if (authorization != null && !authorization.isBlank()) headers.set(HttpHeaders.AUTHORIZATION, authorization);
        if (orgId != null && !orgId.isBlank()) headers.set("X-Weave-Org-Id", orgId);
        if (userRef != null && !userRef.isBlank()) headers.set("X-Weave-User-Ref", userRef);
        if (runtimeProfile != null && !runtimeProfile.isBlank()) headers.set("X-Weave-Runtime-Profile", runtimeProfile);
        if (approvalReceiptRef != null && !approvalReceiptRef.isBlank()) headers.set("X-Weave-Approval-Receipt", approvalReceiptRef);
    }
}
