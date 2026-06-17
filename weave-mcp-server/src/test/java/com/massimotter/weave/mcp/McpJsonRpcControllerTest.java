package com.massimotter.weave.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.BridgeDiscoveryResponse;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.BridgeInvocationRequest;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.BridgeInvocationResponse;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.RuntimeInvocationContext;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.ToolInvocationStatus;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.WeaveMcpContentBlock;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.WeaveMcpRef;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.WeaveMcpToolCatalog;
import com.massimotter.weave.contract.mcp.MemberMcpToolCatalog;
import com.massimotter.weave.contract.mcp.MemberMcpDomainDefinition;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(McpJsonRpcController.class)
class McpJsonRpcControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean WeaveServerClient client;

    @Test
    void getMcpReturnsClean405() throws Exception {
        mvc.perform(get("/mcp"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "POST"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Use JSON-RPC POST /mcp.")));
    }

    @Test
    void toolsListFailsClosedWithoutRuntimeAuthContext() throws Exception {
        mvc.perform(post("/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("missing runtime authorization context")));
    }

    @Test
    void toolsListProjectsTypedBackendCatalogWithoutMetadataDrift() throws Exception {
        when(client.discover(eq("sha256:test"), any(RuntimeHeaders.class))).thenReturn(new BridgeDiscoveryResponse(runtime(), new WeaveMcpToolCatalog(
                "weave-domain-tools",
                MemberMcpDomainDefinition.CONTRACT_VERSION,
                MemberMcpToolCatalog.tools().stream().map(tool -> tool.asBridgeDefinition()).toList())));

        var response = mvc.perform(post("/mcp")
                        .header("Authorization", "Bearer runtime-token")
                        .header("X-Weave-Runtime-Profile", "sha256:test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode tools = objectMapper.readTree(response).at("/result/tools");
        assertEquals(MemberMcpToolCatalog.tools().size(), tools.size());
        for (var contractTool : MemberMcpToolCatalog.tools()) {
            JsonNode projected = null;
            for (JsonNode node : tools) if (contractTool.name().equals(node.path("name").asText())) projected = node;
            assertNotNull(projected, "missing MCP tool projection for " + contractTool.name());
            assertEquals(contractTool.description(), projected.path("description").asText(), contractTool.name());
            assertEquals(objectMapper.valueToTree(contractTool.inputSchema()), projected.path("inputSchema"), contractTool.name());
            assertEquals(contractTool.asBridgeDefinition().annotations().readOnlyHint(), projected.at("/annotations/readOnlyHint").asBoolean(), contractTool.name());
            assertEquals(contractTool.asBridgeDefinition().annotations().destructiveHint(), projected.at("/annotations/destructiveHint").asBoolean(), contractTool.name());
            assertEquals(contractTool.asBridgeDefinition().annotations().openWorldHint(), projected.at("/annotations/openWorldHint").asBoolean(), contractTool.name());
        }
        verify(client).discover(eq("sha256:test"), any(RuntimeHeaders.class));
    }

    @Test
    void toolsCallReturnsMcpStructuredSuccessPayload() throws Exception {
        when(client.invoke(any(BridgeInvocationRequest.class), any(RuntimeHeaders.class))).thenReturn(new BridgeInvocationResponse(
                "files.read",
                ToolInvocationStatus.SUCCESS,
                "audit://weaver-tool/files.read/invoked",
                true,
                List.of(new WeaveMcpContentBlock("text", "read ok", null, Map.of("status", "ok"))),
                Map.of("canonicalRefs", Map.of("space", "space:control-room"), "rawProviderPayload", "redacted")));

        var response = mvc.perform(post("/mcp")
                        .header("Authorization", "Bearer runtime-token")
                        .header("X-Weave-Runtime-Profile", "sha256:test")
                        .header("X-Weave-Org-Id", "org:workspace")
                        .header("X-Weave-User-Ref", "user:member")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"files.read\",\"arguments\":{\"spaceRef\":\"space:control-room\"}}}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode result = objectMapper.readTree(response).at("/result");
        assertEquals(false, result.path("isError").asBoolean());
        assertEquals("read ok", result.at("/content/0/text").asText());
        assertEquals("success", result.at("/structuredContent/status").asText());
        assertEquals("space:control-room", result.at("/structuredContent/canonicalRefs/space").asText());
    }

    @Test
    void toolsCallPreservesApprovalRequiredAsSupportSafeNonSuccess() throws Exception {
        when(client.invoke(any(BridgeInvocationRequest.class), any(RuntimeHeaders.class))).thenReturn(new BridgeInvocationResponse(
                "boards.comment",
                ToolInvocationStatus.DENIED,
                "audit://weaver-tool/boards.comment/approval_required",
                true,
                List.of(new WeaveMcpContentBlock("text", "approval needed", null, Map.of("status", "approval_required"))),
                Map.of("approvalPolicy", "PER_INVOCATION")));

        var response = mvc.perform(post("/mcp")
                        .header("Authorization", "Bearer runtime-token")
                        .header("X-Weave-Runtime-Profile", "sha256:test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"boards.comment\",\"arguments\":{\"taskRef\":\"task:1\",\"body\":\"x\"}}}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode result = objectMapper.readTree(response).at("/result");
        assertEquals(true, result.path("isError").asBoolean());
        assertEquals("approval_required", result.at("/structuredContent/status").asText());
        assertEquals(true, result.at("/structuredContent/supportSafe").asBoolean());
    }

    @Test
    void toolsCallForwardsAuthAndRuntimeHeadersIntoTypedBridgeRequest() throws Exception {
        when(client.invoke(any(BridgeInvocationRequest.class), any(RuntimeHeaders.class))).thenReturn(new BridgeInvocationResponse(
                "files.read",
                ToolInvocationStatus.SUCCESS,
                "audit://weaver-tool/files.read/invoked",
                true,
                List.of(),
                Map.of()));

        mvc.perform(post("/mcp")
                        .header("Authorization", "Bearer runtime-token")
                        .header("X-Weave-Runtime-Profile", "sha256:test")
                        .header("X-Weave-Org-Id", "org:workspace")
                        .header("X-Weave-User-Ref", "user:member")
                        .header("X-Weave-Approval-Receipt", "approval://granted")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"files.read\",\"arguments\":{}}}"))
                .andExpect(status().isOk());

        var requestCaptor = ArgumentCaptor.forClass(BridgeInvocationRequest.class);
        var headersCaptor = ArgumentCaptor.forClass(RuntimeHeaders.class);
        verify(client).invoke(requestCaptor.capture(), headersCaptor.capture());
        assertEquals("Bearer runtime-token", headersCaptor.getValue().authorization());
        assertEquals("sha256:test", headersCaptor.getValue().runtimeProfile());
        assertEquals("approval://granted", requestCaptor.getValue().runtime().approvalReceiptRef().value());
        assertEquals("org:workspace", requestCaptor.getValue().runtime().orgRef().value());
        assertEquals("user:member", requestCaptor.getValue().runtime().userRef().value());
    }

    private RuntimeInvocationContext runtime() {
        return new RuntimeInvocationContext(
                new WeaveMcpRef("org:workspace"),
                new WeaveMcpRef("user:member"),
                new WeaveMcpRef("weave-runtime-profile://sha256:test"),
                "sha256:test",
                new WeaveMcpRef("credentialref://weave/runtime/short-lived/test"),
                "audit://bridge/discovery",
                null,
                null,
                List.of("files.read"),
                List.of("files.read"));
    }
}
