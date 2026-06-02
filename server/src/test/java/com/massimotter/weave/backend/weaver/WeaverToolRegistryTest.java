package com.massimotter.weave.backend.weaver;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.InMemoryAuditEventPublisher;
import java.time.Instant;
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
                Map.of(
                        "spaceRef", "space:control-room",
                        "decisionRef", "decision:governed-weaver",
                        "boardTaskRef", "board-task:WEAVE-601",
                        "body", "Looks good"),
                "approval:abc123"));

        assertThat(approved.status()).isEqualTo("ok");
        assertThat(approved.approvalRequired()).isFalse();
        assertThat(approved.redactedResult()).containsEntry("rawProviderPayload", "redacted");
        assertThat(approved.redactedResult().get("canonicalRefs").toString())
                .contains("space:control-room", "decision:governed-weaver", "board-task:WEAVE-601")
                .doesNotContain("Looks good", "providerRoom", "matrix");
        assertThat(audit.events()).hasSize(2);
        assertThat(audit.events()).allSatisfy(event -> assertThat(event.payload()).containsEntry("supportSafe", true));
        assertThat(audit.events()).allSatisfy(event -> assertThat(event.payload())
                .containsKeys("runtimeProfileHash", "user", "tool", "action", "domain", "providerRef", "credentialRef", "decision", "auditRef"));
    }

    @Test
    void failsClosedForUnsignedRevokedExpiredMismatchedConsentAndOverbroadGovernance() {
        InMemoryAuditEventPublisher audit = new InMemoryAuditEventPublisher();
        WeaverToolRegistry registry = new WeaverToolRegistry(audit);

        assertThat(registry.invoke(governedRequest("runtime_profile_unsigned", "", false, future(), true, "user:abc123", List.of("boards.comment"))).status())
                .isEqualTo("runtime_profile_unsigned");
        assertThat(registry.invoke(governedRequest("runtime_profile_user_mismatch", signature(), false, future(), true, "user:other", List.of("boards.comment"))).status())
                .isEqualTo("runtime_profile_user_mismatch");
        assertThat(registry.invoke(governedRequest("runtime_profile_revoked", signature(), true, future(), true, "user:abc123", List.of("boards.comment"))).status())
                .isEqualTo("runtime_profile_revoked");
        assertThat(registry.invoke(governedRequest("runtime_token_expired", signature(), false, Instant.now().minusSeconds(60).toString(), true, "user:abc123", List.of("boards.comment"))).status())
                .isEqualTo("runtime_token_expired");
        assertThat(registry.invoke(governedRequest("consent_required", signature(), false, future(), false, "user:abc123", List.of("boards.comment"))).status())
                .isEqualTo("consent_required");
        assertThat(registry.invoke(governedRequest("overbroad_grant", signature(), false, future(), true, "user:abc123", List.of("boards.*"))).status())
                .isEqualTo("overbroad_grant");

        assertThat(audit.events()).hasSize(6);
        assertThat(audit.events()).extracting(event -> event.payload().get("status"))
                .containsExactly(
                        "runtime_profile_unsigned",
                        "runtime_profile_user_mismatch",
                        "runtime_profile_revoked",
                        "runtime_token_expired",
                        "consent_required",
                        "overbroad_grant");
        assertThat(audit.events()).allSatisfy(event -> {
            assertThat(event.action()).isEqualTo(AuditAction.WEAVER_TOOL_INVOCATION_RECORDED);
            assertThat(event.payload()).containsEntry("supportSafe", true);
            assertThat(event.payload()).containsKeys("runtimeProfileHash", "user", "tool", "decision", "auditRef");
            assertThat(event.payload().toString()).doesNotContain("prompt", "Bearer ", "refresh_token", "providerRoom");
        });
    }

    @Test
    void failsClosedWhenSignedProfileDoesNotScopeTheSpecificTool() {
        InMemoryAuditEventPublisher audit = new InMemoryAuditEventPublisher();
        WeaverToolRegistry registry = new WeaverToolRegistry(audit);

        var result = registry.invoke(governedRequest(
                "scoped_grant_missing",
                signature(),
                false,
                future(),
                true,
                "user:abc123",
                List.of("calendar.search_events")));

        assertThat(result.status()).isEqualTo("scoped_grant_missing");
        assertThat(result.redactedResult()).containsEntry("auditRef", "audit://weaver-tool/boards.comment/scoped_grant_missing");
        assertThat(audit.events().get(0).payload()).containsEntry("decision", "scoped_grant_missing");
    }

    private WeaverToolInvocationRequest governedRequest(
            String marker,
            String signature,
            boolean revoked,
            String runtimeTokenExpiresAt,
            boolean consentGranted,
            String runtimeProfileUserRef,
            List<String> scopedToolGrants) {
        return new WeaverToolInvocationRequest(
                "boards.comment",
                "user:abc123",
                "sha256:" + marker + "000000000000000000000000000000000000000000000000",
                runtimeProfileUserRef,
                signature,
                revoked,
                runtimeTokenExpiresAt,
                consentGranted,
                List.of("weaver.boards_write"),
                scopedToolGrants,
                Map.of(
                        "spaceRef", "space:control-room",
                        "decisionRef", "decision:governed-weaver",
                        "boardTaskRef", "board-task:WEAVE-601",
                        "body", "support-safe proposal"),
                "approval:abc123");
    }

    private String signature() {
        return "weave-signature:v1:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    }

    private String future() {
        return Instant.now().plusSeconds(300).toString();
    }
}
