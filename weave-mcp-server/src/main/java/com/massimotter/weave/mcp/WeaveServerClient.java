package com.massimotter.weave.mcp;

import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.BridgeDiscoveryResponse;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.BridgeInvocationRequest;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.BridgeInvocationResponse;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.ApprovalEvidence;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class WeaveServerClient {
    static final String SERVER_KEY = "weave-domain-tools";
    private final RestClient restClient;
    private final BackendAccessTokenProvider accessTokenProvider;

    @Autowired
    public WeaveServerClient(
            @Value("${weave.server.base-url:http://localhost:8080}") String baseUrl,
            BackendAccessTokenProvider accessTokenProvider) {
        this(RestClient.builder(), baseUrl, accessTokenProvider);
    }

    WeaveServerClient(RestClient.Builder builder, String baseUrl, BackendAccessTokenProvider accessTokenProvider) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.accessTokenProvider = accessTokenProvider;
    }

    public BridgeDiscoveryResponse discover(String runtimeProfileHash, RuntimeHeaders headers) {
        return discover(runtimeProfileHash, headers, accessTokenProvider.exchangeCurrentMemberToken());
    }

    private BridgeDiscoveryResponse discover(String runtimeProfileHash, RuntimeHeaders headers, String accessToken) {
        HttpHeaders httpHeaders = new HttpHeaders();
        headers.copyTo(httpHeaders);
        httpHeaders.setBearerAuth(accessToken);
        return restClient.get()
                .uri(uri -> uri.path("/api/workspace/weaver/mcp/servers/{serverKey}/tools").queryParam("runtimeProfileHash", runtimeProfileHash).build(SERVER_KEY))
                .headers(h -> h.addAll(httpHeaders))
                .retrieve()
                .body(BridgeDiscoveryResponse.class);
    }

    public BridgeInvocationResponse invoke(BridgeInvocationRequest request, RuntimeHeaders headers) {
        return invoke(request, headers, accessTokenProvider.exchangeCurrentMemberToken());
    }

    private BridgeInvocationResponse invoke(BridgeInvocationRequest request, RuntimeHeaders headers, String accessToken) {
        HttpHeaders httpHeaders = new HttpHeaders();
        headers.copyTo(httpHeaders);
        httpHeaders.setBearerAuth(accessToken);
        return restClient.post()
                .uri("/api/workspace/weaver/mcp/servers/{serverKey}/tools/{toolName}:invoke", SERVER_KEY, request.toolName())
                .headers(h -> h.addAll(httpHeaders))
                .body(request)
                .retrieve()
                .body(BridgeInvocationResponse.class);
    }

    public BridgeInvocationResponse invoke(
            String toolName,
            Map<String, Object> arguments,
            RuntimeHeaders headers,
            ApprovalEvidence approvalEvidence) {
        if (!headers.valid()) {
            throw new McpBoundaryException("mcp-runtime-context-missing");
        }
        String accessToken = accessTokenProvider.exchangeCurrentMemberToken();
        BridgeDiscoveryResponse discovery = discover(headers.runtimeProfile(), headers, accessToken);
        boolean allowed = discovery.catalog().tools().stream().anyMatch(tool -> tool.name().equals(toolName));
        if (!allowed) {
            throw new McpBoundaryException("mcp-tool-not-granted");
        }
        return invoke(new BridgeInvocationRequest(
                toolName,
                arguments == null ? Map.of() : arguments,
                discovery.runtime(),
                approvalEvidence), headers, accessToken);
    }
}
