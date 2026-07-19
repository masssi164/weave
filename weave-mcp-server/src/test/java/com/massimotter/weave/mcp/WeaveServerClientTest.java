package com.massimotter.weave.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.massimotter.weave.contract.mcp.MemberMcpDomainDefinition;
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
    void forwardsOnlyExchangedAuthorizationAndRuntimeProfileWithoutBoundaryOrIdentityHeaders() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WeaveServerClient client = new WeaveServerClient(builder, "http://localhost:8080", () -> "delegated-backend-token");
        RuntimeHeaders headers = new RuntimeHeaders("sha256:test");

        server.expect(requestTo(org.hamcrest.Matchers.containsString("/api/workspace/weaver/mcp/servers/weave-domain-tools/tools?runtimeProfileHash=sha256:test")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer delegated-backend-token"))
                .andExpect(headerDoesNotExist("X-Weave-Org-Id"))
                .andExpect(headerDoesNotExist("X-Weave-User-Ref"))
                .andExpect(header("X-Weave-Runtime-Profile", "sha256:test"))
                .andExpect(headerDoesNotExist("X-Weave-Approval-Receipt"))
                .andExpect(queryParam("runtimeProfileHash", "sha256:test"))
                .andRespond(withSuccess("{" +
                        "\"runtime\":{\"orgRef\":{\"value\":\"org:workspace\"},\"userRef\":{\"value\":\"user:member\"},\"runtimeProfileRef\":{\"value\":\"weave-runtime-profile://sha256:test\"},\"runtimeProfileHash\":\"sha256:test\",\"runtimeTokenRef\":{\"value\":\"credentialref://weave/runtime/short-lived/test\"},\"auditRef\":\"audit://bridge/discovery\",\"capabilityGrants\":[],\"allowedTools\":[]}," +
                        "\"catalog\":{\"serverNamespace\":\"weave-domain-tools\",\"contractVersion\":\"" + MemberMcpDomainDefinition.CONTRACT_VERSION + "\",\"tools\":[]}}", MediaType.APPLICATION_JSON));

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
                        java.util.List.of(),
                        java.util.List.of()));

        server.expect(requestTo(org.hamcrest.Matchers.containsString("/api/workspace/weaver/mcp/servers/weave-domain-tools/tools/files.read:invoke")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer delegated-backend-token"))
                .andExpect(header("X-Weave-Runtime-Profile", "sha256:test"))
                .andExpect(headerDoesNotExist("X-Weave-Mcp-Boundary-Token"))
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
