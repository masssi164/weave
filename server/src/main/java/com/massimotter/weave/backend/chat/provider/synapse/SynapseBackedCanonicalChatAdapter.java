package com.massimotter.weave.backend.chat.provider.synapse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.chat.domain.ChatActorRef;
import com.massimotter.weave.backend.chat.domain.ChatChangeSet;
import com.massimotter.weave.backend.chat.domain.ChatConversation;
import com.massimotter.weave.backend.chat.domain.ChatConversations;
import com.massimotter.weave.backend.chat.domain.ChatCursor;
import com.massimotter.weave.backend.chat.domain.ChatEventContent;
import com.massimotter.weave.backend.chat.domain.ChatEventKind;
import com.massimotter.weave.backend.chat.domain.ChatEncryptionState;
import com.massimotter.weave.backend.chat.domain.ChatMessage;
import com.massimotter.weave.backend.chat.domain.ChatResolvedIdentity;
import com.massimotter.weave.backend.chat.domain.ChatMessages;
import com.massimotter.weave.backend.chat.domain.ChatProviderUnavailableException;
import com.massimotter.weave.backend.chat.domain.ChatReadReceipt;
import com.massimotter.weave.backend.chat.domain.ChatRedactionReceipt;
import com.massimotter.weave.backend.chat.domain.ChatRequestContext;
import com.massimotter.weave.backend.chat.domain.ChatTimeline;
import com.massimotter.weave.backend.chat.domain.ChatTimelineEvent;
import com.massimotter.weave.backend.chat.domain.ChatTransactionId;
import com.massimotter.weave.backend.chat.domain.ChatTypingIndicator;
import com.massimotter.weave.backend.chat.domain.ConversationId;
import com.massimotter.weave.backend.chat.port.CanonicalChatStore;
import com.massimotter.weave.backend.chat.port.ChatProviderPort;
import com.massimotter.weave.backend.chat.port.ChatSouthboundProvider;
import com.massimotter.weave.backend.config.ChatRuntimeProperties;
import com.massimotter.weave.backend.portability.ProviderConformanceProfile;
import com.massimotter.weave.backend.portability.ProviderReadiness;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SynapseBackedCanonicalChatAdapter implements ChatProviderPort {

    private final CanonicalChatStore store;
    private final MatrixSynapseChatSouthboundAdapter provider;
    private final ChatRuntimeProperties.Matrix properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public SynapseBackedCanonicalChatAdapter(
            CanonicalChatStore store,
            MatrixSynapseChatSouthboundAdapter provider,
            ChatRuntimeProperties.Matrix properties,
            ObjectMapper objectMapper,
            Clock clock) {
        this.store = store;
        this.provider = provider;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
        if (!"durable-relational-flyway".equals(store.persistencePosture())) {
            throw new IllegalStateException("Matrix/Synapse Chat requires durable canonical JDBC storage.");
        }
    }

    @Override
    public String providerKey() {
        return provider.providerKey();
    }

    @Override
    public Set<String> providerSelectionKeys() {
        return Set.of(provider.providerKey(), "synapse-homeserver");
    }

    @Override
    public boolean configured() {
        return provider.configured() && "durable-relational-flyway".equals(store.persistencePosture());
    }

    @Override
    public ProviderReadiness readiness() {
        if (!configured()) {
            return ProviderReadiness.degraded("chat-provider-or-canonical-storage-not-configured");
        }
        if (store.degradedMappingCount(provider.providerKey()) > 0) {
            return ProviderReadiness.degraded("chat-provider-mapping-degraded");
        }
        return provider.readiness();
    }

    @Override
    public ProviderConformanceProfile conformanceProfile() {
        Map<String, ProviderConformanceProfile.MappingClass> mappings = new LinkedHashMap<>();
        mappings.put("conversation", ProviderConformanceProfile.MappingClass.PORTABLE);
        mappings.put("message", ProviderConformanceProfile.MappingClass.PORTABLE);
        mappings.put("membership", ProviderConformanceProfile.MappingClass.PORTABLE);
        mappings.put("attachment", ProviderConformanceProfile.MappingClass.ARCHIVE_ONLY);
        mappings.put("thread", ProviderConformanceProfile.MappingClass.PORTABLE);
        mappings.put("reaction", ProviderConformanceProfile.MappingClass.PORTABLE);
        mappings.put("read-receipt", ProviderConformanceProfile.MappingClass.PORTABLE);
        mappings.put("typing", ProviderConformanceProfile.MappingClass.PORTABLE);
        mappings.put("encrypted-history", ProviderConformanceProfile.MappingClass.ARCHIVE_ONLY);
        return new ProviderConformanceProfile(
                "chat",
                "matrix-synapse-appservice",
                Set.of(
                        "joined-conversations",
                        "timeline",
                        "send",
                        "reactions",
                        "redactions",
                        "conversation",
                        "create-conversation",
                        "membership",
                        "read-receipts",
                        "typing",
                        "changes",
                        "idempotent-send",
                        "durable-outbox",
                        "callback-deduplication"),
                mappings,
                false,
                true,
                true);
    }

    @Override
    public ChatConversations joinedConversations(ChatRequestContext context) {
        return store.joinedConversations(context);
    }

    @Override
    public ChatCursor currentCursor(ChatRequestContext context) {
        return store.currentCursor(context);
    }

    @Override
    public ChatMessages timeline(
            ChatRequestContext context,
            ConversationId conversationId,
            ChatCursor cursor,
            int limit) {
        return store.timeline(context, conversationId, cursor, limit);
    }

    @Override
    public ChatMessage send(
            ChatRequestContext context,
            ConversationId conversationId,
            ChatTransactionId transactionId,
            String body) {
        ChatTimelineEvent event = sendEvent(context, conversationId, transactionId, ChatEventContent.text(body));
        return new ChatMessage(event.eventId(), event.conversationId(), event.senderRef(), event.occurredAt(),
                event.content().body(), event.deliveryState(), List.of());
    }

    @Override
    public ChatTimeline timelineEvents(
            ChatRequestContext context,
            ConversationId conversationId,
            ChatCursor cursor,
            int limit) {
        return store.timelineEvents(context, conversationId, cursor, limit);
    }

    @Override
    public ChatTimelineEvent sendEvent(
            ChatRequestContext context,
            ConversationId conversationId,
            ChatTransactionId transactionId,
            ChatEventContent content) {
        CanonicalChatStore.PreparedEvent prepared = store.prepareEvent(context, conversationId, transactionId, content);
        if (prepared.committed()) {
            return prepared.event();
        }
        enforceRetryWindow(context.tenantId(), prepared.operationId());
        try {
            CanonicalChatStore.ProviderMapping actor = ensureActor(context);
            CanonicalChatStore.ProviderMapping room = requireRoom(context.tenantId(), conversationId);
            String providerRelationTarget = content.relation() == null
                    ? null
                    : requireEvent(context.tenantId(), content.relation().targetEventId()).providerRef();
            ChatSouthboundProvider.ProviderAck ack = provider.sendEvent(
                    actor.providerRef(), room.providerRef(), prepared.providerTransactionId(), content,
                    providerRelationTarget);
            return store.acknowledgeEvent(
                    context, prepared, provider.providerKey(), ack.providerRef(), ack.sourceVersion());
        } catch (SynapseProviderException exception) {
            throw failed(context.tenantId(), prepared.operationId(), exception);
        }
    }

    @Override
    public ChatRedactionReceipt redactEvent(
            ChatRequestContext context,
            ConversationId conversationId,
            ChatTransactionId transactionId,
            String eventId) {
        CanonicalChatStore.PreparedRedaction prepared = store.prepareRedaction(
                context, conversationId, transactionId, eventId);
        if (prepared.committed()) {
            return new ChatRedactionReceipt(
                    prepared.redactionEventId(),
                    prepared.event().eventId(),
                    prepared.event().conversationId(),
                    context.actorRef().value(),
                    prepared.occurredAt());
        }
        enforceRetryWindow(context.tenantId(), prepared.operationId());
        try {
            CanonicalChatStore.ProviderMapping actor = ensureActor(context);
            CanonicalChatStore.ProviderMapping room = requireRoom(context.tenantId(), conversationId);
            CanonicalChatStore.ProviderMapping event = requireEvent(context.tenantId(), eventId);
            ChatSouthboundProvider.ProviderAck ack = provider.redactEvent(
                    actor.providerRef(), room.providerRef(), event.providerRef(), prepared.providerTransactionId());
            return store.acknowledgeRedaction(
                    context, prepared, provider.providerKey(), ack.providerRef(), ack.sourceVersion());
        } catch (SynapseProviderException exception) {
            throw failed(context.tenantId(), prepared.operationId(), exception);
        }
    }

    @Override
    public ChatConversation createConversation(
            ChatRequestContext context,
            ChatTransactionId transactionId,
            String title,
            String kind,
            List<ChatResolvedIdentity> invitedIdentities,
            ChatEncryptionState initialEncryption) {
        String alias = "#" + properties.virtualUserPrefix() + UUID.randomUUID().toString().replace("-", "")
                + ":" + properties.requiredServerName();
        CanonicalChatStore.PreparedConversation prepared = store.prepareConversation(
                context,
                transactionId,
                title,
                kind,
                invitedIdentities,
                provider.providerKey(),
                alias,
                initialEncryption);
        if (prepared.committed()) {
            return store.conversation(context, prepared.conversationId());
        }
        enforceRetryWindow(context.tenantId(), prepared.operationId());
        try {
            CanonicalChatStore.ProviderMapping author = ensureActor(context);
            List<String> inviteRefs = prepared.invitedIdentities().stream()
                    .map(identity -> ensureActor(identity.providerRequestContext()).providerRef())
                    .toList();
            ChatSouthboundProvider.ProviderAck room = provider.createRoom(
                    author.providerRef(),
                    prepared.providerAliasIntent(),
                    title,
                    inviteRefs,
                    prepared.encryptionMode());
            return store.acknowledgeConversation(
                    context, prepared, provider.providerKey(), room.providerRef(), room.sourceVersion());
        } catch (SynapseProviderException exception) {
            throw failed(context.tenantId(), prepared.operationId(), exception);
        }
    }

    @Override
    public ChatConversation joinConversation(ChatRequestContext context, ConversationId conversationId) {
        CanonicalChatStore.PreparedMembership prepared = store.prepareMembership(context, conversationId, "joined");
        if (prepared.committed()) {
            return store.conversation(context, conversationId);
        }
        enforceRetryWindow(context.tenantId(), prepared.operationId());
        try {
            CanonicalChatStore.ProviderMapping actor = ensureActor(context);
            CanonicalChatStore.ProviderMapping room = requireRoom(context.tenantId(), conversationId);
            ChatSouthboundProvider.ProviderAck ack = provider.joinRoom(actor.providerRef(), room.providerRef());
            return store.acknowledgeMembership(
                    context, prepared, provider.providerKey(), ack.sourceVersion());
        } catch (SynapseProviderException exception) {
            throw failed(context.tenantId(), prepared.operationId(), exception);
        }
    }

    @Override
    public ChatConversation leaveConversation(ChatRequestContext context, ConversationId conversationId) {
        CanonicalChatStore.PreparedMembership prepared = store.prepareMembership(context, conversationId, "left");
        if (prepared.committed()) {
            throw new IllegalStateException("The Chat membership has already left this conversation.");
        }
        enforceRetryWindow(context.tenantId(), prepared.operationId());
        try {
            CanonicalChatStore.ProviderMapping actor = ensureActor(context);
            CanonicalChatStore.ProviderMapping room = requireRoom(context.tenantId(), conversationId);
            ChatSouthboundProvider.ProviderAck ack = provider.leaveRoom(actor.providerRef(), room.providerRef());
            return store.acknowledgeMembership(
                    context, prepared, provider.providerKey(), ack.sourceVersion());
        } catch (SynapseProviderException exception) {
            throw failed(context.tenantId(), prepared.operationId(), exception);
        }
    }

    @Override
    public ChatConversation conversation(ChatRequestContext context, ConversationId conversationId) {
        return store.conversation(context, conversationId);
    }

    @Override
    public ChatConversation enableEncryption(
            ChatRequestContext context,
            ConversationId conversationId,
            String algorithm) {
        CanonicalChatStore.PreparedEncryption prepared = store.prepareEncryption(context, conversationId, algorithm);
        if (prepared.committed()) {
            return store.conversation(context, conversationId);
        }
        enforceRetryWindow(context.tenantId(), prepared.operationId());
        try {
            CanonicalChatStore.ProviderMapping actor = ensureActor(context);
            CanonicalChatStore.ProviderMapping room = requireRoom(context.tenantId(), conversationId);
            ChatSouthboundProvider.ProviderAck ack = provider.enableEncryption(
                    actor.providerRef(), room.providerRef(), prepared.providerTransactionId(), algorithm);
            return store.acknowledgeEncryption(
                    context, prepared, provider.providerKey(), ack.providerRef(), ack.sourceVersion());
        } catch (SynapseProviderException exception) {
            throw failed(context.tenantId(), prepared.operationId(), exception);
        }
    }

    @Override
    public ChatReadReceipt markRead(ChatRequestContext context, ConversationId conversationId, String eventId) {
        try {
            store.requireEventAccess(context, conversationId, eventId);
            CanonicalChatStore.ProviderMapping actor = ensureActor(context);
            CanonicalChatStore.ProviderMapping room = requireRoom(context.tenantId(), conversationId);
            CanonicalChatStore.ProviderMapping event = requireEvent(context.tenantId(), eventId);
            provider.markRead(actor.providerRef(), room.providerRef(), event.providerRef());
            return store.saveReadReceipt(context, conversationId, eventId, clock.instant());
        } catch (SynapseProviderException exception) {
            throw new ChatProviderUnavailableException(exception.supportSafeCode(), exception.retryAt());
        }
    }

    @Override
    public ChatTypingIndicator setTyping(
            ChatRequestContext context,
            ConversationId conversationId,
            boolean typing,
            int timeoutMilliseconds) {
        store.conversation(context, conversationId);
        try {
            CanonicalChatStore.ProviderMapping actor = ensureActor(context);
            CanonicalChatStore.ProviderMapping room = requireRoom(context.tenantId(), conversationId);
            provider.setTyping(actor.providerRef(), room.providerRef(), typing, timeoutMilliseconds);
            int bounded = Math.max(0, Math.min(timeoutMilliseconds, 120_000));
            return new ChatTypingIndicator(conversationId.value(), context.actorRef().value(), typing,
                    clock.instant().plusMillis(typing ? bounded : 0));
        } catch (SynapseProviderException exception) {
            throw new ChatProviderUnavailableException(exception.supportSafeCode(), exception.retryAt());
        }
    }

    @Override
    public ChatChangeSet changes(ChatRequestContext context, ChatCursor cursor, int limit) {
        return store.changes(context, cursor, limit);
    }

    public CanonicalChatStore.ProviderMapping ensureActor(ChatRequestContext context) {
        String canonicalActor = actorCanonicalId(context);
        String providerUser = "@" + properties.virtualUserPrefix()
                + UUID.randomUUID().toString().replace("-", "") + ":" + properties.requiredServerName();
        CanonicalChatStore.ProviderMapping mapping = store.reserveMapping(
                context.tenantId(), provider.providerKey(), "actor", canonicalActor, providerUser, null);
        if ("acknowledged".equals(mapping.state())) {
            return mapping;
        }
        ChatSouthboundProvider.ProviderAck acknowledgement = provider.ensureVirtualUser(mapping.providerRef());
        return store.acknowledgeMapping(
                context.tenantId(), provider.providerKey(), "actor", canonicalActor,
                acknowledgement.providerRef(), acknowledgement.sourceVersion());
    }

    public CanonicalChatStore.ProviderMapping actorMapping(ChatRequestContext context) {
        return actorMappingIfPresent(context)
                .orElseThrow(() -> new IllegalArgumentException("canonical Chat actor mapping was not found"));
    }

    public java.util.Optional<CanonicalChatStore.ProviderMapping> actorMappingIfPresent(ChatRequestContext context) {
        return store.mapping(context.tenantId(), provider.providerKey(), "actor", actorCanonicalId(context));
    }

    public CanonicalChatStore canonicalStore() {
        return store;
    }

    public MatrixSynapseChatSouthboundAdapter southboundProvider() {
        return provider;
    }

    private CanonicalChatStore.ProviderMapping requireRoom(String tenantId, ConversationId conversationId) {
        return store.mapping(tenantId, provider.providerKey(), "conversation", conversationId.value())
                .filter(mapping -> "acknowledged".equals(mapping.state()) && mapping.providerRef() != null)
                .orElseThrow(() -> new ChatProviderUnavailableException("chat-provider-room-mapping-unavailable"));
    }

    private CanonicalChatStore.ProviderMapping requireEvent(String tenantId, String eventId) {
        return store.mapping(tenantId, provider.providerKey(), "event", eventId)
                .filter(mapping -> "acknowledged".equals(mapping.state()) && mapping.providerRef() != null)
                .orElseThrow(() -> new ChatProviderUnavailableException("chat-provider-event-mapping-unavailable"));
    }

    private String actorCanonicalId(ChatRequestContext context) {
        try {
            Map<String, String> identity = new LinkedHashMap<>();
            identity.put("issuer", context.identityIssuer());
            identity.put("actorRef", context.actorRef().value());
            return objectMapper.writeValueAsString(identity);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("canonical Chat actor could not be mapped", exception);
        }
    }

    private ChatProviderUnavailableException failed(
            String tenantId,
            String operationId,
            SynapseProviderException exception) {
        Instant retryAt = exception.retryAt() == null ? clock.instant().plusSeconds(60) : exception.retryAt();
        store.failOperation(tenantId, operationId, exception.supportSafeCode(), retryAt);
        return new ChatProviderUnavailableException(exception.supportSafeCode(), retryAt);
    }

    private void enforceRetryWindow(String tenantId, String operationId) {
        store.activeRetryWindow(tenantId, operationId, clock.instant())
                .ifPresent(window -> {
                    throw new ChatProviderUnavailableException(
                            window.supportSafeCode(),
                            window.retryAt());
                });
    }
}
