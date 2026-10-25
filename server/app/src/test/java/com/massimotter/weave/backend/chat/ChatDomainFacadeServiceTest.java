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
import com.massimotter.weave.backend.chat.domain.ChatChange;
import com.massimotter.weave.backend.chat.domain.ChatChangeSet;
import com.massimotter.weave.backend.chat.domain.ChatEventContent;
import com.massimotter.weave.backend.chat.domain.ChatTimelineEvent;
import com.massimotter.weave.backend.chat.domain.ConversationId;
import com.massimotter.weave.backend.chat.domain.ChatAccessDeniedException;
import com.massimotter.weave.backend.chat.domain.ChatMemberState;
import com.massimotter.weave.backend.chat.domain.ChatMigrationPreflightRequest;
import com.massimotter.weave.backend.chat.domain.ChatRequestContext;
import com.massimotter.weave.backend.chat.domain.ChatActorRef;
import com.massimotter.weave.backend.chat.domain.ChatIdentityRef;
import com.massimotter.weave.backend.chat.domain.ChatResolvedIdentity;
import com.massimotter.weave.backend.chat.domain.ChatEncryptionState;
import com.massimotter.weave.backend.chat.port.ChatProviderPort;
import com.massimotter.weave.backend.security.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.spaces.authorization.ContextAuthorizationDecision;
import com.massimotter.weave.backend.spaces.authorization.ContextAuthorizationPort;
import com.massimotter.weave.backend.identity.IdentityReferences;
import com.massimotter.weave.backend.identity.CurrentOrganizationMemberQuery;
import com.massimotter.weave.backend.model.WorkspaceCapabilitiesResponse;
import com.massimotter.weave.backend.model.WorkspaceCapabilityPolicyState;
import com.massimotter.weave.backend.model.WorkspaceCapabilityReadiness;
import com.massimotter.weave.backend.model.WorkspaceCapabilityStatusResponse;
import com.massimotter.weave.backend.provider.InMemoryProviderSelectionRepository;
import com.massimotter.weave.backend.discovery.provider.ProviderModule;
import com.massimotter.weave.backend.provider.ProviderRegistry;
import com.massimotter.weave.backend.providerbinding.selection.ProviderSelection;
import com.massimotter.weave.backend.providerbinding.selection.ProviderSelectionRepository;
import com.massimotter.weave.backend.discovery.provider.ProviderState;
import com.massimotter.weave.backend.discovery.provider.ProviderStatusResponse;
import com.massimotter.weave.backend.discovery.provider.StaticProviderPort;
import com.massimotter.weave.backend.providerbinding.contract.ProviderReadiness;
import com.massimotter.weave.backend.service.WorkspaceCapabilityService;
import com.massimotter.weave.backend.identity.OrganizationIdentityContextResolver;
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
    void matrixSyncPagesCommittedChangesWithoutSkippingMoreThanOneTimelineWindow() {
        InMemoryProviderSelectionRepository selections = new InMemoryProviderSelectionRepository();
        selections.save(selection("chat", "in-memory-test", false, List.of()));
        ChatDomainFacadeService service = service(selections, true, capability());
        String room = service.createConversation("sync-room", "Business", "channel", List.of(), memberJwt())
                .conversationId();
        for (int index = 0; index < 105; index++) {
            service.sendEvent(room, "sync-message-" + index,
                    ChatEventContent.text("message-" + index), memberJwt());
        }
        ChatDomainFacadeService.ChatTimelineWindow coldWindow =
                service.timelineWindow(room, memberJwt(), 100);
        assertThat(coldWindow.timeline().events()).hasSize(100);
        assertThat(coldWindow.limited()).isTrue();

        ChatDomainFacadeService.ChatSyncDelta first = service.syncDelta(memberJwt(), "chat-revision-0", 100);
        ChatDomainFacadeService.ChatSyncDelta second = service.syncDelta(memberJwt(), first.cursor(), 100);
        assertThat(first.cursor()).isEqualTo("chat-revision-101");
        assertThat(second.cursor()).isEqualTo("chat-revision-107");
        assertThat(first.events().get(room)).hasSize(99);
        assertThat(second.events().get(room)).hasSize(6);
        assertThat(first.events().get(room).stream().map(event -> event.content().body()).toList())
                .contains("message-0", "message-98")
                .doesNotContain("message-99");
        assertThat(second.events().get(room).stream().map(event -> event.content().body()).toList())
                .containsExactly("message-99", "message-100", "message-101",
                        "message-102", "message-103", "message-104");
    }

    @Test
    void matrixSyncDoesNotAdvancePastAChangeCommittedAfterItsHighWaterRead() {
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
        when(provider.currentCursor(any(ChatRequestContext.class)))
                .thenReturn(new ChatCursor("chat-revision-2"), new ChatCursor("chat-revision-3"));
        var late = new ChatChange(3, "message.created", new ConversationId("room-late"),
                "event-late", FIXED.instant());
        when(provider.changes(any(ChatRequestContext.class), any(ChatCursor.class), eq(100)))
                .thenReturn(new ChatChangeSet(new ChatCursor("chat-revision-3"), List.of(late)));
        when(provider.event(any(ChatRequestContext.class), eq(late.conversationId()), eq("event-late")))
                .thenReturn(new ChatTimelineEvent("event-late", "room-late", "user:member-123",
                        FIXED.instant(), ChatEventContent.text("late"), "committed", false));
        ChatDomainFacadeService service = new ChatDomainFacadeService(
                new ProviderRegistry(List.of(chatProvider(true)), capabilities, selections),
                selections, capabilities, new InMemoryAuditEventPublisher(), provider,
                allowAllContexts(), contextProperties(), FIXED);

        ChatDomainFacadeService.ChatSyncDelta first = service.syncDelta(memberJwt(), "chat-revision-0", 100);
        assertThat(first.cursor()).isEqualTo("chat-revision-2");
        assertThat(first.events()).isEmpty();
        ChatDomainFacadeService.ChatSyncDelta second = service.syncDelta(memberJwt(), first.cursor(), 100);
        assertThat(second.cursor()).isEqualTo("chat-revision-3");
        assertThat(second.events().get("room-late").getFirst().content().body()).isEqualTo("late");
    }

    @Test
    void matrixSyncCarriesOwnDepartureWithoutReadingRevokedRoomContent() {
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
        when(provider.currentCursor(any(ChatRequestContext.class)))
                .thenReturn(new ChatCursor("chat-revision-8"));
        when(provider.changes(any(ChatRequestContext.class), any(ChatCursor.class), eq(100)))
                .thenAnswer(call -> {
                    ChatRequestContext context = call.getArgument(0);
                    return new ChatChangeSet(new ChatCursor("chat-revision-8"), List.of(
                            new ChatChange(8, "membership.left", new ConversationId("room-left"),
                                    context.actorRef().value(), FIXED.instant())));
                });
        ChatDomainFacadeService service = new ChatDomainFacadeService(
                new ProviderRegistry(List.of(chatProvider(true)), capabilities, selections),
                selections, capabilities, new InMemoryAuditEventPublisher(), provider,
                allowAllContexts(), contextProperties(), FIXED);

        ChatDomainFacadeService.ChatSyncDelta delta = service.syncDelta(memberJwt(),
                "chat-revision-7", 100);
        assertThat(delta.cursor()).isEqualTo("chat-revision-8");
        assertThat(delta.leftRooms()).containsExactly("room-left");
        assertThat(delta.events()).isEmpty();
        verify(provider, never()).event(any(ChatRequestContext.class), any(ConversationId.class), any());
    }

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
                com.massimotter.weave.backend.identity.OrganizationIdentityContextResolver.configured(contextProperties()),
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
    void pendingInvitationsRequireCurrentSpaceViewAndCurrentOwnMembershipWithoutHistoryReads() {
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
        var membership = new com.massimotter.weave.backend.chat.domain.ChatMembership(
                "own-invite", "pending-room", "user:member-123", "member", "invited", null, List.of());
        var invitation = new com.massimotter.weave.backend.chat.domain.ChatInvitation(
                "pending-room", "Pending", FIXED.instant(),
                com.massimotter.weave.backend.chat.domain.ChatEncryptionState.unencrypted(), new com.massimotter.weave.backend.chat.domain.ChatActorRef("user:author"), membership);
        when(provider.invitedConversations(any(), eq(""), eq(100))).thenReturn(List.of(invitation));
        when(provider.invitedConversations(any(), eq(""), eq(1))).thenReturn(List.of(invitation));
        SpaceAccessPort spaces = Mockito.mock(SpaceAccessPort.class);
        String account = IdentityReferences.accountId("https://auth.example/realms/weave", "member-123");
        when(spaces.visibleSpaceRefs("weave-dogfood", account, "", 100)).thenReturn(List.of("chosen-space"));
        when(spaces.allows("weave-dogfood", "chosen-space", account, SpaceAccessPort.Permission.VIEW)).thenReturn(true);
        ChatDomainFacadeService service = new ChatDomainFacadeService(
                new ProviderRegistry(List.of(chatProvider(true)), capabilities, selections), selections, capabilities,
                new InMemoryAuditEventPublisher(), provider, allowAllContexts(), contextProperties(),
                OrganizationIdentityContextResolver.configured(contextProperties()), FIXED, new DurableChatSpaceAccess(spaces));
        assertThat(service.invitations(memberJwt())).containsExactly(invitation);
        when(provider.currentCursor(any(ChatRequestContext.class))).thenReturn(new ChatCursor("chat-revision-1"));
        when(provider.changes(any(ChatRequestContext.class), any(), eq(100)))
                .thenReturn(new ChatChangeSet(new ChatCursor("chat-revision-1"), List.of(
                        new ChatChange(1, "conversation.created", new ConversationId("pending-room"),
                                "pending-room", FIXED.instant()))));
        var pendingDelta = service.syncDelta(memberJwt(), "chat-revision-0", 100);
        assertThat(pendingDelta.cursor()).isEqualTo("chat-revision-1");
        assertThat(pendingDelta.events()).isEmpty();
        verify(provider, never()).event(any(ChatRequestContext.class), any(ConversationId.class), any());
        verify(provider, never()).conversation(any(ChatRequestContext.class), any());
        verify(provider, never()).timelineEvents(any(ChatRequestContext.class), any(), any(), org.mockito.ArgumentMatchers.anyInt());
        when(spaces.allows("weave-dogfood", "chosen-space", account, SpaceAccessPort.Permission.VIEW)).thenReturn(false);
        assertThat(service.invitations(memberJwt())).isEmpty();
        assertThat(service.syncDelta(memberJwt(), "chat-revision-0", 100).cursor()).isEqualTo("chat-revision-0");
        verify(provider, times(1)).changes(any(ChatRequestContext.class), any(), eq(100));
        verify(provider, times(1)).invitedConversations(any(), eq(""), eq(100));
        when(spaces.allows("weave-dogfood", "chosen-space", account, SpaceAccessPort.Permission.VIEW)).thenReturn(true);
        // Initial metadata may race a real acceptance; an exact pending recheck must omit it.
        when(provider.invitedConversations(any(), eq(""), eq(1))).thenReturn(List.of());
        assertThat(service.invitations(memberJwt())).isEmpty();
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

        when(spaces.allows("weave-dogfood", "beta", account, SpaceAccessPort.Permission.VIEW))
                .thenReturn(true);
        when(provider.currentCursor(any(ChatRequestContext.class)))
                .thenAnswer(call -> new ChatCursor("chat-revision-"
                        + ("beta".equals(((ChatRequestContext) call.getArgument(0)).contextId()) ? 120 : 60)));
        when(provider.changes(any(ChatRequestContext.class), any(ChatCursor.class), eq(100)))
                .thenAnswer(call -> {
                    ChatRequestContext context = call.getArgument(0);
                    String afterText = ((ChatCursor) call.getArgument(1)).value();
                    long after = Long.parseLong(afterText.substring("chat-revision-".length()));
                    boolean beta = "beta".equals(context.contextId());
                    List<ChatChange> page = java.util.stream.LongStream
                            .rangeClosed(beta ? 61 : 1, beta ? 120 : 60)
                            .filter(sequence -> sequence > after)
                            .mapToObj(sequence -> new ChatChange(sequence, "membership.joined",
                                    new ConversationId(beta ? "room-beta" : "room-alpha"),
                                    null, FIXED.instant()))
                            .toList();
                    return new ChatChangeSet(new ChatCursor("chat-revision-"
                            + (page.isEmpty() ? after : page.getLast().sequence())), page);
                });
        ChatDomainFacadeService.ChatSyncDelta firstPage = service.syncDelta(memberJwt(), "chat-revision-0", 100);
        ChatDomainFacadeService.ChatSyncDelta secondPage = service.syncDelta(memberJwt(), firstPage.cursor(), 100);
        assertThat(firstPage.cursor()).isEqualTo("chat-revision-100");
        assertThat(secondPage.cursor()).isEqualTo("chat-revision-120");
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

    @Test
    void participantDiscoveryUsesCurrentDurableSpaceAccessAndRejectsForeignIdentities() {
        InMemoryProviderSelectionRepository selections = new InMemoryProviderSelectionRepository();
        selections.save(selection("chat", "synapse-homeserver", false, List.of()));
        WorkspaceCapabilityService capabilities = Mockito.mock(WorkspaceCapabilityService.class);
        SpaceAccessPort spaces = Mockito.mock(SpaceAccessPort.class);
        String issuer = "https://auth.example/realms/weave";
        when(spaces.allows("weave-dogfood", "context-isolated-test",
                IdentityReferences.accountId(issuer, "member-123"), SpaceAccessPort.Permission.VIEW)).thenReturn(true);
        when(spaces.allows("weave-dogfood", "context-isolated-test",
                IdentityReferences.accountId(issuer, "peer"), SpaceAccessPort.Permission.VIEW)).thenReturn(true, false);
        var service = new ChatDomainFacadeService(
                new ProviderRegistry(List.of(chatProvider(true)), capabilities, selections), selections, capabilities,
                new InMemoryAuditEventPublisher(), Mockito.mock(ChatProviderPort.class), allowAllContexts(),
                contextProperties(), OrganizationIdentityContextResolver.configured(contextProperties()), FIXED,
                new DurableChatSpaceAccess(spaces));
        var peer = new com.massimotter.weave.backend.chat.domain.ChatResolvedIdentity(
                new com.massimotter.weave.backend.chat.domain.ChatIdentityRef("weave-dogfood", issuer,
                        new com.massimotter.weave.backend.chat.domain.ChatActorRef("user:peer")), "user:peer");
        assertThat(service.canDiscoverParticipant(memberJwt(), peer)).isTrue();
        assertThat(service.canDiscoverParticipant(memberJwt(), peer)).isFalse();
        var outsider = new com.massimotter.weave.backend.chat.domain.ChatResolvedIdentity(
                new com.massimotter.weave.backend.chat.domain.ChatIdentityRef("foreign-org", issuer,
                        new com.massimotter.weave.backend.chat.domain.ChatActorRef("user:peer")), "user:peer");
        assertThat(service.canDiscoverParticipant(memberJwt(), outsider)).isFalse();
    }

    @Test
    void directCreationRechecksCurrentActorRecipientAndSpaceOnEveryRetryBeforeProviderPreparation() {
        var selections = new InMemoryProviderSelectionRepository();
        selections.save(selection("chat", "synapse-homeserver", false, List.of()));
        var capabilities = Mockito.mock(WorkspaceCapabilityService.class);
        var snapshot = new WorkspaceCapabilitiesResponse(capability(), capability(), capability(), capability(), capability(), capability());
        when(capabilities.snapshot()).thenReturn(snapshot);
        when(capabilities.snapshot(any())).thenReturn(snapshot);
        var provider = Mockito.mock(ChatProviderPort.class);
        when(provider.configured()).thenReturn(true);
        when(provider.providerSelectionKeys()).thenReturn(Set.of("synapse-homeserver"));
        when(provider.readiness()).thenReturn(ProviderReadiness.ready("chat-provider-ready"));
        var members = Mockito.mock(com.massimotter.weave.backend.identity.CurrentOrganizationMemberQuery.class);
        var spaces = Mockito.mock(SpaceAccessPort.class);
        when(spaces.allows(any(), any(), any(), any())).thenReturn(true);
        var service = new ChatDomainFacadeService(
                new ProviderRegistry(List.of(chatProvider(true)), capabilities, selections), selections, capabilities,
                new InMemoryAuditEventPublisher(), provider, allowAllContexts(), contextProperties(),
                OrganizationIdentityContextResolver.configured(contextProperties()), FIXED, null,
                new DurableChatSpaceAccess(spaces), members);
        String issuer = memberJwt().getIssuer().toString();
        var peer = new com.massimotter.weave.backend.chat.domain.ChatResolvedIdentity(
                new com.massimotter.weave.backend.chat.domain.ChatIdentityRef("weave-dogfood", issuer,
                        new com.massimotter.weave.backend.chat.domain.ChatActorRef("user:peer")), "user:peer");
        var self = new com.massimotter.weave.backend.chat.domain.ChatResolvedIdentity(
                new com.massimotter.weave.backend.chat.domain.ChatIdentityRef("weave-dogfood", issuer,
                        new com.massimotter.weave.backend.chat.domain.ChatActorRef("user:member-123")), "user:member-123");
        service.createConversation("direct-transaction", "Direct", "direct", List.of(peer), memberJwt());
        verify(members).requireCurrentMember(any(), eq(issuer), eq("member-123"));
        verify(members).requireCurrentMember(any(), eq(issuer), eq("peer"));
        for (var invalid : List.of(List.<com.massimotter.weave.backend.chat.domain.ChatResolvedIdentity>of(),
                List.of(self), List.of(peer, peer), List.of(peer, self))) {
            assertThatThrownBy(() -> service.createConversation("invalid-pair", "Direct", "direct", invalid, memberJwt()))
                    .isInstanceOf(ChatAccessDeniedException.class);
        }
        Mockito.doThrow(new com.massimotter.weave.backend.identity.CurrentOrganizationMemberQuery.Denied())
                .when(members).requireCurrentMember(any(), eq(issuer), eq("peer"));
        assertThatThrownBy(() -> service.createConversation("direct-transaction", "Direct", "direct", List.of(peer), memberJwt()))
                .isInstanceOf(ChatAccessDeniedException.class);
        Mockito.doThrow(new com.massimotter.weave.backend.identity.CurrentOrganizationMemberQuery.Unavailable())
                .when(members).requireCurrentMember(any(), eq(issuer), eq("peer"));
        assertThatThrownBy(() -> service.createConversation("direct-transaction", "Direct", "direct", List.of(peer), memberJwt()))
                .isInstanceOf(com.massimotter.weave.backend.chat.domain.ChatProviderUnavailableException.class);
        when(spaces.allows(eq("weave-dogfood"), any(), eq(IdentityReferences.accountId(issuer, "peer")), any())).thenReturn(false);
        assertThatThrownBy(() -> service.createConversation("direct-transaction", "Direct", "direct", List.of(peer), memberJwt()))
                .isInstanceOf(ChatAccessDeniedException.class);
        verify(provider, times(1)).createConversation(any(ChatRequestContext.class), any(), any(), any(), any(), any());
    }

    @Test
    void selectedEmptySpaceSupportsDiscoveryFirstRoomAndSendWithoutDefaultSpaceAccess() {
        var fixture = selectedSpaceFixture();
        Jwt jwt = unscopedMemberJwt();
        assertThat(fixture.service().conversations(jwt).conversations()).isEmpty();
        assertThatThrownBy(() -> fixture.service().requireParticipantDiscovery(jwt))
                .isInstanceOf(ChatAccessDeniedException.class);
        assertThat(fixture.service().canDiscoverParticipant(jwt, selectedPeer(), "space-project")).isTrue();

        var room = fixture.service().createConversation("first-selected-room", "Project", "channel", List.of(),
                ChatEncryptionState.unencrypted(), jwt, "space-project");
        fixture.service().sendEvent(room.conversationId(), "first-selected-message",
                ChatEventContent.text("First message"), jwt);
        assertThat(fixture.service().conversations(jwt).conversations())
                .extracting(conversation -> conversation.conversationId()).containsExactly(room.conversationId());
        assertThat(fixture.service().messages(room.conversationId(), jwt).messages())
                .extracting(message -> message.body()).containsExactly("First message");
        verify(fixture.provider()).createConversation(
                org.mockito.ArgumentMatchers.<ChatRequestContext>argThat(context -> context.contextId().equals("space-project")),
                any(), eq("Project"), eq("channel"), eq(List.of()), eq(ChatEncryptionState.unencrypted()));
        verify(fixture.members()).requireCurrentMember(jwt, jwt.getIssuer().toString(), jwt.getSubject());
    }

    @Test
    void selectedSpaceRechecksCallerAndRecipientAccessInsteadOfFallingBack() {
        var fixture = selectedSpaceFixture();
        Jwt jwt = unscopedMemberJwt();
        assertThat(fixture.service().canDiscoverParticipant(jwt, selectedPeer(), "space-project")).isTrue();
        String issuer = jwt.getIssuer().toString();
        when(fixture.spaces().allows("weave-dogfood", "space-project",
                IdentityReferences.accountId(issuer, "peer"), SpaceAccessPort.Permission.VIEW)).thenReturn(false);
        assertThat(fixture.service().canDiscoverParticipant(jwt, selectedPeer(), "space-project")).isFalse();
        assertThatThrownBy(() -> fixture.service().createConversation("revoked-peer", "Direct", "direct",
                List.of(selectedPeer()), ChatEncryptionState.unencrypted(), jwt, "space-project"))
                .isInstanceOf(ChatAccessDeniedException.class);
        when(fixture.spaces().allows("weave-dogfood", "space-project",
                IdentityReferences.accountId(issuer, "member-123"), SpaceAccessPort.Permission.EDIT)).thenReturn(false);
        assertThatThrownBy(() -> fixture.service().createConversation("read-only-space", "Project", "channel",
                List.of(), ChatEncryptionState.unencrypted(), jwt, "space-project"))
                .isInstanceOf(ChatAccessDeniedException.class);
        // A visible alternative must never substitute for the explicitly selected inaccessible Space.
        for (String denied : List.of("space-revoked", "space-foreign", "space-missing")) {
            assertThatThrownBy(() -> fixture.service().requireParticipantDiscovery(jwt, denied))
                    .isInstanceOf(ChatAccessDeniedException.class);
            assertThatThrownBy(() -> fixture.service().createConversation("denied-room", "Project", "channel",
                    List.of(), ChatEncryptionState.unencrypted(), jwt, denied))
                    .isInstanceOf(ChatAccessDeniedException.class);
        }
        when(fixture.spaces().allows("weave-dogfood", "space-project",
                IdentityReferences.accountId(issuer, "member-123"), SpaceAccessPort.Permission.VIEW)).thenReturn(false);
        assertThatThrownBy(() -> fixture.service().requireParticipantDiscovery(jwt, "space-project"))
                .isInstanceOf(ChatAccessDeniedException.class);
        verify(fixture.provider(), never()).createConversation(any(ChatRequestContext.class), any(), any(), any(), any(), any());
    }

    @Test
    void selectedCreationRechecksCurrentOrganizationBeforeEveryRetry() {
        var fixture = selectedSpaceFixture();
        Jwt jwt = unscopedMemberJwt();
        var first = fixture.service().createConversation("selected-direct", "Direct", "direct",
                List.of(selectedPeer()), ChatEncryptionState.unencrypted(), jwt, "space-project");
        assertThat(first.kind()).isEqualTo("direct");
        Mockito.doThrow(new CurrentOrganizationMemberQuery.Denied()).when(fixture.members())
                .requireCurrentMember(any(), eq(jwt.getIssuer().toString()), eq("peer"));
        assertThatThrownBy(() -> fixture.service().createConversation("selected-direct", "Direct", "direct",
                List.of(selectedPeer()), ChatEncryptionState.unencrypted(), jwt, "space-project"))
                .isInstanceOf(ChatAccessDeniedException.class);
        Mockito.doThrow(new CurrentOrganizationMemberQuery.Denied()).when(fixture.members())
                .requireCurrentMember(any(), eq(jwt.getIssuer().toString()), eq("member-123"));
        assertThatThrownBy(() -> fixture.service().createConversation("selected-channel", "Project", "channel",
                List.of(), ChatEncryptionState.unencrypted(), jwt, "space-project"))
                .isInstanceOf(ChatAccessDeniedException.class);
        verify(fixture.provider(), times(1)).createConversation(any(ChatRequestContext.class), any(), any(), any(), any(), any());
    }

    @Test
    void selectedSpaceCannotOverrideEitherExplicitTokenContextClaim() {
        var fixture = selectedSpaceFixture();
        for (Map<String, String> boundaries : List.of(
                Map.of("weave_context_id", "workspace-default"),
                Map.of("context_id", "workspace-default"),
                Map.of("weave_context_id", "space-project", "context_id", "workspace-default"))) {
            Jwt jwt = Jwt.withTokenValue("bounded-token").headers(headers -> headers.putAll(unscopedMemberJwt().getHeaders()))
                    .claims(claims -> { claims.putAll(unscopedMemberJwt().getClaims()); claims.putAll(boundaries); }).build();
            assertThatThrownBy(() -> fixture.service().requireParticipantDiscovery(jwt, "space-project"))
                    .isInstanceOf(ChatAccessDeniedException.class);
            assertThatThrownBy(() -> fixture.service().createConversation("bounded-create", "Project", "channel",
                    List.of(), ChatEncryptionState.unencrypted(), jwt, "space-project"))
                    .isInstanceOf(ChatAccessDeniedException.class);
        }
        Jwt matching = Jwt.withTokenValue("matching-token").headers(headers -> headers.putAll(unscopedMemberJwt().getHeaders()))
                .claims(claims -> { claims.putAll(unscopedMemberJwt().getClaims()); claims.put("context_id", "space-project"); }).build();
        fixture.service().requireParticipantDiscovery(matching, "space-project");
        verify(fixture.provider(), never()).createConversation(any(ChatRequestContext.class), any(), any(), any(), any(), any());
    }

    @Test
    void selectedSpaceRejectsForeignRecipientAndMalformedReferences() {
        var fixture = selectedSpaceFixture();
        Jwt jwt = unscopedMemberJwt();
        for (var peer : List.of(
                new ChatResolvedIdentity(new ChatIdentityRef("foreign-org", jwt.getIssuer().toString(),
                        new ChatActorRef("user:peer")), "user:peer"),
                new ChatResolvedIdentity(new ChatIdentityRef("weave-dogfood", "https://foreign.invalid/realm",
                        new ChatActorRef("user:peer")), "user:peer"))) {
            assertThat(fixture.service().canDiscoverParticipant(jwt, peer, "space-project")).isFalse();
            assertThatThrownBy(() -> fixture.service().createConversation("foreign-invite", "Project", "channel",
                    List.of(peer), ChatEncryptionState.unencrypted(), jwt, "space-project"))
                    .isInstanceOf(ChatAccessDeniedException.class);
        }
        for (String invalid : List.of("", " ", "space/other", "space,other", "x".repeat(256))) {
            assertThatThrownBy(() -> fixture.service().requireParticipantDiscovery(jwt, invalid))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        verify(fixture.provider(), never()).createConversation(any(ChatRequestContext.class), any(), any(), any(), any(), any());
    }

    @Test
    void maximumCanonicalSpaceReferenceAndUnselectedDefaultRetainTheirOwnContexts() {
        var fixture = selectedSpaceFixture();
        Jwt jwt = unscopedMemberJwt();
        String actor = IdentityReferences.accountId(jwt.getIssuer().toString(), jwt.getSubject());
        String longestRef = "space:" + "x".repeat(249);
        for (String space : List.of(longestRef, "workspace-default")) {
            when(fixture.spaces().allows(eq("weave-dogfood"), eq(space), eq(actor), any())).thenReturn(true);
        }
        fixture.service().requireParticipantDiscovery(jwt, longestRef);
        fixture.service().createConversation("long-space-create", "Long reference", "channel", List.of(),
                ChatEncryptionState.unencrypted(), jwt, longestRef);
        fixture.service().requireParticipantDiscovery(jwt);
        fixture.service().createConversation("default-space-create", "Default", "channel", List.of(), jwt);
        var contexts = ArgumentCaptor.forClass(ChatRequestContext.class);
        verify(fixture.provider(), times(2)).createConversation(contexts.capture(), any(), any(), any(), any(), any());
        assertThat(contexts.getAllValues()).extracting(context -> context.contextId())
                .containsExactly(longestRef, "workspace-default");
    }

    private SelectedSpaceFixture selectedSpaceFixture() {
        var selections = new InMemoryProviderSelectionRepository();
        selections.save(selection("chat", "in-memory-test", false, List.of()));
        var capabilities = Mockito.mock(WorkspaceCapabilityService.class);
        var snapshot = new WorkspaceCapabilitiesResponse(capability(), capability(), capability(), capability(), capability(), capability());
        when(capabilities.snapshot()).thenReturn(snapshot);
        when(capabilities.snapshot(any())).thenReturn(snapshot);
        var spaces = Mockito.mock(SpaceAccessPort.class);
        String issuer = unscopedMemberJwt().getIssuer().toString();
        String actor = IdentityReferences.accountId(issuer, "member-123");
        when(spaces.allows(eq("weave-dogfood"), eq("space-project"), eq(actor), any())).thenReturn(true);
        when(spaces.allows("weave-dogfood", "space-project", IdentityReferences.accountId(issuer, "peer"),
                SpaceAccessPort.Permission.VIEW)).thenReturn(true);
        when(spaces.visibleSpaceRefs("weave-dogfood", actor, "", 100)).thenReturn(List.of("space-project"));
        var provider = Mockito.spy(new WeaveCanonicalChatAdapter());
        var members = Mockito.mock(CurrentOrganizationMemberQuery.class);
        var service = new ChatDomainFacadeService(
                new ProviderRegistry(List.of(chatProvider(true)), capabilities, selections), selections, capabilities,
                new InMemoryAuditEventPublisher(), provider, allowAllContexts(), contextProperties(),
                OrganizationIdentityContextResolver.configured(contextProperties()), FIXED, null,
                new DurableChatSpaceAccess(spaces), members);
        return new SelectedSpaceFixture(service, spaces, provider, members);
    }

    private record SelectedSpaceFixture(ChatDomainFacadeService service, SpaceAccessPort spaces,
            WeaveCanonicalChatAdapter provider, CurrentOrganizationMemberQuery members) {}

    private ChatResolvedIdentity selectedPeer() {
        return new ChatResolvedIdentity(new ChatIdentityRef("weave-dogfood",
                unscopedMemberJwt().getIssuer().toString(), new ChatActorRef("user:peer")), "user:peer");
    }

    private Jwt unscopedMemberJwt() {
        return Jwt.withTokenValue("unscoped-token").header("alg", "none")
                .issuer("https://auth.example/realms/weave").subject("member-123")
                .claim("weave_tenant_id", "weave-dogfood")
                .claim("organization", HumanJwtTestSupport.organizationWithRole("member")).build();
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
