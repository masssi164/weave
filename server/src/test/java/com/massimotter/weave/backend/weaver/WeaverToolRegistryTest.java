package com.massimotter.weave.backend.weaver;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.InMemoryAuditEventPublisher;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WeaverToolRegistryTest {

    @Test
    void discoversOnlyDomainToolsGrantedByRuntimeProfile() {
        // V01_GOVERNED_WEAVER_TOOL_REGISTRY
        WeaverToolRegistry registry = new WeaverToolRegistry(new InMemoryAuditEventPublisher());

        var tools = registry.discover(List.of("weaver.files_read", "weaver.boards_write"));

        assertThat(tools).extracting(WeaverDomainToolDefinition::name)
                .containsExactly("files.search", "boards.comment");
        assertThat(tools).extracting(WeaverDomainToolDefinition::version).containsOnly("v1");
        assertThat(tools).extracting(WeaverDomainToolDefinition::requiredCapability)
                .containsExactly("weaver.files_read", "weaver.boards_write");
        assertThat(tools).allSatisfy(tool -> {
            assertThat(tool.name()).contains(".");
            assertThat(tool.inputSchema()).containsEntry("additionalProperties", false);
            assertThat(tool.resultRedactionRules()).contains("rawProviderPayload");
        });
    }

    @Test
    void hidesAndBlocksToolsOutsideCapabilityGrants() {
        InMemoryAuditEventPublisher audit = new InMemoryAuditEventPublisher();
        WeaverToolRegistry registry = new WeaverToolRegistry(audit);

        assertThat(registry.discover(List.of("weaver.files_read"))).extracting(WeaverDomainToolDefinition::name)
                .doesNotContain("boards.comment", "notifications.create_action_request");

        var result = registry.invoke(new WeaverToolInvocationRequest(
                "boards.comment",
                "user:abc123",
                "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                List.of("weaver.files_read"),
                Map.of("taskId", "TASK-1", "body", "Looks good"),
                null));

        assertThat(result.status()).isEqualTo("blocked");
        assertThat(result.supportSafeMessage()).doesNotContain("TASK-1");
        assertThat(audit.events()).hasSize(1);
        assertThat(audit.events().get(0).action()).isEqualTo(AuditAction.WEAVER_TOOL_INVOCATION_RECORDED);
        assertThat(audit.events().get(0).payload()).containsEntry("status", "blocked");
        assertThat(audit.events().get(0).payload())
                .containsEntry("runtimeProfileHash", "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                .containsEntry("user", "user:abc123")
                .containsEntry("tool", "boards.comment")
                .containsEntry("action", "tool.invoke")
                .containsEntry("domain", "weaver-runtime")
                .containsEntry("providerRef", "provider:domain-facade")
                .containsEntry("credentialRef", "credentialref://weave/runtime/short-lived")
                .containsEntry("decision", "blocked");
        assertThat(audit.events().get(0).payload()).containsEntry("supportSafe", true);
    }

    @Test
    void requiresApprovalReceiptForWriteLikeToolsBeforeInvocation() {
        InMemoryAuditEventPublisher audit = new InMemoryAuditEventPublisher();
        WeaverToolRegistry registry = new WeaverToolRegistry(audit);

        var missingApproval = registry.invoke(new WeaverToolInvocationRequest(
                "boards.comment",
                "user:abc123",
                "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                List.of("weaver.boards_write"),
                Map.of("taskId", "TASK-1", "body", "Looks good"),
                null));

        assertThat(missingApproval.status()).isEqualTo("approval_required");
        assertThat(missingApproval.approvalRequired()).isTrue();
        assertThat(missingApproval.redactedResult()).containsEntry("approvalPolicy", "REQUIRED_BEFORE_INVOCATION");

        var approved = registry.invoke(new WeaverToolInvocationRequest(
                "boards.comment",
                "user:abc123",
                "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                List.of("weaver.boards_write"),
                Map.of("taskId", "TASK-1", "body", "Looks good"),
                "approval:abc123"));

        assertThat(approved.status()).isEqualTo("ok");
        assertThat(approved.approvalRequired()).isFalse();
        assertThat(approved.redactedResult()).containsEntry("rawProviderPayload", "redacted");
        assertThat(audit.events()).hasSize(2);
        assertThat(audit.events()).allSatisfy(event -> assertThat(event.payload()).containsEntry("supportSafe", true));
        assertThat(audit.events()).allSatisfy(event -> assertThat(event.payload())
                .containsKeys("runtimeProfileHash", "user", "tool", "action", "domain", "providerRef", "credentialRef", "decision"));
    }
}
