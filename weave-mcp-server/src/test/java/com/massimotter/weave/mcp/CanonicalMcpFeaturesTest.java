package com.massimotter.weave.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.massimotter.weave.contract.mcp.MemberMcpDomainDefinition;
import com.massimotter.weave.contract.mcp.MemberMcpToolCatalog;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.BridgeDiscoveryResponse;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.BridgeInvocationResponse;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.RuntimeInvocationContext;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.ToolInvocationStatus;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.WeaveMcpContentBlock;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.WeaveMcpRef;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.WeaveMcpToolCatalog;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.provider.prompt.SyncStatelessMcpPromptProvider;
import org.springframework.ai.mcp.annotation.provider.resource.SyncStatelessMcpResourceProvider;
import org.springframework.ai.mcp.annotation.provider.tool.SyncMcpToolProvider;
import tools.jackson.databind.json.JsonMapper;

class CanonicalMcpFeaturesTest {

    private final WeaveServerClient client = mock(WeaveServerClient.class);
    private final CanonicalMcpFeatures features =
            new CanonicalMcpFeatures(client, JsonMapper.builder().build(), new McpToolApprovalService());

    @Test
    void springAiToolsAreGeneratedFromAnnotatedCanonicalMethods() {
        var tools = tools();

        assertThat(tools).extracting(specification -> specification.tool().name())
                .containsExactlyInAnyOrder(
                        "files.search",
                        "files.read",
                        "calendar.search_events",
                        "calendar.create_event",
                        "chat.send_message");
        tools.forEach(specification -> {
            var contract = MemberMcpToolCatalog.byName().get(specification.tool().name());
            assertThat(specification.tool().inputSchema())
                    .containsEntry("type", "object")
                    .containsKey("properties");
            assertThat(specification.tool().meta())
                    .containsEntry("domain", contract.domain())
                    .containsEntry("requiredCapability", contract.requiredCapability())
                    .containsEntry("approvalRequired", contract.approvalRequired())
                    .containsEntry("supportSafe", true)
                .containsEntry("canonical", true);
            assertThat(specification.tool().name()).doesNotContain("nextcloud", "synapse", "slack", "teams");
        });
        assertThat(tool("files.read").tool().inputSchema().get("required").toString()).contains("fileRef");
        assertThat(tool("calendar.create_event").tool().inputSchema().get("required").toString())
                .contains("title", "startsAt", "calendarRef");
        assertThat(tool("chat.send_message").tool().annotations())
                .extracting(
                        McpSchema.ToolAnnotations::readOnlyHint,
                        McpSchema.ToolAnnotations::destructiveHint,
                        McpSchema.ToolAnnotations::idempotentHint,
                        McpSchema.ToolAnnotations::openWorldHint)
                .containsExactly(false, false, true, false);
    }

    @Test
    void toolInvocationPreflightsRuntimeGrantAndReturnsSupportSafeStructuredContent() {
        when(client.invoke(eq("files.search"), eq(Map.of("path", "/Team")), any(RuntimeHeaders.class), eq(null)))
                .thenReturn(new BridgeInvocationResponse(
                        "files.search",
                        ToolInvocationStatus.SUCCESS,
                        "audit://mcp/files/search/1",
                        true,
                        List.of(new WeaveMcpContentBlock("text", "Files search completed.", null, Map.of())),
                        Map.of(
                                "status", "ok",
                                "dataPlane", "weave-webdav-facade",
                                "canonicalRefs", Map.of("folder", "file:/Team"),
                                "rawProviderPayload", "redacted")));

        var specification = tool("files.search");
        McpSchema.CallToolResult result = specification.callHandler().apply(
                exchange(),
                McpSchema.CallToolRequest.builder("files.search")
                        .arguments(Map.of("path", "/Team"))
                        .build());

        assertThat(result.isError()).isFalse();
        assertThat(result.structuredContent().toString())
                .contains("weave-webdav-facade", "file:/Team", "rawProviderPayload=redacted")
                .doesNotContain("remote.php", "access_token", "Bearer runtime-token");
        assertThat(result.meta()).containsEntry("auditRef", "audit://mcp/files/search/1");
    }

    @Test
    void approvedToolsResourceAndWorkspacePromptUseBackendGrantedCatalogOnly() {
        when(client.discover(eq("sha256:test"), any(RuntimeHeaders.class))).thenReturn(discovery());

        var resource = new SyncStatelessMcpResourceProvider(List.of(features))
                .getResourceSpecifications()
                .getFirst();
        var resourceResult = resource.readHandler().apply(
                context(),
                McpSchema.ReadResourceRequest.builder(CanonicalMcpFeatures.APPROVED_TOOLS_RESOURCE).build());
        String resourceText = ((McpSchema.TextResourceContents) resourceResult.contents().getFirst()).text();

        assertThat(resourceText)
                .contains("files.search", "files-docs", "supportSafe")
                .doesNotContain("credentialref://", "Bearer ", "remote.php", "tenantId");

        var prompt = new SyncStatelessMcpPromptProvider(List.of(features))
                .getPromptSpecifications()
                .getFirst();
        var promptResult = prompt.promptHandler().apply(
                context(),
                McpSchema.GetPromptRequest.builder(CanonicalMcpFeatures.WORKSPACE_PROMPT)
                        .arguments(Map.of("objective", "Find the team plan"))
                        .build());
        String promptText = ((McpSchema.TextContent) promptResult.messages().getFirst().content()).text();

        assertThat(promptText)
                .contains("Find the team plan", "files.search", "standard MCP form elicitation", "argument-bound receipt")
                .doesNotContain("nextcloud", "synapse", "providerAccessToken");
    }

    private List<io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification> tools() {
        return new SyncMcpToolProvider(List.of(features)).getToolSpecifications();
    }

    private io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification tool(String name) {
        return tools().stream()
                .filter(candidate -> candidate.tool().name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private McpTransportContext context() {
        return McpTransportContext.create(Map.of(
                RuntimeHeaders.AUTHORIZATION, "Bearer runtime-token",
                RuntimeHeaders.RUNTIME_PROFILE, "sha256:test",
                RuntimeHeaders.ORG_ID, "org:workspace",
                RuntimeHeaders.USER_REF, "user:member"));
    }

    private McpSyncServerExchange exchange() {
        McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
        when(exchange.transportContext()).thenReturn(context());
        return exchange;
    }

    private BridgeDiscoveryResponse discovery() {
        RuntimeInvocationContext runtime = new RuntimeInvocationContext(
                new WeaveMcpRef("org:workspace"),
                new WeaveMcpRef("user:member"),
                new WeaveMcpRef("weave-runtime-profile://sha256:test"),
                "sha256:test",
                new WeaveMcpRef("credentialref://weave/runtime/short-lived/test"),
                "audit://mcp/discovery/test",
                List.of("files.read"),
                List.of("files.search"));
        return new BridgeDiscoveryResponse(
                runtime,
                new WeaveMcpToolCatalog(
                        MemberMcpToolCatalog.SERVER_NAMESPACE,
                        MemberMcpDomainDefinition.CONTRACT_VERSION,
                        List.of(MemberMcpToolCatalog.byName().get("files.search").asBridgeDefinition())));
    }
}
