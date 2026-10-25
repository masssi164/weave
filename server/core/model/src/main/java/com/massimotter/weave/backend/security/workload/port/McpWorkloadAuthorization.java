package com.massimotter.weave.backend.security.workload.port;

import com.massimotter.weave.backend.security.workload.domain.ExchangedWorkloadToken;
import com.massimotter.weave.backend.security.workload.domain.WeaverWorkloadPrincipal;

/** Resolves current workload-to-member authorization from authoritative state on every call. */
public interface McpWorkloadAuthorization {
    WeaverWorkloadPrincipal authorize(ExchangedWorkloadToken token);
}
