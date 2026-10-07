package com.massimotter.weave.backend.chat;

import com.massimotter.weave.backend.support.HumanJwtTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.InMemoryAuditEventPublisher;
import com.massimotter.weave.backend.chat.adapter.WeaveCanonicalChatAdapter;
import com.massimotter.weave.backend.chat.domain.ChatCursor;
import com.massimotter.weave.backend.chat.domain.ChatAccessDeniedException;
import com.massimotter.weave.backend.chat.domain.ChatMemberState;
import com.massimotter.weave.backend.chat.domain.ChatMigrationPreflightRequest;
import com.massimotter.weave.backend.chat.domain.ChatRequestContext;
import com.massimotter.weave.backend.chat.port.ChatProviderPort;
import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationDecision;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationPort;
import com.massimotter.weave.backend.identity.IdentityReferences;
import com.massimotter.weave.backend.model.WorkspaceCapabilitiesResponse;
import com.massimotter.weave.backend.model.WorkspaceCapabilityPolicyState;
import com.massimotter.weave.backend.model.WorkspaceCapabilityReadiness;
import com.massimotter.weave.backend.model.WorkspaceCapabilityStatusResponse;
import com.massimotter.weave.backend.provider.InMemoryProviderSelectionRepository;
import com.massimotter.weave.backend.provider.ProviderModule;
import com.massimotter.weave.backend.provider.ProviderRegistry;
import com.massimotter.weave.backend.provider.ProviderSelection;
import com.massimotter.weave.backend.provider.ProviderSelectionRepository;
import com.massimotter.weave.backend.provider.ProviderState;
import com.massimotter.weave.backend.provider.ProviderStatusResponse;
import com.massimotter.weave.backend.provider.StaticProviderPort;
import com.massimotter.weave.backend.portability.ProviderReadiness;
import com.massimotter.weave.backend.service.WorkspaceCapabilityService;
import com.massimotter.weave.backend.service.OrganizationIdentityContextResolver;
import com.massimotter.weave.backend.spaces.port.SpaceAccessPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.oauth2.jwt.Jwt;

class ChatDomainFacadeServiceTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-05-25T08:00:00Z"), ZoneOffset.UTC);

    @Test
    void aHealthyLegacySelectionCannotAdmitChatWithoutTheOrganizationBinding() {
        InMemoryProviderSelectionRepository selections = new InMemoryProviderSelectionRepository();
        selections.save(selection("chat", "synapse-homeserver", false, List.of()));
        WorkspaceCapabilityService capabilities = Mockito.mock(WorkspaceCapabilityService.class);
        WorkspaceCapabilitiesResponse snapshot = new WorkspaceCapabilitiesResponse(
                capability(), capability(), capability(), capability(), capability(), capability());
        when(capabilities.snapshot()).thenReturn(snapshot);
        when(capabilities.snapshot(any())).thenReturn(snapshot);
        ChatProviderPort provider = Mockito.mock(ChatProviderPort.class);
        when(provider.configured()).thenReturn(true);
        when(provider.providerSelectionKeys()).thenReturn(Set.of("synapse-homeserver"));
        when(provider.readiness()).thenReturn(ProviderReadiness.ready("chat-provider-ready"));
        ChatProviderBindingGate bindingGate = Mockito.mock(ChatProviderBindingGate.class);
        ChatDomainFacadeService service = new ChatDomainFacadeService(
                new ProviderRegistry(List.of(chatProvider(true)), capabilities, selections),
                selections, capabilities, new InMemoryAuditEventPublisher(), provider,
                allowAllContexts(), contextProperties(),
                com.massimotter.weave.backend.service.OrganizationIdentityContextResolver.configured(contextProperties()),
                FIXED, bindingGate);

        assertThat(service.memberReadiness(memberJwt()).memberState()).isEqualTo(ChatMemberState.MISCONFIGURED);
        assertThat(service.syncCursor(memberJwt())).isEqualTo("chat-unavailable");
        verify(provider, never()).currentCursor(any(ChatRequestContext.class));
    }

    @Test
    void memberReadinessFailsClosedWithoutAdminSelectedChatMapping() {
        ChatDomainFacadeService service = service(new InMemoryProviderSelectionRepository(), false, capability());

        var readiness = service.memberReadiness(memberJwt());
        var conversations = service.conversations(memberJwt());

        assertThat(readiness.contractVersion()).isEqualTo("chat-domain-facade-v1");
        assertThat(readiness.memberState()).isEqualTo(ChatMemberState.MISCONFIGURED);
        assertThat(readiness.failClosed()).isTrue();
        assertThat(readiness.memberClientMayConfigureProvider()).isFalse();
        assertThat(readiness.downstreamDiagnosticsExposedToMember()).isFalse();
        assertThat(readiness.providerMapping()).isNull();
        assertThat(readiness.supportSafeDiagnostics())
                .containsEntry("diagnosticsExposed", false)
                .containsEntry("downstreamErrorsReturned", false)
                .containsEntry("secretsReturned", false);
        assertThat(conversations.conversations()).isEmpty();
    }

    @Test
    void adminSelectionUnlocksConversationCreationWithoutBypassingReadiness() {
        InMemoryProviderSelectionRepository selections = new InMemoryProviderSelectionRepository();
        ChatDomainFacadeService service = service(selections, true, capability());

        assertThatThrownBy(() -> service.createConversation(
                "create-before-selection",
                "Blocked conversation",
                "channel",
                List.of(),
                memberJwt()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Chat is not ready.");

        selections.save(selection("chat", "in-memory-test", false, List.of()));

        var created = service.createConversation(
                "create-after-selection",
                "Selected conversation",
                "channel",
                List.of(),
                memberJwt());

        assertThat(created.title()).isEqualTo("Selected conversation");
        assertThat(service.memberReadiness(memberJwt()).memberState()).isEqualTo(ChatMemberState.READY);
    }

    @Test
    void adminReadinessShowsSupportSafeMappingWhileSelectedProviderIsUnconfigured() {
        InMemoryProviderSelectionRepository selections = new InMemoryProviderSelectionRepository();
        selections.save(selection("chat", "slack", true, List.of("Thread replies need Weave annotations.")));
        ChatDomainFacadeService service = service(selections, false, capability());

        var readiness = service.adminReadiness(adminJwt());

        assertThat(readiness.memberState()).isEqualTo(ChatMemberState.MISCONFIGURED);
        assertThat(readiness.providerMapping()).isNotNull();
        assertThat(readiness.providerMapping().selectedProviderKey()).isEqualTo("slack");
        assertThat(readiness.providerMapping().selectedByAdmin()).isTrue();
        assertThat(readiness.providerMapping().configured()).isFalse();
        assertThat(readiness.providerMapping().secretsReturned()).isFalse();
        assertThat(readiness.providerMapping().downstreamErrorsReturned()).isFalse();
        assertThat(readiness.providerMapping().lossyMappingWarnings()).contains("Thread replies need Weave annotations.");
        assertThat(readiness.supportSafeDiagnostics())
                .containsEntry("missingConfigurationCategory", "backend_provider_configuration")
                .containsEntry("currentRealProviderPath", "in-memory-test")
                .containsEntry("contractOnlyChatProviders", List.of("microsoft-teams", "slack", "nextcloud-talk"))
                .containsEntry("diagnosticsRedacted", true);
        assertThat(readiness.toString()).doesNotContain("Bear" + "er ", "access_token", "xoxb-");
    }

    @Test
    void readyProviderUsesCanonicalProviderPortCollections() {
        InMemoryProviderSelectionRepository selections = new InMemoryProviderSelectionRepository();
        selections.save(selection("chat", "in-memory-test", false, List.of()));
        ChatDomainFacadeService service = service(selections, true, capability());

        var conversations = service.conversations(memberJwt());

        assertThat(conversations.readiness().memberState()).isEqualTo(ChatMemberState.READY);
        assertThat(conversations.conversations())
                .extracting(conversation -> conversation.conversationId())
                .containsExactly("channel-general");
        assertThat(conversations.readiness().defaultHistoryPolicy().visibility()).isEqualTo("conversation_members");
        assertThat(service.adminReadiness(adminJwt()).supportSafeDiagnostics())
                .containsEntry("currentRealProviderPath", "in-memory-test")
                .containsEntry("currentRealProviderAliases", List.of("in-memory-test"));
        assertThat(conversations.toString()).doesNotContain("rawProvider", "Authorization");
    }

    @Test
    void spaceRoomProjectionUsesExplicitCurrentContextAuthorization() {
        InMemoryProviderSelectionRepository selections = new InMemoryProviderSelectionRepository();
        selections.save(selection("chat", "synapse-homeserver", false, List.of()));
        WorkspaceCapabilityService capabilities = Mockito.mock(WorkspaceCapabilityService.class);
        WorkspaceCapabilitiesResponse snapshot = new WorkspaceCapabilitiesResponse(
                capability(), capability(), capability(), capability(), capability(), capability());
        when(capabilities.snapshot()).thenReturn(snapshot);
        when(capabilities.snapshot(any())).thenReturn(snapshot);
        ChatProviderPort provider = Mockito.mock(ChatProviderPort.class);
        when(provider.configured()).thenReturn(true);
        when(provider.providerSelectionKeys()).thenReturn(Set.of("synapse-homeserver"));
        when(provider.readiness()).thenReturn(ProviderReadiness.ready("chat-provider-ready"));
        when(provider.joinedConversationRefs(any(ChatRequestContext.class), eq(""), eq(10)))
                .thenReturn(List.of("canonical-room"));
        ContextAuthorizationPort spaceOnly = request -> request.contextId().equals("chosen-space")
                ? ContextAuthorizationDecision.allow("current Space grant")
                : ContextAuthorizationDecision.deny("no current Space grant");
        ChatDomainFacadeService service = new ChatDomainFacadeService(
                new ProviderRegistry(List.of(chatProvider(true)), capabilities, selections),
                selections, capabilities, new InMemoryAuditEventPublisher(), provider,
                spaceOnly, contextProperties(), FIXED);

        assertThat(service.joinedConversationRefsInSpace(memberJwt(), "chosen-space", "", 10))
                .containsExactly("canonical-room");
        ArgumentCaptor<ChatRequestContext> context = ArgumentCaptor.forClass(ChatRequestContext.class);
        verify(provider).joinedConversationRefs(context.capture(), eq(""), eq(10));
        assertThat(context.getValue().contextId()).isEqualTo("chosen-space");
        assertThat(context.getValue().actorRef().value()).isEqualTo("user:member-123");
        assertThatThrownBy(() -> service.joinedConversationRefsInSpace(memberJwt(),
                "different-space", "", 10)).isInstanceOf(ChatAccessDeniedException.class);
    }

    @Test
    void productionChatSpaceAdmissionFollowsDurableGrantAndRevocation() {
        InMemoryProviderSelectionRepository selections = new InMemoryProviderSelectionRepository();
        selections.save(selection("chat", "synapse-homeserver", false, List.of()));
        WorkspaceCapabilityService capabilities = Mockito.mock(WorkspaceCapabilityService.class);
        WorkspaceCapabilitiesResponse snapshot = new WorkspaceCapabilitiesResponse(
                capability(), capability(), capability(), capability(), capability(), capability());
        when(capabilities.snapshot()).thenReturn(snapshot);
        when(capabilities.snapshot(any())).thenReturn(snapshot);
        ChatProviderPort provider = Mockito.mock(ChatProviderPort.class);
        when(provider.configured()).thenReturn(true);
        when(provider.providerSelectionKeys()).thenReturn(Set.of("synapse-homeserver"));
        when(provider.readiness()).thenReturn(ProviderReadiness.ready("chat-provider-ready"));
        when(provider.joinedConversationRefs(any(ChatRequestContext.class), eq(""), eq(10)))
                .thenReturn(List.of("canonical-room"));
        SpaceAccessPort spaces = Mockito.mock(SpaceAccessPort.class);
        String account = IdentityReferences.accountId("https://auth.example/realms/weave", "member-123");
        when(spaces.allows("weave-dogfood", "chosen-space", account,
                SpaceAccessPort.Permission.VIEW)).thenReturn(true, false);
        ChatDomainFacadeService service = new ChatDomainFacadeService(
                new ProviderRegistry(List.of(chatProvider(true)), capabilities, selections),
                selections, capabilities, new InMemoryAuditEventPublisher(), provider,
                allowAllContexts(), contextProperties(),
                OrganizationIdentityContextResolver.configured(contextProperties()), FIXED,
                new DurableChatSpaceAccess(spaces));

        assertThat(service.joinedConversationRefsInSpace(memberJwt(), "chosen-space", "", 10))
                .containsExactly("canonical-room");
        assertThatThrownBy(() -> service.joinedConversationRefsInSpace(memberJwt(),
                "chosen-space", "", 10)).isInstanceOf(ChatAccessDeniedException.class);
        verify(provider, times(1)).joinedConversationRefs(any(ChatRequestContext.class), eq(""), eq(10));
    }

    @Test
    void matrixRoomReadbackResolvesJoinedSpaceFromCanonicalStoreNotTokenContext() {
        InMemoryProviderSelectionRepository selections = new InMemoryProviderSelectionRepository();
        selections.save(selection("chat", "synapse-homeserver", false, List.of()));
        WorkspaceCapabilityService capabilities = Mockito.mock(WorkspaceCapabilityService.class);
        WorkspaceCapabilitiesResponse snapshot = new WorkspaceCapabilitiesResponse(
                capability(), capability(), capability(), capability(), capability(), capability());
        when(capabilities.snapshot()).thenReturn(snapshot);
        when(capabilities.snapshot(any())).thenReturn(snapshot);
        ChatProviderPort provider = Mockito.mock(ChatProviderPort.class);
        when(provider.configured()).thenReturn(true);
        when(provider.providerSelectionKeys()).thenReturn(Set.of("synapse-homeserver"));
        when(provider.readiness()).thenReturn(ProviderReadiness.ready("chat-provider-ready"));
        when(provider.memberConversationContext(any(ChatRequestContext.class), any()))
                .thenReturn(java.util.Optional.of("chosen-space"));
        var room = Mockito.mock(com.massimotter.weave.backend.chat.domain.ChatConversation.class);
        when(room.conversationId()).thenReturn("canonical-room");
        when(provider.conversation(any(ChatRequestContext.class), any())).thenReturn(room);
        SpaceAccessPort spaces = Mockito.mock(SpaceAccessPort.class);
        String account = IdentityReferences.accountId("https://auth.example/realms/weave", "member-123");
        when(spaces.allows("weave-dogfood", "chosen-space", account,
                SpaceAccessPort.Permission.VIEW)).thenReturn(true, false);
        ChatDomainFacadeService service = new ChatDomainFacadeService(
                new ProviderRegistry(List.of(chatProvider(true)), capabilities, selections),
                selections, capabilities, new InMemoryAuditEventPublisher(), provider,
                allowAllContexts(), contextProperties(),
                OrganizationIdentityContextResolver.configured(contextProperties()), FIXED,
                new DurableChatSpaceAccess(spaces));

        assertThat(service.conversation("canonical-room", memberJwt()).conversationId())
                .isEqualTo("canonical-room");
        ArgumentCaptor<ChatRequestContext> context = ArgumentCaptor.forClass(ChatRequestContext.class);
        verify(provider).conversation(context.capture(), any());
        assertThat(context.getValue().contextId()).isEqualTo("chosen-space");
        assertThatThrownBy(() -> service.conversation("canonical-room", memberJwt()))
                .isInstanceOf(ChatAccessDeniedException.class);
        verify(provider, times(1)).conversation(any(ChatRequestContext.class), any());
    }

    @Test
    void matrixSyncProjectsOnlyRoomsInCurrentlyVisibleDurableSpaces() {
        InMemoryProviderSelectionRepository selections = new InMemoryProviderSelectionRepository();
        selections.save(selection("chat", "synapse-homeserver", false, List.of()));
        WorkspaceCapabilityService capabilities = Mockito.mock(WorkspaceCapabilityService.class);
        WorkspaceCapabilitiesResponse snapshot = new WorkspaceCapabilitiesResponse(
                capability(), capability(), capability(), capability(), capability(), capability());
        when(capabilities.snapshot()).thenReturn(snapshot);
        when(capabilities.snapshot(any())).thenReturn(snapshot);
        ChatProviderPort provider = Mockito.mock(ChatProviderPort.class);
        when(provider.configured()).thenReturn(true);
        when(provider.providerSelectionKeys()).thenReturn(Set.of("synapse-homeserver"));
        when(provider.readiness()).thenReturn(ProviderReadiness.ready("chat-provider-ready"));
        when(provider.joinedConversationRefs(any(ChatRequestContext.class), eq(""), eq(100)))
                .thenAnswer(call -> List.of("room-" + ((ChatRequestContext) call.getArgument(0)).contextId()));
        when(provider.conversation(any(ChatRequestContext.class), any()))
                .thenAnswer(call -> {
                    var room = Mockito.mock(com.massimotter.weave.backend.chat.domain.ChatConversation.class);
                    when(room.conversationId()).thenReturn(((com.massimotter.weave.backend.chat.domain.ConversationId)
                            call.getArgument(1)).value());
                    return room;
                });
        when(provider.currentCursor(any(ChatRequestContext.class)))
                .thenAnswer(call -> new ChatCursor("chat-revision-"
                        + ("beta".equals(((ChatRequestContext) call.getArgument(0)).contextId()) ? 7 : 2)));
        SpaceAccessPort spaces = Mockito.mock(SpaceAccessPort.class);
        String account = IdentityReferences.accountId("https://auth.example/realms/weave", "member-123");
        when(spaces.visibleSpaceRefs("weave-dogfood", account, "", 100))
                .thenReturn(List.of("alpha", "beta"));
        when(spaces.allows("weave-dogfood", "alpha", account, SpaceAccessPort.Permission.VIEW))
                .thenReturn(true);
        when(spaces.allows("weave-dogfood", "beta", account, SpaceAccessPort.Permission.VIEW))
                .thenReturn(true);
        ChatDomainFacadeService service = new ChatDomainFacadeService(
                new ProviderRegistry(List.of(chatProvider(true)), capabilities, selections),
                selections, capabilities, new InMemoryAuditEventPublisher(), provider,
                allowAllContexts(), contextProperties(),
                OrganizationIdentityContextResolver.configured(contextProperties()), FIXED,
                new DurableChatSpaceAccess(spaces));

        assertThat(service.conversations(memberJwt()).conversations())
                .extracting(value -> value.conversationId()).containsExactly("room-alpha", "room-beta");
        assertThat(service.syncCursor(memberJwt())).isEqualTo("chat-revision-7");

        when(spaces.allows("weave-dogfood", "beta", account, SpaceAccessPort.Permission.VIEW))
                .thenReturn(false);
        assertThat(service.conversations(memberJwt()).conversations())
                .extracting(value -> value.conversationId()).containsExactly("room-alpha");
        assertThat(service.syncCursor(memberJwt())).isEqualTo("chat-revision-2");
    }

    @Test
    void canonicalContextUsesTheKeycloakIssuerAndPrimaryTenantClaim() {
        InMemoryProviderSelectionRepository selections = new InMemoryProviderSelectionRepository();
        selections.save(selection("chat", "synapse-homeserver", false, List.of()));
        WorkspaceCapabilityService capabilities = Mockito.mock(WorkspaceCapabilityService.class);
        WorkspaceCapabilitiesResponse snapshot = new WorkspaceCapabilitiesResponse(
                capability(), capability(), capability(), capability(), capability(), capability());
        when(capabilities.snapshot()).thenReturn(snapshot);
        when(capabilities.snapshot(any())).thenReturn(snapshot);
        ChatProviderPort provider = Mockito.mock(ChatProviderPort.class);
        when(provider.configured()).thenReturn(true);
        when(provider.providerSelectionKeys()).thenReturn(Set.of("synapse-homeserver"));
        when(provider.readiness()).thenReturn(ProviderReadiness.ready("chat-provider-ready"));
        when(provider.currentCursor(any(ChatRequestContext.class))).thenReturn(new ChatCursor("chat-revision-7"));
        ChatDomainFacadeService service = new ChatDomainFacadeService(
                new ProviderRegistry(List.of(chatProvider(true)), capabilities, selections),
                selections,
                capabilities,
                new InMemoryAuditEventPublisher(),
                provider,
                allowAllContexts(),
                contextProperties(),
                FIXED);
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuer("https://auth.example/realms/weave")
                .subject("member-tenant-a")
                .claim("weave_tenant_id", "tenant-a")
                .claim("weave_tenant", "legacy-tenant-must-not-win")
                .build();

        assertThat(service.syncCursor(jwt)).isEqualTo("chat-revision-7");
        ArgumentCaptor<ChatRequestContext> context = ArgumentCaptor.forClass(ChatRequestContext.class);
        verify(provider).currentCursor(context.capture());
        assertThat(context.getValue().tenantId()).isEqualTo("tenant-a");
        assertThat(context.getValue().identityIssuer()).isEqualTo("https://auth.example/realms/weave");
        assertThat(context.getValue().actorRef().value()).isEqualTo("user:member-tenant-a");
    }

    @Test
    void canonicalContextUsesTheConfiguredTenantWhenHumanTokensOmitTenantClaims() {
        InMemoryProviderSelectionRepository selections = new InMemoryProviderSelectionRepository();
        selections.save(selection("chat", "synapse-homeserver", false, List.of()));
        WorkspaceCapabilityService capabilities = Mockito.mock(WorkspaceCapabilityService.class);
        WorkspaceCapabilitiesResponse snapshot = new WorkspaceCapabilitiesResponse(
                capability(), capability(), capability(), capability(), capability(), capability());
        when(capabilities.snapshot()).thenReturn(snapshot);
        when(capabilities.snapshot(any())).thenReturn(snapshot);
        ChatProviderPort provider = Mockito.mock(ChatProviderPort.class);
        when(provider.configured()).thenReturn(true);
        when(provider.providerSelectionKeys()).thenReturn(Set.of("synapse-homeserver"));
        when(provider.readiness()).thenReturn(ProviderReadiness.ready("chat-provider-ready"));
        when(provider.currentCursor(any(ChatRequestContext.class))).thenReturn(new ChatCursor("chat-revision-8"));
        ChatDomainFacadeService service = new ChatDomainFacadeService(
                new ProviderRegistry(List.of(chatProvider(true)), capabilities, selections),
                selections,
                capabilities,
                new InMemoryAuditEventPublisher(),
                provider,
                allowAllContexts(),
                contextProperties(),
                FIXED);
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuer("https://auth.example/realms/weave")
                .subject("member-with-configured-tenant")
                .build();

        assertThat(service.syncCursor(jwt)).isEqualTo("chat-revision-8");
        ArgumentCaptor<ChatRequestContext> context = ArgumentCaptor.forClass(ChatRequestContext.class);
        verify(provider).currentCursor(context.capture());
        assertThat(context.getValue().tenantId()).isEqualTo("tenant-default");
        assertThat(context.getValue().identityIssuer()).isEqualTo("https://auth.example/realms/weave");
    }

    @Test
    void malformedOidcIdentityRemainsAChatAuthorizationFailure() {
        ChatDomainFacadeService service = service(
                new InMemoryProviderSelectionRepository(), true, capability());
        Jwt missingIssuer = Jwt.withTokenValue("missing-issuer")
                .header("alg", "none")
                .subject("member")
                .build();
        Jwt invalidSubject = Jwt.withTokenValue("invalid-subject")
                .header("alg", "none")
                .issuer("https://auth.example/realms/weave")
                .subject("invalid subject")
                .build();

        assertThatThrownBy(() -> service.syncCursor(missingIssuer))
                .isInstanceOf(ChatAccessDeniedException.class);
        assertThatThrownBy(() -> service.syncCursor(invalidSubject))
                .isInstanceOf(ChatAccessDeniedException.class);
    }

    @Test
    void matrixSendUsesCanonicalProviderAndPublishesSupportSafeAudit() {
        InMemoryProviderSelectionRepository selections = new InMemoryProviderSelectionRepository();
        selections.save(selection("chat", "in-memory-test", false, List.of()));
        InMemoryAuditEventPublisher audit = new InMemoryAuditEventPublisher();
        ChatDomainFacadeService service = service(selections, true, capability(), audit);

        var event = service.sendEvent(
                "channel-general",
                "matrix-transaction-1",
                com.massimotter.weave.backend.chat.domain.ChatEventContent.text("Sent through Matrix"),
                memberJwt());

        assertThat(event.conversationId()).isEqualTo("channel-general");
        assertThat(event.content().body()).isEqualTo("Sent through Matrix");
        assertThat(audit.events()).singleElement().satisfies(auditEvent -> {
            assertThat(auditEvent.action()).isEqualTo(AuditAction.CHAT_MESSAGE_SENT);
            assertThat(auditEvent.sourceRef()).isEqualTo("matrix-client-server-facade");
            assertThat(auditEvent.payload())
                    .containsEntry("operation", "event-sent")
                    .containsEntry("providerPayloadExposed", false);
        });
    }

    @Test
    void policyBlockedMemberStateDoesNotExposeProviderDiagnostics() {
        InMemoryProviderSelectionRepository selections = new InMemoryProviderSelectionRepository();
        selections.save(selection("chat", "in-memory-test", false, List.of()));
        ChatDomainFacadeService service = service(selections, true,
                capability(WorkspaceCapabilityReadiness.BLOCKED, WorkspaceCapabilityPolicyState.POLICY_BLOCKED, "blocked"));

        var readiness = service.memberReadiness(memberJwt());

        assertThat(readiness.memberState()).isEqualTo(ChatMemberState.POLICY_BLOCKED);
        assertThat(readiness.providerMapping()).isNull();
        assertThat(readiness.memberImpact()).contains("role or group policy");
    }

    @Test
    void migrationPreflightIsDryRunAuditedAndSupportSafe() {
        InMemoryProviderSelectionRepository selections = new InMemoryProviderSelectionRepository();
        selections.save(selection("chat", "slack", true, List.of("Thread replies need Weave annotations.")));
        InMemoryAuditEventPublisher audit = new InMemoryAuditEventPublisher();
        ChatDomainFacadeService service = service(selections, false, capability(), audit);

        var report = service.preflight(new ChatMigrationPreflightRequest(
                "slack",
                "microsoft-teams",
                false,
                Map.of("conversations", 12, "messages", 1200, "bad key", 9),
                List.of("External emoji aliases include " + "Bear" + "er secret-token"),
                List.of("member identity collision"),
                "compare " + "xoxb-secret" + " and " + "secret=raw"), adminJwt());

        assertThat(report.mode()).isEqualTo("dry-run");
        assertThat(report.destructiveApplyAvailable()).isFalse();
        assertThat(report.auditEventPublished()).isTrue();
        assertThat(report.objectCounts()).containsEntry("conversations", 12).containsEntry("messages", 1200);
        assertThat(report.blockedOperations()).contains("destructive_apply_not_available_in_chat_domain_facade_v1", "selected_chat_mapping_not_ready");
        assertThat(report.lossyFieldWarnings().get(0)).contains("[redacted-token]");
        assertThat(audit.events()).hasSize(1);
        assertThat(audit.events().get(0).action()).isEqualTo(AuditAction.CHAT_MIGRATION_PREFLIGHTED);
        assertThat(audit.events().get(0).payload().toString())
                .doesNotContain("xoxb-secret", "secret=raw", "secret-token", "Bear" + "er");
    }

    private ChatDomainFacadeService service(
            ProviderSelectionRepository selections,
            boolean configuredProvider,
            WorkspaceCapabilityStatusResponse chatCapability) {
        return service(selections, configuredProvider, chatCapability, new InMemoryAuditEventPublisher());
    }

    private ChatDomainFacadeService service(
            ProviderSelectionRepository selections,
            boolean configuredProvider,
            WorkspaceCapabilityStatusResponse chatCapability,
            InMemoryAuditEventPublisher audit) {
        WorkspaceCapabilityService capabilities = Mockito.mock(WorkspaceCapabilityService.class);
        WorkspaceCapabilitiesResponse snapshot = new WorkspaceCapabilitiesResponse(
                capability(), chatCapability, capability(), capability(), capability(), capability());
        when(capabilities.snapshot()).thenReturn(snapshot);
        when(capabilities.snapshot(any())).thenReturn(snapshot);
        ProviderRegistry registry = new ProviderRegistry(List.of(chatProvider(configuredProvider)), capabilities, selections);
        return new ChatDomainFacadeService(
                registry,
                selections,
                capabilities,
                audit,
                new WeaveCanonicalChatAdapter(),
                allowAllContexts(),
                contextProperties(),
                FIXED);
    }

    private StaticProviderPort chatProvider(boolean configured) {
        return new StaticProviderPort(new ProviderStatusResponse(
                ProviderModule.MATRIX,
                "synapse-homeserver",
                configured ? ProviderState.CONFIGURED : ProviderState.NOT_CONFIGURED,
                configured ? "configured" : "not_configured",
                true,
                configured,
                true,
                true,
                true,
                false,
                "Chat provider seam is support-safe.",
                Set.of("chat.read", "chat.send", "chat.history"),
                Set.of("raw-provider-errors", "credential-exposure", "direct-member-provider-api"),
                List.of("provider-not-configured", "provider-disabled", "unsupported-capability"),
                "support-safe redaction policy",
                List.of("synapse-homeserver", "in-memory-test", "slack", "microsoft-teams"),
                Map.of("secretsReturned", false, "downstreamErrorsReturned", false)));
    }

    private ProviderSelection selection(String category, String providerKey, boolean migrationRequired, List<String> lossyNotes) {
        return new ProviderSelection(
                category,
                providerKey,
                providerKey.equals("slack") ? "external_existing_provider" : "recommended_self_hosted_default",
                "secretref://weave/provider/" + providerKey,
                "actor:test-admin",
                Instant.parse("2026-05-24T18:00:00Z"),
                true,
                true,
                migrationRequired,
                lossyNotes);
    }

    private WorkspaceCapabilityStatusResponse capability() {
        return capability(WorkspaceCapabilityReadiness.READY, WorkspaceCapabilityPolicyState.ALLOWED, "Ready through Weave.");
    }

    private WorkspaceCapabilityStatusResponse capability(
            WorkspaceCapabilityReadiness readiness,
            WorkspaceCapabilityPolicyState policyState,
            String impact) {
        return new WorkspaceCapabilityStatusResponse(
                policyState != WorkspaceCapabilityPolicyState.DISABLED,
                readiness,
                policyState,
                "test-profile",
                impact,
                List.of("chat.read", "chat.send"));
    }

    private Jwt memberJwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuer("https://auth.example/realms/weave")
                .subject("member-123")
                .claim("weave_tenant_id", "weave-dogfood")
                .claim("weave_context_id", "context-isolated-test")
                .claim("organization", HumanJwtTestSupport.organizationWithRole("member"))
                .build();
    }

    private Jwt adminJwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuer("https://auth.example/realms/weave")
                .subject("admin-123")
                .claim("weave_tenant_id", "weave-dogfood")
                .claim("weave_context_id", "context-isolated-test")
                .claim("organization", HumanJwtTestSupport.organizationWithRole("admin"))
                .build();
    }

    private ContextAuthorizationPort allowAllContexts() {
        return request -> ContextAuthorizationDecision.allow("test context grant");
    }

    private ContextAuthorizationProperties contextProperties() {
        return new ContextAuthorizationProperties(
                "weave_tenant_id",
                "tenant_id",
                "tenant-default",
                "sub",
                "user:",
                List.of(),
                List.of(),
                List.of());
    }
}
