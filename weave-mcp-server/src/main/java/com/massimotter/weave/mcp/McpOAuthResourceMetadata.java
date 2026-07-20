package com.massimotter.weave.mcp;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** RFC 9728 metadata for the exact public Streamable HTTP MCP resource. */
@Component
final class McpOAuthResourceMetadata {

    static final String METADATA_PATH = "/.well-known/oauth-protected-resource/mcp";

    private final URI resource;
    private final URI authorizationServer;
    private final URI metadataUri;

    McpOAuthResourceMetadata(
            @Value("${weave.oidc.resource:https://api.weave.test/mcp}") String resource,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String authorizationServer) {
        this.resource = exactHttpsUri(resource, "MCP resource");
        this.authorizationServer = exactHttpsUri(authorizationServer, "OIDC issuer");
        if (!"/mcp".equals(this.resource.getPath())) {
            throw new IllegalStateException("MCP resource path must be exactly /mcp");
        }
        this.metadataUri = URI.create(this.resource.getScheme() + "://" + this.resource.getRawAuthority() + METADATA_PATH);
    }

    URI metadataUri() {
        return metadataUri;
    }

    URI resource() {
        return resource;
    }

    URI authorizationServer() {
        return authorizationServer;
    }

    private static URI exactHttpsUri(String value, String label) {
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(label + " must be a valid URI", exception);
        }
        if (!"https".equals(uri.getScheme())
                || uri.getRawAuthority() == null
                || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null) {
            throw new IllegalStateException(label + " must be an exact HTTPS URI without credentials, query, or fragment");
        }
        return uri;
    }

}
