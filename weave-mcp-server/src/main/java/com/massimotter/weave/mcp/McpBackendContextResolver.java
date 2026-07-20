package com.massimotter.weave.mcp;

interface McpBackendContextResolver {
    McpBackendContext resolve(McpCellWorkloadPrincipal workload, ExchangedAccessToken token);
}
