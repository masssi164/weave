package com.massimotter.weave.mcp;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.massimotter.weave.contract.mcp.MemberMcpToolCatalog;
import java.util.Map;
import org.junit.jupiter.api.Test;
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
    void toolsListFailsClosedWithoutRuntimeAuthContext() throws Exception {
        mvc.perform(post("/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("missing runtime authorization context")));
    }

    @Test
    void toolsListDecoratesBackendGovernedDiscoveryWithoutMetadataDrift() throws Exception {
        when(client.discover(eq("sha256:test"), any(RuntimeHeaders.class))).thenReturn(MemberMcpToolCatalog.tools().stream()
                .map(tool -> Map.<String, Object>of("name", tool.name(), "inputSchema", tool.inputSchema(), "supportSafeDescription", tool.description()))
                .toList());

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
        assertEquals(MemberMcpToolCatalog.tools().size(), tools.size(), "tools/list must expose exactly backend-governed contract tools from this fixture");
        for (var contractTool : MemberMcpToolCatalog.tools()) {
            JsonNode projected = null;
            for (JsonNode node : tools) if (contractTool.name().equals(node.path("name").asText())) projected = node;
            assertNotNull(projected, "missing MCP tool projection for " + contractTool.name());
            assertEquals(contractTool.description(), projected.path("description").asText(), contractTool.name());
            assertEquals(objectMapper.valueToTree(contractTool.inputSchema()), projected.path("inputSchema"), contractTool.name());
            assertEquals(!contractTool.writeLike(), projected.at("/annotations/readOnlyHint").asBoolean(), contractTool.name());
            assertEquals(contractTool.approvalRequired(), projected.at("/annotations/destructiveHint").asBoolean(), contractTool.name());
        }
        verify(client).discover(eq("sha256:test"), any(RuntimeHeaders.class));
    }

    @Test
    void writeToolRequiresApprovalBeforeBackendCall() throws Exception {
        mvc.perform(post("/mcp")
                        .header("Authorization", "Bearer runtime-token")
                        .header("X-Weave-Runtime-Profile", "sha256:test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"boards.comment\",\"arguments\":{\"taskRef\":\"task:1\",\"body\":\"x\"}}}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("approval_required")));
    }
}
