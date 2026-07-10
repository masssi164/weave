package com.massimotter.weave.backend.weaver;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.InMemoryAuditEventPublisher;
import com.massimotter.weave.contract.mcp.MemberMcpDomainDefinition;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WeaverToolRegistryTest {

    @Test
    void mcpToolsForNonChatDomainsUseCanonicalDomainContractVocabulary() {
        WeaverToolRegistry registry = new WeaverToolRegistry(new InMemoryAuditEventPublisher());

        var tools = registry.discover(List.of(
                "files.read",
                "calendar.read",
                "boards.read",
                "boards.read",
                "boards.update_task"));

        assertThat(tools).filteredOn(tool -> tool.name().startsWith("files."))
                .allSatisfy(tool -> assertThat(tool.domain()).isEqualTo(MemberMcpDomainDefinition.FILES_DOCS.domain()));
        assertThat(tools).filteredOn(tool -> tool.name().startsWith("calendar."))
                .allSatisfy(tool -> assertThat(tool.domain()).isEqualTo(MemberMcpDomainDefinition.CALENDAR_MEETINGS.domain()));
        assertThat(tools).filteredOn(tool -> tool.name().startsWith("boards.") || tool.name().startsWith("tasks."))
                .allSatisfy(tool -> assertThat(tool.domain()).isEqualTo(MemberMcpDomainDefinition.BOARDS_TASKS.domain()));
        assertThat(tools).extracting(WeaverDomainToolDefinition::domain)
                .doesNotContain("calendar-events", "files_documents", "boards_tasks", "provider", "adapter");
        assertThat(tools).allSatisfy(tool -> assertThat(tool.inputSchema().toString().toLowerCase())
                .doesNotContain("caldav", "webdav", "nextcloud", "openproject", "provider", "adapter"));
    }

    @Test
    void discoversOnlyDomainToolsGrantedByRuntimeProfile() {
        // V01_GOVERNED_WEAVER_TOOL_REGISTRY
        WeaverToolRegistry registry = new WeaverToolRegistry(new InMemoryAuditEventPublisher());

        var tools = registry.discover(List.of("files.read", "boards.update_task"));

        assertThat(tools).extracting(WeaverDomainToolDefinition::name)
                .containsExactly("files.search", "files.read", "boards.comment");
        assertThat(tools).extracting(WeaverDomainToolDefinition::version).containsOnly("v1");
        assertThat(tools).extracting(WeaverDomainToolDefinition::requiredCapability)
                .containsExactly("files.read", "files.read", "boards.update_task");
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

        assertThat(registry.discover(List.of("files.read"))).extracting(WeaverDomainToolDefinition::name)
                .doesNotContain("boards.comment", "notifications.create_action_request");

        var result = registry.invoke(new WeaverToolInvocationRequest(
                "boards.comment",
                "user:abc123",
                "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                List.of("files.read"),
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
                List.of("boards.update_task"),
                Map.of("taskId", "TASK-1", "body", "Looks good"),
                null));

        assertThat(missingApproval.status()).isEqualTo("approval_required");
        assertThat(missingApproval.approvalRequired()).isTrue();
        assertThat(missingApproval.redactedResult()).containsEntry("approvalPolicy", "REQUIRED_BEFORE_INVOCATION");

        var approved = registry.invoke(new WeaverToolInvocationRequest(
                "boards.comment",
                "user:abc123",
                "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                "user:abc123",
                signature(),
                false,
                future(),
                true,
                List.of("boards.update_task"),
                List.of("boards.comment"),
                Map.of(
                        "spaceRef", "space:control-room",
                        "decisionRef", "decision:governed-weaver",
                        "boardTaskRef", "board-task:WEAVE-601",
                        "body", "Looks good"),
                "approval:abc123",
                new WeaverApprovalReceipt(
                        "approval:abc123",
                        "user:abc123",
                        "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                        MemberMcpDomainDefinition.BOARDS_TASKS.domain(),
                        "boards.comment",
                        List.of("space:control-room", "decision:governed-weaver", "board-task:WEAVE-601"),
                        WeaverApprovalReceipt.argumentDigest(Map.of(
                                "spaceRef", "space:control-room",
                                "decisionRef", "decision:governed-weaver",
                                "boardTaskRef", "board-task:WEAVE-601",
                                "body", "Looks good")),
                        MemberMcpDomainDefinition.CONTRACT_VERSION,
                        "policy:support-safe-bridge-v1",
                        "approved",
                        "allow-once",
                        "elicitation://openclaw/test",
                        Instant.now().minusSeconds(1).toString(),
                        future(),
                        "audit://weaver-approval/test")));

        assertThat(approved.status()).isEqualTo("ok");
        assertThat(approved.approvalRequired()).isFalse();
        assertThat(approved.redactedResult()).containsEntry("rawProviderPayload", "redacted");
        assertThat(approved.redactedResult()).containsEntry("approvalReceiptAuditRef", "audit://weaver-approval/test");
        assertThat(approved.redactedResult().get("canonicalRefs").toString())
                .contains("space:control-room", "decision:governed-weaver", "board-task:WEAVE-601")
                .doesNotContain("Looks good", "providerRoom", "matrix");
        assertThat(audit.events()).hasSize(2);
        assertThat(audit.events()).allSatisfy(event -> assertThat(event.payload()).containsEntry("supportSafe", true));
        assertThat(audit.events()).allSatisfy(event -> assertThat(event.payload())
                .containsKeys("runtimeProfileHash", "user", "tool", "action", "domain", "providerRef", "credentialRef", "decision", "auditRef"));
        assertThat(audit.events().get(1).payload())
                .containsEntry("approvalReceiptValidated", true)
                .containsEntry("approvalReceiptAuditRef", "audit://weaver-approval/test")
                .containsEntry("approvalReceiptPolicyVersion", "policy:support-safe-bridge-v1");
    }

    @Test
    void mismatchedApprovalReceiptScopeFailsClosedBeforeInvocation() {
        InMemoryAuditEventPublisher audit = new InMemoryAuditEventPublisher();
        WeaverToolRegistry registry = new WeaverToolRegistry(audit);

        var result = registry.invoke(new WeaverToolInvocationRequest(
                "boards.comment",
                "user:abc123",
                "sha256:scope-mismatch000000000000000000000000000000000000000000000000",
                "user:abc123",
                signature(),
                false,
                future(),
                true,
                List.of("weaver.boards_write"),
                List.of("boards.comment"),
                Map.of(
                        "spaceRef", "space:control-room",
                        "decisionRef", "decision:governed-weaver",
                        "boardTaskRef", "board-task:WEAVE-602",
                        "body", "Looks good"),
                "approval:abc123",
                new WeaverApprovalReceipt(
                        "approval:abc123",
                        "user:abc123",
                        "sha256:scope-mismatch000000000000000000000000000000000000000000000000",
                        MemberMcpDomainDefinition.BOARDS_TASKS.domain(),
                        "boards.comment",
                        List.of("space:control-room", "decision:governed-weaver", "board-task:WEAVE-601"),
                        WeaverApprovalReceipt.argumentDigest(Map.of(
                                "spaceRef", "space:control-room",
                                "decisionRef", "decision:governed-weaver",
                                "boardTaskRef", "board-task:WEAVE-602",
                                "body", "Looks good")),
                        MemberMcpDomainDefinition.CONTRACT_VERSION,
                        "policy:support-safe-bridge-v1",
                        "approved",
                        "allow-once",
                        "elicitation://openclaw/test",
                        Instant.now().minusSeconds(1).toString(),
                        future(),
                        "audit://weaver-approval/test")));

        assertThat(result.status()).isEqualTo("approval_receipt_invalid");
        assertThat(result.approvalRequired()).isFalse();
        assertThat(result.redactedResult()).containsEntry("approvalReceiptValidated", false);
        assertThat(result.redactedResult().get("canonicalRefs").toString())
                .contains("space:control-room", "decision:governed-weaver", "board-task:WEAVE-602")
                .doesNotContain("WEAVE-601");
        assertThat(audit.events()).hasSize(1);
        assertThat(audit.events().get(0).payload())
                .containsEntry("status", "approval_receipt_invalid")
                .containsEntry("approvalReceiptValidated", false)
                .containsEntry("serverApprovalDecision", false);
        assertThat(audit.events().get(0).payload().toString()).doesNotContain("Looks good", "provider payload", "secret");
    }

    @Test
    void runtimeDeniedOrTimedOutApprovalFailsClosedWithoutServerDecision() {
        InMemoryAuditEventPublisher audit = new InMemoryAuditEventPublisher();
        WeaverToolRegistry registry = new WeaverToolRegistry(audit);

        var denied = registry.invoke(new WeaverToolInvocationRequest(
                "boards.comment",
                "user:abc123",
                "sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
                List.of("weaver.boards_write"),
                Map.of("boardTaskRef", "board-task:WEAVE-833", "body", "provider payload must stay out"),
                "approval:denied:user-runtime"));
        var timedOut = registry.invoke(new WeaverToolInvocationRequest(
                "boards.comment",
                "user:abc123",
                "sha256:dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd",
                List.of("weaver.boards_write"),
                Map.of("boardTaskRef", "board-task:WEAVE-833", "body", "provider payload must stay out"),
                "approval:timeout:user-runtime"));

        assertThat(denied.status()).isEqualTo("approval_denied");
        assertThat(timedOut.status()).isEqualTo("approval_timeout");
        assertThat(denied.approvalRequired()).isFalse();
        assertThat(timedOut.approvalRequired()).isFalse();
        assertThat(audit.events()).hasSize(2);
        assertThat(audit.events()).allSatisfy(event -> assertThat(event.payload())
                .containsEntry("approvalAuthority", "user_openclaw_runtime")
                .containsEntry("serverApprovalDecision", false)
                .containsEntry("supportSafe", true));
        assertThat(audit.events().toString()).doesNotContain("provider payload", "secret", "Bearer ");
    }

    @Test
    void readOnlyToolsDoNotRequestApprovalToAvoidApprovalFatigue() {
        InMemoryAuditEventPublisher audit = new InMemoryAuditEventPublisher();
        WeaverToolRegistry registry = new WeaverToolRegistry(audit);

        var result = registry.invoke(new WeaverToolInvocationRequest(
                "calendar.search_events",
                "user:abc123",
                "sha256:eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee",
                List.of("weaver.calendar_read"),
                Map.of("query", "today"),
                null));

        assertThat(result.status()).isEqualTo("ok");
        assertThat(result.approvalRequired()).isFalse();
        assertThat(result.redactedResult()).containsEntry("approvalAuthority", "not_required");
    }

    @Test
    void failsClosedForServerPolicyConsentExpiryAndOverbroadGovernanceButNotRuntimeProfileMarkers() {
        InMemoryAuditEventPublisher audit = new InMemoryAuditEventPublisher();
        WeaverToolRegistry registry = new WeaverToolRegistry(audit);

        assertThat(registry.invoke(governedRequest("runtime_token_expired", signature(), false, Instant.now().minusSeconds(60).toString(), true, "user:abc123", List.of("boards.comment"))).status())
                .isEqualTo("runtime_token_expired");
        assertThat(registry.invoke(governedRequest("consent_required", signature(), false, future(), false, "user:abc123", List.of("boards.comment"))).status())
                .isEqualTo("consent_required");
        assertThat(registry.invoke(governedRequest("overbroad_grant", signature(), false, future(), true, "user:abc123", List.of("boards.*"))).status())
                .isEqualTo("overbroad_grant");

        assertThat(audit.events()).hasSize(3);
        assertThat(audit.events()).extracting(event -> event.payload().get("status"))
                .containsExactly(
                        "runtime_token_expired",
                        "consent_required",
                        "overbroad_grant");
        assertThat(audit.events()).allSatisfy(event -> {
            assertThat(event.action()).isEqualTo(AuditAction.WEAVER_TOOL_INVOCATION_RECORDED);
            assertThat(event.payload())
                    .containsEntry("supportSafe", true)
                    .containsEntry("runtimeProfileAuthority", "correlation_only")
                    .containsEntry("policyEnforcementPoint", "weave-mcp-server");
            assertThat(event.payload()).containsKeys("runtimeProfileHash", "user", "tool", "decision", "auditRef");
            assertThat(event.payload().toString()).doesNotContain("prompt", "Bearer ", "refresh_token", "providerRoom");
        });
    }

    @Test
    void runtimeProfileMarkersAreCorrelationOnlyAndCannotOverrideServerPolicyApproval() {
        InMemoryAuditEventPublisher audit = new InMemoryAuditEventPublisher();
        WeaverToolRegistry registry = new WeaverToolRegistry(audit);

        var unsignedMismatchedRevokedProfile = registry.invoke(governedRequest(
                "profile_marker_correlation_only",
                "",
                true,
                future(),
                true,
                "user:other",
                List.of("boards.comment")));

        assertThat(unsignedMismatchedRevokedProfile.status()).isEqualTo("approval_required");
        assertThat(unsignedMismatchedRevokedProfile.approvalRequired()).isTrue();
        assertThat(audit.events()).hasSize(1);
        assertThat(audit.events().get(0).payload())
                .containsEntry("status", "approval_required")
                .containsEntry("runtimeProfileAuthority", "correlation_only")
                .containsEntry("policyEnforcementPoint", "weave-mcp-server");
        assertThat(audit.events().get(0).payload().toString())
                .doesNotContain("runtime_profile_unsigned", "runtime_profile_user_mismatch", "runtime_profile_revoked");
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

    @Test
    void exposesWaveOneReadOnlyMcpFacadeToolsAndFailsClosedForUnknownTools() {
        InMemoryAuditEventPublisher audit = new InMemoryAuditEventPublisher();
        WeaverToolRegistry registry = new WeaverToolRegistry(audit);

        var tools = registry.discover(List.of(
                "registry.tools.read",
                "files.read",
                "calendar.read",
                "boards.read"));

        assertThat(tools).extracting(WeaverDomainToolDefinition::name).contains(
                "registry.tools.read",
                "files.read",
                "calendar.search_events",
                "boards.search_tasks");
        assertThat(tools.stream().filter(tool -> !tool.writeLike()))
                .allSatisfy(tool -> {
                    assertThat(tool.mode()).isEqualTo(WeaverToolMode.READ);
                    assertThat(tool.approvalRequirement()).isEqualTo(WeaverApprovalRequirement.NONE);
                });

        var unknown = registry.invoke(new WeaverToolInvocationRequest(
                "raw.unknown",
                "user:abc123",
                "sha256:unknown000000000000000000000000000000000000000000000000",
                "user:abc123",
                signature(),
                false,
                future(),
                true,
                List.of("raw.read"),
                List.of("raw.unknown"),
                Map.of("prompt", "do not leak"),
                null));

        assertThat(unknown.status()).isEqualTo("blocked");
        assertThat(unknown.supportSafeMessage()).doesNotContain("do not leak");
        assertThat(audit.events().get(0).payload()).containsEntry("reason", "not_granted");
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
                List.of("boards.update_task"),
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
