package com.massimotter.weave.mcp;

import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.BridgeDiscoveryResponse;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.BridgeInvocationRequest;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.BridgeInvocationResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class WeaveServerClient {
    static final String SERVER_KEY = "weave-domain-tools";
    private final RestClient restClient;
    public WeaveServerClient(@Value("${weave.server.base-url:http://localhost:8080}") String baseUrl) { this(RestClient.builder(), baseUrl); }
    WeaveServerClient(RestClient.Builder builder, String baseUrl) { this.restClient = builder.baseUrl(baseUrl).build(); }

    public BridgeDiscoveryResponse discover(String runtimeProfileHash, RuntimeHeaders headers) {
        HttpHeaders httpHeaders = new HttpHeaders();
        headers.copyTo(httpHeaders);
        return restClient.get()
                .uri(uri -> uri.path("/api/workspace/weaver/mcp/servers/{serverKey}/tools").queryParam("runtimeProfileHash", runtimeProfileHash).build(SERVER_KEY))
                .headers(h -> h.addAll(httpHeaders))
                .retrieve()
                .body(BridgeDiscoveryResponse.class);
    }

    public BridgeInvocationResponse invoke(BridgeInvocationRequest request, RuntimeHeaders headers) {
        HttpHeaders httpHeaders = new HttpHeaders();
        headers.copyTo(httpHeaders);
        return restClient.post()
                .uri("/api/workspace/weaver/mcp/servers/{serverKey}/tools/{toolName}:invoke", SERVER_KEY, request.toolName())
                .headers(h -> h.addAll(httpHeaders))
                .body(request)
                .retrieve()
                .body(BridgeInvocationResponse.class);
    }
}
