package com.massimotter.weave.contract.mcp;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import org.junit.jupiter.api.Test;

class MemberMcpToolCatalogTest {
    @Test void canonicalToolsUseContractCapabilitiesAndNoWeaverDialect() {
        var capabilities = new HashSet<String>();
        for (var domain : MemberMcpDomainDefinition.values()) capabilities.addAll(domain.allCapabilities());
        for (var tool : MemberMcpToolCatalog.tools()) {
            assertFalse(tool.requiredCapability().startsWith("weaver."), tool.name());
            if (!tool.name().equals("registry.tools.read")) assertTrue(capabilities.contains(tool.requiredCapability()), tool.name());
            assertFalse(tool.inputSchema().containsKey("javaType"), tool.name());
            assertEquals("object", tool.inputSchema().get("type"), tool.name());
        }
    }
    @Test void writeLikeToolsRequireApproval() {
        for (var tool : MemberMcpToolCatalog.tools()) if (tool.writeLike()) assertTrue(tool.approvalRequired(), tool.name());
    }
}
