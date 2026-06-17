package com.massimotter.weave.mcp;

import com.massimotter.weave.contract.mcp.MemberMcpToolDefinition;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class WeaveServerClient {
    static final String SERVER_KEY = "weave-domain-tools";
    private final RestClient restClient;
    public WeaveServerClient(@Value("${weave.server.base-url:http://localhost:8080}") String baseUrl) { this.restClient = RestClient.builder().baseUrl(baseUrl).build(); }

    public List<Map<String, Object>> discover(String runtimeProfileHash, RuntimeHeaders headers) {
        HttpHeaders httpHeaders = new HttpHeaders();
        headers.copyTo(httpHeaders);
        return restClient.get()
                .uri(uri -> uri.path("/api/workspace/weaver/mcp/servers/{serverKey}/tools").queryParam("runtimeProfileHash", runtimeProfileHash).build(SERVER_KEY))
                .headers(h -> h.addAll(httpHeaders))
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
    }

    public Map<String, Object> invoke(MemberMcpToolDefinition tool, Map<String, Object> input, RuntimeHeaders headers) {
        HttpHeaders httpHeaders = new HttpHeaders();
        headers.copyTo(httpHeaders);
        return restClient.post()
                .uri("/api/workspace/weaver/mcp/servers/{serverKey}/tools/{toolName}:invoke", SERVER_KEY, tool.name())
                .headers(h -> h.addAll(httpHeaders))
                .body(Map.of("runtimeProfileHash", headers.runtimeProfile(), "input", input))
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
    }
}
