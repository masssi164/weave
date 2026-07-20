package com.massimotter.weave.backend.domainfacade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.massimotter.weave.backend.audit.InMemoryAuditEventPublisher;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationDecision;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationPort;
import com.massimotter.weave.backend.context.authz.ContextPermission;
import com.massimotter.weave.backend.model.WorkspaceCapabilitiesResponse;
import com.massimotter.weave.backend.model.WorkspaceCapabilityPolicyState;
import com.massimotter.weave.backend.model.WorkspaceCapabilityReadiness;
import com.massimotter.weave.backend.model.WorkspaceCapabilityStatusResponse;
import com.massimotter.weave.backend.provider.InMemoryProviderSelectionRepository;
import com.massimotter.weave.backend.provider.ProviderModule;
import com.massimotter.weave.backend.provider.ProviderPort;
import com.massimotter.weave.backend.provider.ProviderRegistry;
import com.massimotter.weave.backend.provider.ProviderSelection;
import com.massimotter.weave.backend.provider.ProviderSelectionRepository;
import com.massimotter.weave.backend.provider.ProviderState;
import com.massimotter.weave.backend.provider.ProviderStatusResponse;
import com.massimotter.weave.backend.provider.StaticProviderPort;
import com.massimotter.weave.backend.service.WorkspaceCapabilityService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.oauth2.jwt.Jwt;

class CanonicalDomainFacadeServicesTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-05-25T09:00:00Z"), ZoneOffset.UTC);

    @Test
    void contractsCoverRemainingNonChatDomainsWithoutThinProviderProxyVocabulary() {
        var services = services(new InMemoryProviderSelectionRepository(), configuredProviders(), allowedCapabilities());

        assertThat(services).extracting(service -> service.contract().domain())
                .containsExactly("files-docs", "calendar-meetings", "boards-tasks", "identity-admin-policy");

        for (CanonicalDomainFacade service : services) {
            CanonicalDomainContract contract = service.contract();
            assertThat(contract.contractVersion()).isEqualTo("canonical-domain-facade-v1");
            assertThat(contract.policyEvaluatedBeforeProviderAccess()).isTrue();
            assertThat(contract.unknownCapabilitiesFailClosed()).isTrue();
            assertThat(contract.normalMembersConfigureProviders()).isFalse();
            assertThat(contract.providerCategoryKeys()).isNotEmpty();
            assertThat(contract.canonicalObjectKinds()).isNotEmpty();
            assertThat(contract.adapterBoundaryOperations()).isNotEmpty();
            assertThat(contract.unsupportedUntilAdapterMapped()).isNotEmpty();
            assertThat(contract.toString().toLowerCase())
                    .doesNotContain("webdav", "caldav", "openproject", "keycloak", "nextcloud", "onlyoffice");
        }
    }

    @Test
    void contractsExposeSpaceCenteredWorkspaceFlowAcrossSuiteFacades() {
        var services = services(new InMemoryProviderSelectionRepository(), configuredProviders(), allowedCapabilities());

        CanonicalDomainContract filesDocs = contract(services, "files-docs");
        assertThat(filesDocs.adapterBoundaryOperations())
                .contains("read_space_scoped_file_refs", "link_document_to_space_context", "attach_file_ref_to_chat_or_task");
        assertThat(filesDocs.canonicalObjectKinds())
                .contains("space_ref", "chat_attachment_ref", "task_attachment_ref");

        CanonicalDomainContract calendarMeetings = contract(services, "calendar-meetings");
        assertThat(calendarMeetings.adapterBoundaryOperations())
                .contains("bind_event_to_space_context", "link_meeting_capsule_to_chat_thread", "read_space_scoped_agenda_refs");
        assertThat(calendarMeetings.canonicalObjectKinds())
                .contains("space_ref", "meeting_chat_ref", "agenda_ref");

        CanonicalDomainContract boardsTasks = contract(services, "boards-tasks");
        assertThat(boardsTasks.adapterBoundaryOperations())
                .contains("read_space_scoped_tasks", "link_task_to_chat_decision_or_file", "preview_space_task_write");
        assertThat(boardsTasks.canonicalObjectKinds())
                .contains("space_ref", "chat_ref", "file_ref", "decision_link");

        assertThat(List.of(filesDocs, calendarMeetings, boardsTasks))
                .allSatisfy(contract -> assertThat(contract.toString().toLowerCase())
                        .doesNotContain("matrix", "nextcloud", "webdav", "caldav", "openproject", "sharepoint", "onlyoffice"));
    }

    @Test
    void memberReadinessFailsClosedWithoutAdminSelectedMappingsAndHidesProviderDiagnostics() {
        var services = services(new InMemoryProviderSelectionRepository(), configuredProviders(), allowedCapabilities());

        for (CanonicalDomainFacade service : services) {
            CanonicalDomainReadiness readiness = service.memberReadiness(memberJwt());
            CanonicalDomainItems items = service.items(memberJwt());

            assertThat(readiness.memberState()).isEqualTo(CanonicalMemberState.MISCONFIGURED);
            assertThat(readiness.failClosed()).isTrue();
            assertThat(readiness.supportSafe()).isTrue();
            assertThat(readiness.memberClientMayConfigureProvider()).isFalse();
            assertThat(readiness.downstreamDiagnosticsExposedToMember()).isFalse();
            assertThat(readiness.rawProviderPayloadsReturned()).isFalse();
            assertThat(readiness.rawProviderErrorsReturned()).isFalse();
            assertThat(readiness.secretMaterialReturned()).isFalse();
            assertThat(readiness.providerMappings()).isEmpty();
            assertThat(readiness.supportSafeDiagnostics())
                    .containsEntry("diagnosticsExposed", false)
                    .containsEntry("secretsReturned", false)
                    .containsEntry("rawProviderErrorsReturned", false)
                    .containsEntry("downstreamPayloadsReturned", false);
            assertThat(items.items()).isEmpty();
            assertThat(readiness.toString())
                    .doesNotContain("https://", "Bearer ", "access_token", "secretref://", "downstream body");
        }
    }

    @Test
    void adminReadinessUsesSecretRefsOnlyAndSupportSafeMappingDiagnostics() {
        InMemoryProviderSelectionRepository selections = selectedMappings();
        var services = services(selections, configuredProviders(), allowedCapabilities());

        for (CanonicalDomainFacade service : services) {
            CanonicalDomainReadiness readiness = service.adminReadiness(adminJwt());

            assertThat(readiness.memberState()).isEqualTo(CanonicalMemberState.READY);
            assertThat(readiness.providerMappings()).isNotEmpty();
            assertThat(readiness.supportSafeDiagnostics())
                    .containsEntry("diagnosticsRedacted", true)
                    .containsEntry("secretRefOnly", true)
                    .containsEntry("rawProviderErrorsReturned", false);
            assertThat(readiness.providerMappings())
                    .allSatisfy(mapping -> {
                        assertThat(mapping.selectedByAdmin()).isTrue();
                        assertThat(mapping.configured()).isTrue();
                        assertThat(mapping.secretRefConfigured()).isTrue();
                        assertThat(mapping.secretMaterialReturned()).isFalse();
                        assertThat(mapping.downstreamPayloadsReturned()).isFalse();
                        assertThat(mapping.rawProviderErrorsReturned()).isFalse();
                        assertThat(mapping.supportSafeDiagnostics())
                                .containsEntry("secretRefConfigured", true)
                                .containsEntry("secretsReturned", false)
                                .containsEntry("downstreamPayloadsReturned", false);
                    });
            assertThat(readiness.toString()).doesNotContain("secretref://", "client-secret", "Bearer ", "access_token", "downstream body");
        }
    }

    @Test
    void policyBlockedReadinessSkipsProviderLookupAndFailsClosed() {
        AtomicInteger providerCalls = new AtomicInteger();
        ProviderPort explodingProvider = () -> {
            providerCalls.incrementAndGet();
            throw new AssertionError("provider status must not be read before policy allows the domain");
        };
        var service = filesDocs(new InMemoryProviderSelectionRepository(), List.of(explodingProvider), blockedCapabilities());

        CanonicalDomainReadiness readiness = service.memberReadiness(guestJwt());

        assertThat(readiness.memberState()).isEqualTo(CanonicalMemberState.POLICY_BLOCKED);
        assertThat(readiness.failClosed()).isTrue();
        assertThat(readiness.providerLookupPerformed()).isFalse();
        assertThat(readiness.supportSafeDiagnostics())
                .containsEntry("providerLookupPerformed", false)
                .containsEntry("policyEvaluatedBeforeProviderAccess", true)
                .containsEntry("secretsReturned", false)
                .containsEntry("rawProviderErrorsReturned", false);
        assertThat(providerCalls).hasValue(0);
    }

    @Test
    void unknownAndDenyByDefaultCapabilitiesFailClosedBeforeProviderAccess() {
        AtomicInteger providerCalls = new AtomicInteger();
        ProviderPort provider = () -> {
            providerCalls.incrementAndGet();
            return configuredProvider(ProviderModule.FILES, "generic-file-storage", Set.of("files.read"));
        };
        var service = filesDocs(new InMemoryProviderSelectionRepository(), List.of(provider), blockedCapabilities());

        CanonicalCapabilityDecision unknown = service.evaluateCapability(memberJwt(), "files.raw_provider_url", "open-raw-url");
        CanonicalCapabilityDecision denied = service.evaluateCapability(guestJwt(), "files.read", "list-files");

        assertThat(unknown.allowed()).isFalse();
        assertThat(unknown.state()).isEqualTo(CanonicalMemberState.UNSUPPORTED);
        assertThat(unknown.reason()).isEqualTo("unsupported_capability_fail_closed");
        assertThat(unknown.providerAccessAllowed()).isFalse();
        assertThat(unknown.supportSafeDiagnostics())
                .containsEntry("knownCapability", false)
                .containsEntry("providerLookupPerformed", false);
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.state()).isEqualTo(CanonicalMemberState.POLICY_BLOCKED);
        assertThat(denied.reason()).isEqualTo("capability_policy_blocked");
        assertThat(denied.providerAccessAllowed()).isFalse();
        assertThat(providerCalls).hasValue(0);
    }

    @Test
    void nonChatOperationGuardEnforcesCapabilityContextAuditAndCanonicalMappingBeforeProviderAccess() {
        InMemoryAuditEventPublisher audit = new InMemoryAuditEventPublisher();
        var guard = new NonChatDomainFacadeOperationGuard(
                filesDocs(selectedMappings(), configuredProviders(), allowedCapabilities()),
                allowContext(),
                audit,
                FIXED);

        NonChatDomainFacadeOperationDecision decision = guard.decide(memberJwt(), operation("files.upload", true, false));

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.providerAccessAllowed()).isTrue();
        assertThat(decision.audited()).isTrue();
        assertThat(decision.error()).isNull();
        assertThat(decision.canonicalObjectRef()).isEqualTo("file:weave:space-alpha:proposal.md");
        assertThat(decision.provenanceRef()).isEqualTo("mapping:files:space-alpha:proposal.md");
        assertThat(decision.supportSafeDiagnostics())
                .containsEntry("policyEvaluatedBeforeProviderAccess", true)
                .containsEntry("secretsReturned", false)
                .containsEntry("rawProviderPayloadsReturned", false)
                .containsEntry("rawProviderErrorsReturned", false);
        assertThat(audit.events()).hasSize(1);
        assertThat(audit.events().getFirst().payload())
                .containsEntry("domain", "files-docs")
                .containsEntry("canonicalObjectRef", "file:weave:space-alpha:proposal.md")
                .containsEntry("provenanceRef", "mapping:files:space-alpha:proposal.md");
        assertThat(audit.events().getFirst().toString()).doesNotContain("https://", "Bearer ", "access_token", "provider:");
    }

    @Test
    void nonChatOperationGuardFailsClosedForPolicyOrContextAndDoesNotAuditDeniedWrites() {
        InMemoryAuditEventPublisher audit = new InMemoryAuditEventPublisher();
        var policyGuard = new NonChatDomainFacadeOperationGuard(
                filesDocs(selectedMappings(), configuredProviders(), blockedCapabilities()),
                allowContext(),
                audit,
                FIXED);
        var contextGuard = new NonChatDomainFacadeOperationGuard(
                filesDocs(selectedMappings(), configuredProviders(), allowedCapabilities()),
                denyContext(),
                audit,
                FIXED);

        NonChatDomainFacadeOperationDecision policyDenied = policyGuard.decide(guestJwt(), operation("files.upload", true, false));
        NonChatDomainFacadeOperationDecision contextDenied = contextGuard.decide(memberJwt(), operation("files.upload", true, false));

        assertThat(policyDenied.allowed()).isFalse();
        assertThat(policyDenied.providerAccessAllowed()).isFalse();
        assertThat(policyDenied.error()).isEqualTo(SupportSafeFacadeError.POLICY_BLOCKED);
        assertThat(contextDenied.allowed()).isFalse();
        assertThat(contextDenied.providerAccessAllowed()).isFalse();
        assertThat(contextDenied.error()).isEqualTo(SupportSafeFacadeError.CONTEXT_FORBIDDEN);
        assertThat(audit.events()).isEmpty();
    }

    @Test
    void nonChatOperationGuardSupportsDryRunReplacementPreviewWithoutAuditForReads() {
        InMemoryAuditEventPublisher audit = new InMemoryAuditEventPublisher();
        var guard = new NonChatDomainFacadeOperationGuard(
                filesDocs(selectedMappings(), configuredProviders(), allowedCapabilities()),
                allowContext(),
                audit,
                FIXED);

        NonChatDomainFacadeOperationDecision decision = guard.decide(memberJwt(), operation("files.read", false, true));

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.dryRun()).isTrue();
        assertThat(decision.reason()).isEqualTo("dry_run_allowed");
        assertThat(decision.audited()).isFalse();
        assertThat(audit.events()).isEmpty();
    }

    private List<CanonicalDomainFacade> services(
            ProviderSelectionRepository selections,
            List<ProviderPort> providers,
            WorkspaceCapabilitiesResponse capabilities) {
        WorkspaceCapabilityService capabilityService = capabilityService(capabilities);
        ProviderRegistry registry = new ProviderRegistry(providers, capabilityService, selections);
        return List.of(
                new FilesDocsDomainFacadeService(registry, selections, capabilityService, FIXED),
                new CalendarMeetingsDomainFacadeService(registry, selections, capabilityService, FIXED),
                new BoardsTasksDomainFacadeService(registry, selections, capabilityService, FIXED),
                new IdentityAdminPolicyDomainFacadeService(registry, selections, capabilityService, FIXED));
    }

    private CanonicalDomainContract contract(List<CanonicalDomainFacade> services, String domain) {
        return services.stream()
                .map(CanonicalDomainFacade::contract)
                .filter(contract -> contract.domain().equals(domain))
                .findFirst()
                .orElseThrow();
    }

    private FilesDocsDomainFacadeService filesDocs(
            ProviderSelectionRepository selections,
            List<ProviderPort> providers,
            WorkspaceCapabilitiesResponse capabilities) {
        WorkspaceCapabilityService capabilityService = capabilityService(capabilities);
        ProviderRegistry registry = new ProviderRegistry(providers, capabilityService, selections);
        return new FilesDocsDomainFacadeService(registry, selections, capabilityService, FIXED);
    }

    private ContextAuthorizationPort allowContext() {
        return request -> ContextAuthorizationDecision.allow("test context grants " + request.permission());
    }

    private ContextAuthorizationPort denyContext() {
        return request -> ContextAuthorizationDecision.deny("test context denies");
    }

    private NonChatDomainFacadeOperationRequest operation(String capability, boolean writeOrDelete, boolean dryRun) {
        return new NonChatDomainFacadeOperationRequest(
                "weave-dogfood",
                "space-alpha",
                "user:member-123",
                capability,
                writeOrDelete ? "upload_file" : "preview_file_mapping",
                writeOrDelete ? ContextPermission.EDIT : ContextPermission.VIEW,
                writeOrDelete,
                "file:weave:space-alpha:proposal.md",
                "mapping:files:space-alpha:proposal.md",
                dryRun);
    }

    private WorkspaceCapabilityService capabilityService(WorkspaceCapabilitiesResponse capabilities) {
        WorkspaceCapabilityService service = Mockito.mock(WorkspaceCapabilityService.class);
        when(service.snapshot()).thenReturn(capabilities);
        when(service.snapshot(any())).thenReturn(capabilities);
        return service;
    }

    private WorkspaceCapabilitiesResponse allowedCapabilities() {
        return new WorkspaceCapabilitiesResponse(
                capability("identity/IDM", List.of("identity.sign_in", "identity.groups", "identity.roles", "policy.read"), WorkspaceCapabilityPolicyState.ALLOWED),
                capability("chat", List.of("chat.read"), WorkspaceCapabilityPolicyState.ALLOWED),
                capability("files", List.of("files.read", "files.upload", "documents.view", "documents.edit"), WorkspaceCapabilityPolicyState.ALLOWED),
                capability("calendar", List.of("calendar.read", "calendar.manage_events", "meetings.join"), WorkspaceCapabilityPolicyState.ALLOWED),
                capability("boards/tasks", List.of("boards.read", "boards.update_task"), WorkspaceCapabilityPolicyState.ALLOWED),
                capability("Agent Runtime Control", List.of("agent-runtime.entitled"), WorkspaceCapabilityPolicyState.ALLOWED));
    }

    private WorkspaceCapabilitiesResponse blockedCapabilities() {
        return new WorkspaceCapabilitiesResponse(
                capability("identity/IDM", List.of(), WorkspaceCapabilityPolicyState.POLICY_BLOCKED),
                capability("chat", List.of(), WorkspaceCapabilityPolicyState.POLICY_BLOCKED),
                capability("files", List.of(), WorkspaceCapabilityPolicyState.POLICY_BLOCKED),
                capability("calendar", List.of(), WorkspaceCapabilityPolicyState.POLICY_BLOCKED),
                capability("boards/tasks", List.of(), WorkspaceCapabilityPolicyState.POLICY_BLOCKED),
                capability("Agent Runtime Control", List.of(), WorkspaceCapabilityPolicyState.POLICY_BLOCKED));
    }

    private WorkspaceCapabilityStatusResponse capability(
            String profile,
            List<String> granted,
            WorkspaceCapabilityPolicyState policyState) {
        return new WorkspaceCapabilityStatusResponse(
                policyState != WorkspaceCapabilityPolicyState.DISABLED,
                policyState == WorkspaceCapabilityPolicyState.POLICY_BLOCKED
                        ? WorkspaceCapabilityReadiness.BLOCKED
                        : WorkspaceCapabilityReadiness.READY,
                policyState,
                profile,
                "Ready through Weave policy.",
                granted);
    }

    private InMemoryProviderSelectionRepository selectedMappings() {
        InMemoryProviderSelectionRepository selections = new InMemoryProviderSelectionRepository();
        selections.save(selection("files", "generic-file-storage"));
        selections.save(selection("documents-collaboration", "generic-document-session"));
        selections.save(selection("calendar", "generic-calendar"));
        selections.save(selection("meetings-calls", "generic-meetings"));
        selections.save(selection("boards-tasks", "generic-boards"));
        selections.save(selection("identity-idm", "generic-identity"));
        return selections;
    }

    private ProviderSelection selection(String category, String providerKey) {
        return new ProviderSelection(
                category,
                providerKey,
                "external_existing_provider",
                "secretref://weave/provider/" + providerKey,
                "actor:test-admin",
                Instant.parse("2026-05-24T18:00:00Z"),
                true,
                true,
                false,
                List.of("Provider-specific fields are normalized into Weave annotations."));
    }

    private List<ProviderPort> configuredProviders() {
        return List.of(
                new StaticProviderPort(configuredProvider(ProviderModule.FILES, "generic-file-storage", Set.of("files.read", "files.upload"))),
                new StaticProviderPort(configuredProvider(ProviderModule.OFFICE, "generic-document-session", Set.of("documents.view", "documents.edit"))),
                new StaticProviderPort(configuredProvider(ProviderModule.CALENDAR, "generic-calendar", Set.of("calendar.read", "calendar.manage_events"))),
                new StaticProviderPort(configuredProvider(ProviderModule.MEETINGS, "generic-meetings", Set.of("meetings.join", "meetings.host"))),
                new StaticProviderPort(configuredProvider(ProviderModule.BOARDS, "generic-boards", Set.of("boards.read", "boards.update_task"))),
                new StaticProviderPort(configuredProvider(ProviderModule.IDENTITY_REALM, "generic-identity", Set.of("identity.sign_in", "identity.groups", "identity.roles"))));
    }

    private ProviderStatusResponse configuredProvider(ProviderModule module, String key, Set<String> capabilities) {
        return new ProviderStatusResponse(
                module,
                key,
                ProviderState.CONFIGURED,
                "configured",
                true,
                true,
                true,
                true,
                true,
                false,
                "Canonical domain adapter seam is configured with support-safe diagnostics.",
                capabilities,
                Set.of("direct-member-provider-api", "raw-provider-errors", "credential-exposure"),
                List.of("provider-not-configured", "provider-disabled", "unsupported-capability"),
                "support-safe redaction policy",
                List.of(key),
                Map.of("secretsReturned", false, "rawProviderErrorsReturned", false, "downstreamPayloadsReturned", false));
    }

    private Jwt memberJwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("member-123")
                .claim("weave_tenant", "weave-dogfood")
                .claim("resource_access", Map.of("weave-app", Map.of("roles", List.of("member"))))
                .build();
    }

    private Jwt adminJwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("admin-123")
                .claim("weave_tenant", "weave-dogfood")
                .claim("resource_access", Map.of("weave-app", Map.of("roles", List.of("admin"))))
                .build();
    }

    private Jwt guestJwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("guest-123")
                .claim("weave_tenant", "weave-dogfood")
                .claim("resource_access", Map.of("weave-app", Map.of("roles", List.of("guest"))))
                .build();
    }
}
