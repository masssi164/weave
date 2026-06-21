package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.audit.InMemoryAuditEventPublisher;
import com.massimotter.weave.backend.config.WeaveSecurityProperties;
import com.massimotter.weave.backend.config.WeaverRuntimeProperties;
import com.massimotter.weave.backend.config.WorkspaceCapabilityProperties;
import com.massimotter.weave.backend.model.WorkspaceCapabilityReadiness;
import com.massimotter.weave.backend.weaver.MemberDomainToolDispatcher;
import com.massimotter.weave.backend.weaver.WeaverToolRegistry;
import com.massimotter.weave.contract.mcp.MemberMcpToolCatalog;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.ApprovalReceiptRef;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.BridgeInvocationRequest;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.RuntimeInvocationContext;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.ToolInvocationStatus;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.WeaveMcpRef;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WeaverMcpBridgeServiceTest {

    @Test
    void discoveryReturnsOnlyServerGrantedMemberRuntimeTools() {
        Fixture fixture = fixture();
        var profile = fixture.runtimeService.profileFor(jwt());

        var discovery = fixture.bridge.discoverMcpTools(jwt(), profile.runtimeProfileHash(), MemberMcpToolCatalog.SERVER_NAMESPACE);

        assertThat(discovery.catalog().serverNamespace()).isEqualTo(MemberMcpToolCatalog.SERVER_NAMESPACE);
        assertThat(discovery.catalog().tools()).extracting(tool -> tool.name())
                .containsExactly("calendar.search_events", "calendar.create_event");
        assertThat(discovery.catalog().tools()).allSatisfy(tool -> assertThat(tool.annotations().openWorldHint()).isFalse());
    }

    @Test
    void forbiddenAdminProviderAndControlPlaneToolsFailClosedBeforeDispatch() {
        Fixture fixture = fixture();
        var profile = fixture.runtimeService.profileFor(jwt());

        var response = fixture.bridge.invokeMcpTool(jwt(), MemberMcpToolCatalog.SERVER_NAMESPACE, "admin.readiness",
                request("admin.readiness", profile.runtimeProfileHash(), profile.userRef(), null, Map.of()));

        assertThat(response.status()).isEqualTo(ToolInvocationStatus.DENIED);
        assertThat(response.supportSafe()).isTrue();
        assertThat(response.structuredContent()).containsEntry("supportSafe", true);
        assertThat(response.structuredContent()).containsEntry("approvalRequired", false);
        verifyNoInteractions(fixture.dispatcher);
    }

    @Test
    void catalogMemberToolWithoutDispatcherFailsClosedBeforeGovernanceSuccess() {
        Fixture fixture = fixture();
        var profile = fixture.runtimeService.profileFor(jwt());

        var response = fixture.bridge.invokeMcpTool(jwt(), MemberMcpToolCatalog.SERVER_NAMESPACE, "files.read",
                request("files.read", profile.runtimeProfileHash(), profile.userRef(), null, Map.of("fileRef", "file:1")));

        assertThat(response.status()).isEqualTo(ToolInvocationStatus.DENIED);
        assertThat(response.structuredContent()).containsEntry("supportSafe", true);
        assertThat(response.structuredContent()).containsEntry("approvalRequired", false);
        verifyNoInteractions(fixture.dispatcher);
    }

    @Test
    void readToolUsesMemberDomainDispatcherAndPreservesStructuredFields() {
        Fixture fixture = fixture();
        var profile = fixture.runtimeService.profileFor(jwt());
        when(fixture.dispatcher.dispatch("calendar.search_events", Map.of("from", "2026-06-17T00:00:00Z")))
                .thenReturn(Map.of(
                        "status", "ok",
                        "supportSafe", true,
                        "events", List.of(Map.of("id", "calendar-event:1")),
                        "auditRef", "audit://calendar/search/support-safe",
                        "rawProviderPayload", "redacted"));

        var response = fixture.bridge.invokeMcpTool(jwt(), MemberMcpToolCatalog.SERVER_NAMESPACE, "calendar.search_events",
                request("calendar.search_events", profile.runtimeProfileHash(), profile.userRef(), null, Map.of("from", "2026-06-17T00:00:00Z")));

        assertThat(response.status()).isEqualTo(ToolInvocationStatus.SUCCESS);
        assertThat(response.auditRef()).isEqualTo("audit://calendar/search/support-safe");
        assertThat(response.structuredContent()).containsEntry("supportSafe", true);
        assertThat(response.structuredContent()).containsEntry("approvalRequired", false);
        assertThat(response.structuredContent().get("structuredContent").toString()).contains("calendar-event:1");
        assertThat(response.structuredContent().get("redactedContent").toString()).contains("redacted");
        assertThat(response.content()).singleElement().satisfies(block -> assertThat(block.text()).contains("Weave domain capability boundary"));
        verify(fixture.dispatcher).dispatch("calendar.search_events", Map.of("from", "2026-06-17T00:00:00Z"));
    }

    @Test
    void writeToolRequiresApprovalBeforeDispatch() {
        Fixture fixture = fixture();
        var profile = fixture.runtimeService.profileFor(jwt());

        var response = fixture.bridge.invokeMcpTool(jwt(), MemberMcpToolCatalog.SERVER_NAMESPACE, "calendar.create_event",
                request("calendar.create_event", profile.runtimeProfileHash(), profile.userRef(), null, Map.of("title", "Planning")));

        assertThat(response.status()).isEqualTo(ToolInvocationStatus.DENIED);
        assertThat(response.structuredContent()).containsEntry("approvalRequired", true);
        verifyNoInteractions(fixture.dispatcher);
    }

    @Test
    void approvedWriteToolDispatchesThroughMemberDomainFacadeBoundary() {
        Fixture fixture = fixture();
        var profile = fixture.runtimeService.profileFor(jwt());
        when(fixture.dispatcher.dispatch("calendar.create_event", Map.of("title", "Planning")))
                .thenReturn(Map.of(
                        "status", "ok",
                        "supportSafe", true,
                        "event", Map.of("id", "calendar-event:created"),
                        "auditRef", "audit://calendar/create/support-safe",
                        "rawProviderPayload", "redacted"));

        var response = fixture.bridge.invokeMcpTool(jwt(), MemberMcpToolCatalog.SERVER_NAMESPACE, "calendar.create_event",
                request("calendar.create_event", profile.runtimeProfileHash(), profile.userRef(), new ApprovalReceiptRef("approval://calendar-create/1"), Map.of("title", "Planning")));

        assertThat(response.status()).isEqualTo(ToolInvocationStatus.SUCCESS);
        assertThat(response.structuredContent().get("structuredContent").toString()).contains("calendar-event:created");
        verify(fixture.dispatcher).dispatch("calendar.create_event", Map.of("title", "Planning"));
    }

    private BridgeInvocationRequest request(String toolName, String profileHash, String userRef, ApprovalReceiptRef approvalReceiptRef, Map<String, Object> arguments) {
        return new BridgeInvocationRequest(toolName, arguments, new RuntimeInvocationContext(
                new WeaveMcpRef("org:workspace"),
                new WeaveMcpRef(userRef),
                new WeaveMcpRef("weave-runtime-profile://" + profileHash),
                profileHash,
                new WeaveMcpRef("credentialref://weave/runtime/short-lived/test"),
                "audit://test/bridge",
                approvalReceiptRef,
                null,
                List.of(),
                List.of()));
    }

    private Fixture fixture() {
        InMemoryAuditEventPublisher audit = new InMemoryAuditEventPublisher();
        MemberDomainToolDispatcher dispatcher = mock(MemberDomainToolDispatcher.class);
        WeaverRuntimeService runtimeService = runtimeService(audit);
        WeaverMcpBridgeService bridge = new WeaverMcpBridgeService(runtimeService, new WeaverToolRegistry(audit), dispatcher);
        return new Fixture(runtimeService, bridge, dispatcher);
    }

    private WeaverRuntimeService runtimeService(InMemoryAuditEventPublisher audit) {
        WorkspaceCapabilityProperties capabilities = new WorkspaceCapabilityProperties(
                new WorkspaceCapabilityProperties.Capability(true, null, null),
                new WorkspaceCapabilityProperties.Capability(true, "https://matrix.weave.test", WorkspaceCapabilityReadiness.READY),
                new WorkspaceCapabilityProperties.Capability(true, "https://files.weave.test", WorkspaceCapabilityReadiness.READY),
                new WorkspaceCapabilityProperties.Capability(true, null, WorkspaceCapabilityReadiness.READY),
                new WorkspaceCapabilityProperties.Capability(true, null, WorkspaceCapabilityReadiness.READY),
                new WorkspaceCapabilityProperties.Capability(true, null, WorkspaceCapabilityReadiness.READY));
        WeaverRuntimeProperties runtimeProperties = new WeaverRuntimeProperties(
                true,
                null,
                null,
                null,
                null,
                null,
                List.of("weave-weaver-runtime"),
                List.of("calendar.read", "calendar.manage_events", "weaver.exec_disabled"),
                List.of(),
                List.of("calendar.search_events", "calendar.create_event"),
                false,
                false,
                true,
                false);
        OAuth2ResourceServerProperties resourceServerProperties = new OAuth2ResourceServerProperties();
        resourceServerProperties.getJwt().setIssuerUri("https://auth.weave.test/realms/weave");
        WorkspaceCapabilityService capabilityService = new WorkspaceCapabilityService(
                resourceServerProperties,
                new WeaveSecurityProperties("weave-app", "weave-app"),
                capabilities,
                runtimeProperties);
        return new WeaverRuntimeService(capabilityService, capabilities, runtimeProperties, audit);
    }

    private Jwt jwt() {
        return new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(300),
                Map.of("alg", "none"),
                Map.of(
                        "sub", "member@example.invalid",
                        "iss", "https://auth.example.invalid/realms/acme",
                        "realm_access", Map.of("roles", List.of("member")),
                        "groups", List.of("weave-weaver-runtime", "weave-weaver-calendar")));
    }

    private record Fixture(
            WeaverRuntimeService runtimeService,
            WeaverMcpBridgeService bridge,
            MemberDomainToolDispatcher dispatcher) {}
}
