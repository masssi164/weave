package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.audit.InMemoryAuditEventPublisher;
import com.massimotter.weave.backend.config.WeaveSecurityProperties;
import com.massimotter.weave.backend.config.WeaverRuntimeProperties;
import com.massimotter.weave.backend.config.WorkspaceCapabilityProperties;
import com.massimotter.weave.backend.model.WorkspaceCapabilityReadiness;
import com.massimotter.weave.backend.weaver.MemberDomainToolDispatcher;
import com.massimotter.weave.backend.weaver.WeaverMcpApprovalReceiptService;
import com.massimotter.weave.backend.weaver.WeaverToolRegistry;
import com.massimotter.weave.contract.mcp.MemberMcpToolCatalog;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.ApprovalEvidence;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
                .containsExactly("files.search", "files.read", "calendar.search_events", "calendar.create_event", "chat.send_message");
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

        var response = fixture.bridge.invokeMcpTool(jwt(), MemberMcpToolCatalog.SERVER_NAMESPACE, "boards.search_tasks",
                request("boards.search_tasks", profile.runtimeProfileHash(), profile.userRef(), null, Map.of("taskRef", "task:1")));

        assertThat(response.status()).isEqualTo(ToolInvocationStatus.DENIED);
        assertThat(response.structuredContent()).containsEntry("supportSafe", true);
        assertThat(response.structuredContent()).containsEntry("approvalRequired", false);
        verifyNoInteractions(fixture.dispatcher);
    }

    @Test
    void readToolUsesMemberDomainDispatcherAndPreservesStructuredFields() {
        Fixture fixture = fixture();
        var profile = fixture.runtimeService.profileFor(jwt());
        when(fixture.dispatcher.dispatch(any(Jwt.class), eq("calendar.search_events"), eq(Map.of("from", "2026-06-17T00:00:00Z"))))
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
        verify(fixture.dispatcher).dispatch(any(Jwt.class), eq("calendar.search_events"), eq(Map.of("from", "2026-06-17T00:00:00Z")));
    }

    @Test
    void filesReadToolUsesMemberDomainDispatcherThroughWebdavBackedFacadeProjection() {
        Fixture fixture = fixture();
        var profile = fixture.runtimeService.profileFor(jwt());
        when(fixture.dispatcher.dispatch(any(Jwt.class), eq("files.read"), eq(Map.of("fileRef", "file:/Team/readme.md"))))
                .thenReturn(Map.of(
                        "status", "ok",
                        "supportSafe", true,
                        "dataPlane", "weave-webdav-facade",
                        "webDavFacadePath", "/dav/files",
                        "openApiDataPlaneUsed", false,
                        "item", Map.of("fileRef", "file:/Team/readme.md"),
                        "auditRef", "audit://files/read/support-safe",
                        "rawProviderPayload", "redacted"));

        var response = fixture.bridge.invokeMcpTool(jwt(), MemberMcpToolCatalog.SERVER_NAMESPACE, "files.read",
                request("files.read", profile.runtimeProfileHash(), profile.userRef(), null, Map.of("fileRef", "file:/Team/readme.md")));

        assertThat(response.status()).isEqualTo(ToolInvocationStatus.SUCCESS);
        assertThat(response.structuredContent().get("structuredContent").toString())
                .contains("weave-webdav-facade", "/dav/files", "openApiDataPlaneUsed=false")
                .doesNotContain("Nextcloud", "remote.php", "Bearer ");
        verify(fixture.dispatcher).dispatch(any(Jwt.class), eq("files.read"), eq(Map.of("fileRef", "file:/Team/readme.md")));
    }

    @Test
    void writeToolRequiresApprovalBeforeDispatch() {
        Fixture fixture = fixture();
        var profile = fixture.runtimeService.profileFor(jwt());
        Map<String, Object> arguments = Map.of(
                "title", "Planning",
                "startsAt", "2026-07-10T09:00:00Z",
                "calendarRef", "calendar:workspace");

        var response = fixture.bridge.invokeMcpTool(jwt(), MemberMcpToolCatalog.SERVER_NAMESPACE, "calendar.create_event",
                request("calendar.create_event", profile.runtimeProfileHash(), profile.userRef(), null, arguments));

        assertThat(response.status()).isEqualTo(ToolInvocationStatus.DENIED);
        assertThat(response.structuredContent()).containsEntry("approvalRequired", true);
        assertThat(response.structuredContent().toString()).contains("mcp_elicitation_missing");
        verifyNoInteractions(fixture.dispatcher);
    }

    @Test
    void directOidcCallerWithoutPrivateMcpBoundaryDoesNotAuthorizeWriteToolDispatch() {
        Fixture fixture = fixture();
        var profile = fixture.runtimeService.profileFor(jwt());
        Map<String, Object> arguments = Map.of(
                "title", "Planning",
                "startsAt", "2026-07-10T09:00:00Z",
                "calendarRef", "calendar:team:engineering");

        var response = fixture.bridge.invokeMcpTool(jwt(), MemberMcpToolCatalog.SERVER_NAMESPACE, "calendar.create_event",
                request(
                        "calendar.create_event",
                        profile.runtimeProfileHash(),
                        profile.userRef(),
                        new ApprovalEvidence(
                                "mcp-elicitation/v1",
                                "elicitation://openclaw/untrusted",
                                "calendar.create_event",
                                List.of("calendar:team:engineering"),
                                "allow-once",
                                Instant.now().toString()),
                        arguments),
                "wrong-boundary-token");

        assertThat(response.status()).isEqualTo(ToolInvocationStatus.DENIED);
        assertThat(response.structuredContent()).containsEntry("approvalRequired", true);
        assertThat(response.structuredContent().toString()).contains("mcp_boundary_untrusted");
        verifyNoInteractions(fixture.dispatcher);
    }

    @Test
    void unknownProviderShapedArgumentsFailBeforeGovernanceOrDomainDispatch() {
        Fixture fixture = fixture();
        var profile = fixture.runtimeService.profileFor(jwt());

        var response = fixture.bridge.invokeMcpTool(jwt(), MemberMcpToolCatalog.SERVER_NAMESPACE, "calendar.search_events",
                request(
                        "calendar.search_events",
                        profile.runtimeProfileHash(),
                        profile.userRef(),
                        null,
                        Map.of("providerUrl", "https://calendar.invalid/remote.php/dav")));

        assertThat(response.status()).isEqualTo(ToolInvocationStatus.VALIDATION_ERROR);
        assertThat(response.structuredContent().toString())
                .contains("supportSafe=true")
                .doesNotContain("remote.php", "calendar.invalid");
        verifyNoInteractions(fixture.dispatcher);
    }

    @Test
    void openClawElicitationEvidenceAuthorizesTheExactWriteOnce() {
        Fixture fixture = fixture();
        var profile = fixture.runtimeService.profileFor(jwt());
        Map<String, Object> arguments = Map.of(
                "title", "Planning",
                "startsAt", "2026-07-10T09:00:00Z",
                "calendarRef", "calendar:team:engineering");
        when(fixture.dispatcher.dispatch(any(Jwt.class), eq("calendar.create_event"), eq(arguments)))
                .thenReturn(Map.of(
                        "status", "ok",
                        "supportSafe", true,
                        "event", Map.of("id", "calendar-event:created"),
                        "auditRef", "audit://calendar/create/support-safe",
                        "rawProviderPayload", "redacted"));

        ApprovalEvidence evidence = new ApprovalEvidence(
                "mcp-elicitation/v1",
                "elicitation://openclaw/approval-1",
                "calendar.create_event",
                List.of("calendar:team:engineering"),
                "allow-once",
                Instant.now().toString());

        var response = fixture.bridge.invokeMcpTool(
                jwt(),
                MemberMcpToolCatalog.SERVER_NAMESPACE,
                "calendar.create_event",
                request("calendar.create_event", profile.runtimeProfileHash(), profile.userRef(), evidence, arguments));

        assertThat(response.status()).isEqualTo(ToolInvocationStatus.SUCCESS);
        assertThat(response.structuredContent().get("structuredContent").toString()).contains("calendar-event:created");
        verify(fixture.dispatcher).dispatch(any(Jwt.class), eq("calendar.create_event"), eq(arguments));

        var replay = fixture.bridge.invokeMcpTool(
                jwt(),
                MemberMcpToolCatalog.SERVER_NAMESPACE,
                "calendar.create_event",
                request("calendar.create_event", profile.runtimeProfileHash(), profile.userRef(), evidence, arguments));
        assertThat(replay.status()).isEqualTo(ToolInvocationStatus.DENIED);
        assertThat(replay.structuredContent().toString()).contains("mcp_elicitation_replayed");
    }

    private BridgeInvocationRequest request(
            String toolName,
            String profileHash,
            String userRef,
            ApprovalEvidence approvalEvidence,
            Map<String, Object> arguments) {
        return new BridgeInvocationRequest(toolName, arguments, new RuntimeInvocationContext(
                new WeaveMcpRef("org:workspace"),
                new WeaveMcpRef(userRef),
                new WeaveMcpRef("weave-runtime-profile://" + profileHash),
                profileHash,
                new WeaveMcpRef("credentialref://weave/runtime/short-lived/test"),
                "audit://test/bridge",
                List.of(),
                List.of()),
                approvalEvidence);
    }

    private Fixture fixture() {
        InMemoryAuditEventPublisher audit = new InMemoryAuditEventPublisher();
        MemberDomainToolDispatcher dispatcher = mock(MemberDomainToolDispatcher.class);
        WeaverRuntimeService runtimeService = runtimeService(audit);
        WeaverMcpBridgeService bridge = new WeaverMcpBridgeService(
                runtimeService,
                new WeaverToolRegistry(audit),
                dispatcher,
                new WeaverMcpApprovalReceiptService(audit));
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
                List.of("files.read", "calendar.read", "calendar.manage_events", "chat.send", "weaver.exec_disabled"),
                List.of(),
                List.of("files.search", "files.read", "calendar.search_events", "calendar.create_event", "chat.send_message"),
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
