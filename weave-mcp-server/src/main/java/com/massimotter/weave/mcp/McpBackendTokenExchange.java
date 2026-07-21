package com.massimotter.weave.mcp;

import java.util.Set;

interface McpBackendTokenExchange {
    ExchangedAccessToken exchange(McpCellWorkloadPrincipal workload, String subjectToken, Set<String> scopes);
}
