package com.massimotter.weave.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.BridgeInvocationRequest;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.RuntimeInvocationContext;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.WeaveMcpRef;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class WeaveServerClientTest {

    @Test
    void forwardsAuthorizationAndRuntimeHeadersWithoutQueryTokens() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WeaveServerClient client = new WeaveServerClient(builder, "http://localhost:8080");
        RuntimeHeaders headers = new RuntimeHeaders("Bearer runtime-token", "org:workspace", "user:member", "sha256:test", "approval://granted");

        server.expect(requestTo(org.hamcrest.Matchers.containsString("/api/workspace/weaver/mcp/servers/weave-domain-tools/tools?runtimeProfileHash=sha256:test")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer runtime-token"))
                .andExpect(header("X-Weave-Org-Id", "org:workspace"))
                .andExpect(header("X-Weave-User-Ref", "user:member"))
                .andExpect(header("X-Weave-Runtime-Profile", "sha256:test"))
                .andExpect(header("X-Weave-Approval-Receipt", "approval://granted"))
                .andExpect(queryParam("runtimeProfileHash", "sha256:test"))
                .andRespond(withSuccess("{" +
                        "\"runtime\":{\"orgRef\":{\"value\":\"org:workspace\"},\"userRef\":{\"value\":\"user:member\"},\"runtimeProfileRef\":{\"value\":\"weave-runtime-profile://sha256:test\"},\"runtimeProfileHash\":\"sha256:test\",\"runtimeTokenRef\":{\"value\":\"credentialref://weave/runtime/short-lived/test\"},\"auditRef\":\"audit://bridge/discovery\",\"approvalReceiptRef\":null,\"alwaysAllowGrantRef\":null,\"capabilityGrants\":[],\"allowedTools\":[]}," +
                        "\"catalog\":{\"serverNamespace\":\"weave-domain-tools\",\"contractVersion\":\"weave-mcp-bridge-v1\",\"tools\":[]}}", MediaType.APPLICATION_JSON));

        BridgeInvocationRequest request = new BridgeInvocationRequest(
                "files.read",
                java.util.Map.of("spaceRef", "space:control-room"),
                new RuntimeInvocationContext(
                        new WeaveMcpRef("org:workspace"),
                        new WeaveMcpRef("user:member"),
                        new WeaveMcpRef("weave-runtime-profile://sha256:test"),
                        "sha256:test",
                        new WeaveMcpRef("credentialref://weave/runtime/short-lived/test"),
                        "audit://bridge/invoke",
                        null,
                        null,
                        java.util.List.of(),
                        java.util.List.of()));

        server.expect(requestTo(org.hamcrest.Matchers.containsString("/api/workspace/weaver/mcp/servers/weave-domain-tools/tools/files.read:invoke")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer runtime-token"))
                .andExpect(header("X-Weave-Runtime-Profile", "sha256:test"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("runtimeToken="))))
                .andRespond(withSuccess("{" +
                        "\"toolName\":\"files.read\",\"status\":\"SUCCESS\",\"auditRef\":\"audit://weaver-tool/files.read/invoked\",\"supportSafe\":true,\"content\":[],\"structuredContent\":{}}", MediaType.APPLICATION_JSON));

        var discovery = client.discover("sha256:test", headers);
        assertThat(discovery.catalog().serverNamespace()).isEqualTo("weave-domain-tools");

        var invocation = client.invoke(request, headers);
        assertThat(invocation.toolName()).isEqualTo("files.read");

        server.verify();
    }
}
