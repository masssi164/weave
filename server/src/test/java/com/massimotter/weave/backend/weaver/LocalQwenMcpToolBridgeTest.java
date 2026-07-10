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

        var turn = bridge.execute(request("calendar.search_events", false, Map.of("calendarRef", "calendar:workspace")));

        assertThat(turn.allowed()).isTrue();
        assertThat(turn.decision()).isEqualTo("ok");
        assertThat(turn.supportSafeEvidence())
                .containsEntry("channelId", "channels.matrix")
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
        assertThat(turn.supportSafeEvidence().toString())
                .doesNotContain("dump everything", "providerPayload");
        assertThat(auditPublisher.events()).isEmpty();
    }

    @Test
    void unexpectedArgsAreDeniedBeforeInvocation() {
        InMemoryAuditEventPublisher auditPublisher = new InMemoryAuditEventPublisher();
        LocalQwenMcpToolBridge bridge = new LocalQwenMcpToolBridge(new WeaverToolRegistry(auditPublisher));

        var turn = bridge.execute(request("calendar.search_events", false, Map.of("calendarRef", "calendar:workspace", "unexpected", "value")));

        assertThat(turn.allowed()).isFalse();
        assertThat(turn.decision()).isEqualTo("overbroad_args");
        assertThat(turn.supportSafeEvidence())
                .containsEntry("denyState", "overbroad_args")
                .containsEntry("toolResultFedBackToModel", false)
                .containsEntry("rawProviderPayloadIncluded", false);
        assertThat(turn.supportSafeEvidence().toString()).doesNotContain("unexpected", "value");
        assertThat(auditPublisher.events()).isEmpty();
    }

    @Test
    void nestedProviderAndSecretShapedArgsAreDeniedBeforeInvocation() {
        InMemoryAuditEventPublisher auditPublisher = new InMemoryAuditEventPublisher();
        LocalQwenMcpToolBridge bridge = new LocalQwenMcpToolBridge(new WeaverToolRegistry(auditPublisher));

        var turn = bridge.execute(request("calendar.search_events", false, Map.of(
                "spaceRef", "space:default",
                "query", Map.of(
                        "providerUrl", "https://svc-user:svc-pass@calendar.weave.test?access_token=raw",
                        "tokenLike", "Bearer raw"))));

        assertThat(turn.allowed()).isFalse();
        assertThat(turn.decision()).isEqualTo("overbroad_args");
        assertThat(turn.supportSafeEvidence())
                .containsEntry("denyState", "overbroad_args")
                .containsEntry("toolResultFedBackToModel", false)
                .containsEntry("rawProviderPayloadIncluded", false);
        assertThat(turn.supportSafeEvidence().toString())
                .doesNotContain("calendar.weave.test", "access_token", "Bearer raw", "providerUrl", "tokenLike");
        assertThat(auditPublisher.events()).isEmpty();
    }

    @Test
    void nestedSecretRefUnderAllowedTopLevelArgIsDeniedBeforeInvocation() {
        InMemoryAuditEventPublisher auditPublisher = new InMemoryAuditEventPublisher();
        LocalQwenMcpToolBridge bridge = new LocalQwenMcpToolBridge(new WeaverToolRegistry(auditPublisher));

        var turn = bridge.execute(request("calendar.search_events", false, Map.of(
                "query", Map.of("secretRef", Map.of("value", "secret://calendar/provider-token")))));

        assertThat(turn.allowed()).isFalse();
        assertThat(turn.decision()).isEqualTo("overbroad_args");
        assertThat(turn.supportSafeEvidence())
                .containsEntry("denyState", "overbroad_args")
                .containsEntry("toolResultFedBackToModel", false)
                .containsEntry("rawProviderPayloadIncluded", false);
        assertThat(turn.supportSafeEvidence().toString())
                .doesNotContain("secret://calendar/provider-token", "secretRef");
        assertThat(auditPublisher.events()).isEmpty();
    }

    @Test
    void nestedRawPayloadUnderAllowedTopLevelArgIsDeniedBeforeInvocation() {
        InMemoryAuditEventPublisher auditPublisher = new InMemoryAuditEventPublisher();
        LocalQwenMcpToolBridge bridge = new LocalQwenMcpToolBridge(new WeaverToolRegistry(auditPublisher));

        var turn = bridge.execute(request("calendar.search_events", false, Map.of(
                "query", Map.of("rawPayload", Map.of("provider", "calendar", "body", "raw dump")))));

        assertThat(turn.allowed()).isFalse();
        assertThat(turn.decision()).isEqualTo("overbroad_args");
        assertThat(turn.supportSafeEvidence())
                .containsEntry("denyState", "overbroad_args")
                .containsEntry("toolResultFedBackToModel", false)
                .containsEntry("rawProviderPayloadIncluded", false);
        assertThat(turn.supportSafeEvidence().toString())
                .doesNotContain("raw dump", "rawPayload");
        assertThat(auditPublisher.events()).isEmpty();
    }

    @Test
    void runtimeProfileRevocationMarkerIsCorrelationOnlyForMcpToolPolicy() {
        InMemoryAuditEventPublisher auditPublisher = new InMemoryAuditEventPublisher();
        LocalQwenMcpToolBridge bridge = new LocalQwenMcpToolBridge(new WeaverToolRegistry(auditPublisher));

        var turn = bridge.execute(request("calendar.search_events", true, Map.of("calendarRef", "calendar:workspace")));

        assertThat(turn.allowed()).isTrue();
        assertThat(turn.decision()).isEqualTo("ok");
        assertThat(turn.supportSafeEvidence())
                .containsEntry("denyState", "allowed")
                .containsEntry("toolResultFedBackToModel", true);
        assertThat(auditPublisher.events()).hasSize(1);
        assertThat(auditPublisher.events().get(0).payload())
                .containsEntry("decision", "invoked")
                .containsEntry("runtimeProfileAuthority", "correlation_only")
                .containsEntry("policyEnforcementPoint", "weave-mcp-server")
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
                "channels.matrix",
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
