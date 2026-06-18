package com.massimotter.weave.backend.weaver;

import com.massimotter.weave.backend.audit.InMemoryAuditEventPublisher;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocalQwenMcpToolBridgeTest {

    @Test
    void localQwenReadOnlyToolCallExecutesThroughGovernedDomainToolPlane() {
        InMemoryAuditEventPublisher auditPublisher = new InMemoryAuditEventPublisher();
        LocalQwenMcpToolBridge bridge = new LocalQwenMcpToolBridge(new WeaverToolRegistry(auditPublisher));

        var turn = bridge.execute(request("calendar.search_events", false, Map.of("spaceRef", "space:default")));

        assertThat(turn.allowed()).isTrue();
        assertThat(turn.decision()).isEqualTo("ok");
        assertThat(turn.supportSafeEvidence())
                .containsEntry("channelId", "channels.weave-chat")
                .containsEntry("modelRef", "lmstudio/qwen/qwen3.5-9b")
                .containsEntry("runtimeProfileHash", "rph-qwen-tool-test")
                .containsEntry("runtimeProfileVersion", "weaver-runtime-profile:v1")
                .containsEntry("mcpServerId", "mcp-weave-domain-tools")
                .containsEntry("toolId", "calendar.search_events")
                .containsEntry("approvalState", "not_required")
                .containsEntry("toolCallReceived", true)
                .containsEntry("toolResultFedBackToModel", true)
                .containsEntry("supportSafe", true)
                .containsEntry("rawProviderPayloadIncluded", false)
                .containsEntry("visibleThinkingTreatedAsAuthority", false);
        assertThat(turn.toolResult())
                .containsEntry("rawProviderPayload", "redacted")
                .containsKey("auditRef");
        assertThat(auditPublisher.events()).hasSize(1);
        assertThat(auditPublisher.events().get(0).payload())
                .containsEntry("runtimeProfileHash", "rph-qwen-tool-test")
                .containsEntry("tool", "calendar.search_events")
                .containsEntry("decision", "invoked")
                .containsEntry("providerRef", "provider:domain-facade")
                .containsEntry("credentialRef", "credentialref://weave/runtime/short-lived");
        assertThat(auditPublisher.events().toString())
                .doesNotContain("Bearer", "access_token", "rawProviderPayload={", "secret.value");
    }

    @Test
    void unknownToolIsDeniedBeforeInvocation() {
        InMemoryAuditEventPublisher auditPublisher = new InMemoryAuditEventPublisher();
        LocalQwenMcpToolBridge bridge = new LocalQwenMcpToolBridge(new WeaverToolRegistry(auditPublisher));

        var turn = bridge.execute(request("provider.native_dump", false, Map.of("spaceRef", "space:default")));

        assertThat(turn.allowed()).isFalse();
        assertThat(turn.decision()).isEqualTo("tool_not_offered");
        assertThat(turn.supportSafeEvidence())
                .containsEntry("denyState", "tool_not_offered")
                .containsEntry("toolResultFedBackToModel", false);
        assertThat(auditPublisher.events()).isEmpty();
    }

    @Test
    void overbroadArgsAreDeniedBeforeInvocation() {
        InMemoryAuditEventPublisher auditPublisher = new InMemoryAuditEventPublisher();
        LocalQwenMcpToolBridge bridge = new LocalQwenMcpToolBridge(new WeaverToolRegistry(auditPublisher));

        var turn = bridge.execute(request("calendar.search_events", false, Map.of("providerPayload", "dump everything")));

        assertThat(turn.allowed()).isFalse();
        assertThat(turn.decision()).isEqualTo("overbroad_args");
        assertThat(turn.supportSafeEvidence())
                .containsEntry("denyState", "overbroad_args")
                .containsEntry("rawProviderPayloadIncluded", false);
        assertThat(auditPublisher.events()).isEmpty();
    }

    @Test
    void revokedRuntimeProfileIsDeniedByToolRegistryPolicyGuard() {
        InMemoryAuditEventPublisher auditPublisher = new InMemoryAuditEventPublisher();
        LocalQwenMcpToolBridge bridge = new LocalQwenMcpToolBridge(new WeaverToolRegistry(auditPublisher));

        var turn = bridge.execute(request("calendar.search_events", true, Map.of("spaceRef", "space:default")));

        assertThat(turn.allowed()).isFalse();
        assertThat(turn.decision()).isEqualTo("runtime_profile_revoked");
        assertThat(turn.supportSafeEvidence())
                .containsEntry("denyState", "runtime_profile_revoked")
                .containsEntry("toolResultFedBackToModel", false);
        assertThat(auditPublisher.events()).hasSize(1);
        assertThat(auditPublisher.events().get(0).payload())
                .containsEntry("decision", "runtime_profile_revoked")
                .containsEntry("runtimeProfileHash", "rph-qwen-tool-test");
    }

    @Test
    void chatSendMessageIsNotOfferedAsInboundTransportTool() {
        LocalQwenMcpToolBridge bridge = new LocalQwenMcpToolBridge(new WeaverToolRegistry(new InMemoryAuditEventPublisher()));

        assertThat(bridge.offeredTools(List.of("weaver.chat_read", "weaver.calendar_read")))
                .extracting(WeaverDomainToolDefinition::name)
                .contains("chat.search_messages", "calendar.search_events")
                .doesNotContain("chat.send_message");

        var turn = bridge.execute(request("chat.send_message", false, Map.of("spaceRef", "space:default")));
        assertThat(turn.allowed()).isFalse();
        assertThat(turn.decision()).isEqualTo("tool_not_offered");
    }

    private LocalQwenMcpToolBridge.QwenMcpToolRequest request(
            String toolName,
            boolean revoked,
            Map<String, Object> input) {
        return new LocalQwenMcpToolBridge.QwenMcpToolRequest(
                "channels.weave-chat",
                "lmstudio/qwen/qwen3.5-9b",
                toolName,
                "user:123",
                "rph-qwen-tool-test",
                "weaver-runtime-profile:v1",
                "user:123",
                "weave-signature:v1:test-support-safe",
                revoked,
                Instant.now().plusSeconds(300).toString(),
                true,
                List.of("weaver.calendar_read", "weaver.chat_read"),
                List.of(toolName),
                input);
    }
}
