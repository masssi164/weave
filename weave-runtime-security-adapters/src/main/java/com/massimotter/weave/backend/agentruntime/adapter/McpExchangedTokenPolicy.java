package com.massimotter.weave.backend.agentruntime.adapter;

import com.massimotter.weave.backend.agentruntime.domain.ExchangedWorkloadToken;
import com.massimotter.weave.backend.agentruntime.port.McpWorkloadAuthorizationException;
import java.net.URI;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.oauth2.jwt.Jwt;

/** Exact claim-shape policy for the API token emitted by Standard Token Exchange V2. */
public final class McpExchangedTokenPolicy {
    private static final Set<String> FORBIDDEN_SCOPES = Set.of(
            "openid",
            "offline_access",
            "weave:workspace",
            "mcp.tools",
            "agent-runtime.profile.read",
            "agent-runtime.admin");

    private final String apiResource;
    private final String edgeClientId;

    public McpExchangedTokenPolicy(String apiResource, String edgeClientId) {
        URI resource = URI.create(apiResource);
        if (!"https".equalsIgnoreCase(resource.getScheme()) || resource.getHost() == null
                || resource.getUserInfo() != null || resource.getQuery() != null || resource.getFragment() != null) {
            throw new IllegalArgumentException("The API resource must be an absolute HTTPS URI");
        }
        if (!"weave-mcp-server".equals(edgeClientId)) {
            throw new IllegalArgumentException("The MCP edge client must be weave-mcp-server");
        }
        this.apiResource = resource.toString();
        this.edgeClientId = edgeClientId;
    }

    public ExchangedWorkloadToken resolve(Jwt jwt) {
        if (jwt == null || jwt.getIssuer() == null || blank(jwt.getSubject())
                || jwt.getIssuedAt() == null || jwt.getExpiresAt() == null || blank(jwt.getId())) {
            throw denied(McpWorkloadAuthorizationException.Reason.TOKEN_REQUIRED_CLAIMS);
        }
        if (!"at+jwt".equalsIgnoreCase(String.valueOf(jwt.getHeaders().getOrDefault("typ", "")))) {
            throw denied(McpWorkloadAuthorizationException.Reason.TOKEN_TYPE);
        }
        if (!edgeClientId.equals(jwt.getClaimAsString("azp"))) {
            throw denied(McpWorkloadAuthorizationException.Reason.TOKEN_REQUESTER);
        }
        String clientId = jwt.getClaimAsString("client_id");
        if (clientId != null && !edgeClientId.equals(clientId)) {
            throw denied(McpWorkloadAuthorizationException.Reason.TOKEN_REQUESTER);
        }
        if (!new HashSet<>(jwt.getAudience()).equals(Set.of(apiResource)) || jwt.getAudience().size() != 1) {
            throw denied(McpWorkloadAuthorizationException.Reason.TOKEN_AUDIENCE);
        }
        Set<String> scopes = exactScopes(jwt.getClaimAsString("scope"));
        requireNoRoles(
                jwt.getClaimAsMap("realm_access"),
                McpWorkloadAuthorizationException.Reason.TOKEN_REALM_ROLES);
        requireNoRoles(
                jwt.getClaimAsMap("resource_access"),
                McpWorkloadAuthorizationException.Reason.TOKEN_CLIENT_ROLES);
        return new ExchangedWorkloadToken(
                jwt.getIssuer().toString(),
                jwt.getSubject(),
                edgeClientId,
                scopes,
                jwt.getIssuedAt(),
                jwt.getExpiresAt(),
                jwt.getId());
    }

    private static Set<String> exactScopes(String scopeClaim) {
        if (scopeClaim == null || scopeClaim.isBlank()) {
            throw denied(McpWorkloadAuthorizationException.Reason.TOKEN_SCOPE);
        }
        String[] values = scopeClaim.trim().split("\\s+");
        LinkedHashSet<String> scopes = new LinkedHashSet<>(List.of(values));
        if (scopes.size() != values.length || scopes.isEmpty() || scopes.size() > 16
                || scopes.stream().anyMatch(scope -> scope.isBlank() || FORBIDDEN_SCOPES.contains(scope))) {
            throw denied(McpWorkloadAuthorizationException.Reason.TOKEN_SCOPE);
        }
        return Set.copyOf(scopes);
    }

    private static void requireNoRoles(
            Map<String, Object> access,
            McpWorkloadAuthorizationException.Reason reason) {
        if (access != null && !access.isEmpty()) {
            throw denied(reason);
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static McpWorkloadAuthorizationException denied(
            McpWorkloadAuthorizationException.Reason reason) {
        return new McpWorkloadAuthorizationException(false, reason);
    }

}
