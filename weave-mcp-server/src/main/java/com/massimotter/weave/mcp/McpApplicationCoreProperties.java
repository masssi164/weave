package com.massimotter.weave.mcp;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "weave.mcp.application-core")
record McpApplicationCoreProperties(
        Path profileTrustManifest) {

    McpApplicationCoreProperties {
        if (profileTrustManifest == null || !profileTrustManifest.isAbsolute()) {
            throw new IllegalArgumentException("MCP public profile trust manifest path must be absolute");
        }
    }
}
