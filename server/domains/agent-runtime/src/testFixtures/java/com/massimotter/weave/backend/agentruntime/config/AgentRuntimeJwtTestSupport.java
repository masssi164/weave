package com.massimotter.weave.backend.agentruntime.config;

import com.massimotter.weave.backend.security.config.WeaveSecurityProperties;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/** Installs the actual future-admin decoder in signed-token contract harnesses. */
public final class AgentRuntimeJwtTestSupport {
    private AgentRuntimeJwtTestSupport() {}

    public static JwtDecoder adminDecoder(
            OAuth2ResourceServerProperties resourceServerProperties,
            WeaveSecurityProperties securityProperties) {
        return new AgentRuntimeJwtConfiguration().agentRuntimeAdminJwtDecoder(
                resourceServerProperties, securityProperties);
    }
}
