package com.massimotter.weave.contract.mcp;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.ApprovalReceiptRef;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.RuntimeInvocationContext;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.WeaveMcpRef;

class MemberMcpToolCatalogTest {
    @Test void canonicalToolsUseContractCapabilitiesAndNoWeaverDialect() {
        var capabilities = new HashSet<String>();
        for (var domain : MemberMcpDomainDefinition.values()) capabilities.addAll(domain.allCapabilities());
        capabilities.add("chat.send");
        for (var tool : MemberMcpToolCatalog.tools()) {
            assertFalse(tool.requiredCapability().startsWith("weaver."), tool.name());
            if (!tool.name().equals("registry.tools.read")) assertTrue(capabilities.contains(tool.requiredCapability()), tool.name());
            assertFalse(tool.inputSchema().containsKey("javaType"), tool.name());
            assertEquals("object", tool.inputSchema().get("type"), tool.name());
            assertEquals(Boolean.FALSE, tool.inputSchema().get("additionalProperties"), tool.name());
        }
    }

    @Test void writeLikeToolsRequireApproval() {
        for (var tool : MemberMcpToolCatalog.tools()) if (tool.writeLike()) assertTrue(tool.approvalRequired(), tool.name());
    }

    @Test void contractSchemasExposeRealPropertiesAndApprovalStaysOutOfBusinessArguments() {
        assertEquals(List.of("title", "startsAt"), MemberMcpToolCatalog.byName().get("calendar.create_event").inputSchema().get("required"));
        assertEquals(List.of("taskRef", "body"), MemberMcpToolCatalog.byName().get("boards.comment").inputSchema().get("required"));
        assertEquals(List.of("threadRef", "body"), MemberMcpToolCatalog.byName().get("chat.send_message").inputSchema().get("required"));
        for (var toolName : List.of("calendar.create_event", "boards.comment", "chat.send_message")) {
            @SuppressWarnings("unchecked")
            var properties = (Map<String, Object>) MemberMcpToolCatalog.byName().get(toolName).inputSchema().get("properties");
            assertFalse(properties.containsKey("approvalReceiptRef"), toolName);
        }
    }

    @Test void bridgeCatalogProjectsLegacyTools() {
        var bridgeCatalog = MemberMcpToolCatalog.bridgeCatalog();
        assertEquals(MemberMcpToolCatalog.SERVER_NAMESPACE, bridgeCatalog.serverNamespace());
        assertEquals(MemberMcpDomainDefinition.CONTRACT_VERSION, bridgeCatalog.contractVersion());
        assertEquals(MemberMcpToolCatalog.tools().size(), bridgeCatalog.tools().size());
        assertEquals("chat.send_message", bridgeCatalog.tools().getLast().name());
    }

    @Test void runtimeInvocationContextCarriesApprovalAtEnvelopeLevel() {
        var context = new RuntimeInvocationContext(
                new WeaveMcpRef("org://dogfood"),
                new WeaveMcpRef("user://massimo"),
                new WeaveMcpRef("weave-runtime-profile://abc"),
                "sha256:abc",
                new WeaveMcpRef("credentialref://weave/runtime/1"),
                "audit://mcp/runtime/support-safe",
                new ApprovalReceiptRef("approval://calendar/1"),
                null,
                List.of("calendar.manage_events"),
                List.of("calendar.create_event"));
        assertEquals("approval://calendar/1", context.approvalReceiptRef().value());
        assertEquals(List.of("calendar.create_event"), context.allowedTools());
    }
}
