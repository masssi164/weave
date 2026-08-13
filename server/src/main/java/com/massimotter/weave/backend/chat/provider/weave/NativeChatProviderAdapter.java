package com.massimotter.weave.backend.chat.provider.weave;

import com.massimotter.weave.backend.chat.domain.ChatChangeSet;
import com.massimotter.weave.backend.chat.domain.ChatConversation;
import com.massimotter.weave.backend.chat.domain.ChatConversations;
import com.massimotter.weave.backend.chat.domain.ChatCursor;
import com.massimotter.weave.backend.chat.domain.ChatEncryptionState;
import com.massimotter.weave.backend.chat.domain.ChatEventContent;
import com.massimotter.weave.backend.chat.domain.ChatMessage;
import com.massimotter.weave.backend.chat.domain.ChatMessages;
import com.massimotter.weave.backend.chat.domain.ChatReadReceipt;
import com.massimotter.weave.backend.chat.domain.ChatRedactionReceipt;
import com.massimotter.weave.backend.chat.domain.ChatRequestContext;
import com.massimotter.weave.backend.chat.domain.ChatResolvedIdentity;
import com.massimotter.weave.backend.chat.domain.ChatTimeline;
import com.massimotter.weave.backend.chat.domain.ChatTimelineEvent;
import com.massimotter.weave.backend.chat.domain.ChatTransactionId;
import com.massimotter.weave.backend.chat.domain.ChatTypingIndicator;
import com.massimotter.weave.backend.chat.domain.ConversationId;
import com.massimotter.weave.backend.chat.port.CanonicalChatStore;
import com.massimotter.weave.backend.chat.port.ChatProviderPort;
import com.massimotter.weave.backend.portability.ProviderConformanceProfile;
import com.massimotter.weave.backend.portability.ProviderReadiness;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Provider-free Chat adapter backed only by Weave's canonical relational store.
 *
 * <p>This adapter deliberately creates no provider mapping, bridge-ledger row,
 * provider identifier, or southbound call. Client-side encryption remains
 * client-owned; Server persists only the opaque canonical envelope.</p>
 */
public final class NativeChatProviderAdapter implements ChatProviderPort {

    public static final String PROVIDER_KEY = "weave-native";
    private static final String PERSISTENCE_POSTURE = "durable-relational-jpa-code-first";

    private final CanonicalChatStore store;
    private final Clock clock;

    public NativeChatProviderAdapter(CanonicalChatStore store, Clock clock) {
        if (store == null || !PERSISTENCE_POSTURE.equals(store.persistencePosture())) {
            throw new IllegalStateException("Native Chat requires durable canonical JPA storage.");
        }
        this.store = store;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public String providerKey() {
        return PROVIDER_KEY;
    }

    @Override
    public boolean configured() {
        return PERSISTENCE_POSTURE.equals(store.persistencePosture());
    }

    @Override
    public ProviderReadiness readiness() {
        return configured()
                ? ProviderReadiness.ready("chat-native-canonical-jpa-ready")
                : ProviderReadiness.degraded("chat-native-canonical-jpa-unavailable");
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
        mappings.put("encrypted-history", ProviderConformanceProfile.MappingClass.PORTABLE);
        return new ProviderConformanceProfile(
                "chat",
                PROVIDER_KEY,
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
                        "opaque-encrypted-events"),
                mappings,
                true,
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
        ChatTimelineEvent event = sendEvent(
                context, conversationId, transactionId, ChatEventContent.text(body));
        return new ChatMessage(
                event.eventId(),
                event.conversationId(),
                event.senderRef(),
                event.occurredAt(),
                event.content().body(),
                event.deliveryState(),
                List.of());
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
        CanonicalChatStore.PreparedEvent prepared = store.prepareEvent(
                context, conversationId, transactionId, content);
        return prepared.committed() ? prepared.event() : store.commitEvent(context, prepared);
    }

    @Override
    public ChatRedactionReceipt redactEvent(
            ChatRequestContext context,
            ConversationId conversationId,
            ChatTransactionId transactionId,
            String eventId) {
        CanonicalChatStore.PreparedRedaction prepared = store.prepareRedaction(
                context, conversationId, transactionId, eventId);
        if (!prepared.committed()) {
            return store.commitRedaction(context, prepared);
        }
        return new ChatRedactionReceipt(
                prepared.redactionEventId(),
                prepared.event().eventId(),
                prepared.event().conversationId(),
                context.actorRef().value(),
                prepared.occurredAt());
    }

    @Override
    public ChatConversation createConversation(
            ChatRequestContext context,
            ChatTransactionId transactionId,
            String title,
            String kind,
            List<ChatResolvedIdentity> invitedIdentities,
            ChatEncryptionState initialEncryption) {
        CanonicalChatStore.PreparedConversation prepared = store.prepareConversation(
                context,
                transactionId,
                title,
                kind,
                invitedIdentities,
                PROVIDER_KEY,
                null,
                initialEncryption);
        return prepared.committed()
                ? store.conversation(context, prepared.conversationId())
                : store.commitConversation(context, prepared);
    }

    @Override
    public ChatConversation joinConversation(
            ChatRequestContext context,
            ConversationId conversationId) {
        CanonicalChatStore.PreparedMembership prepared = store.prepareMembership(
                context, conversationId, "joined");
        return prepared.committed()
                ? store.conversation(context, conversationId)
                : store.commitMembership(context, prepared);
    }

    @Override
    public ChatConversation leaveConversation(
            ChatRequestContext context,
            ConversationId conversationId) {
        CanonicalChatStore.PreparedMembership prepared = store.prepareMembership(
                context, conversationId, "left");
        if (prepared.committed()) {
            throw new IllegalStateException("The Chat membership has already left this conversation.");
        }
        return store.commitMembership(context, prepared);
    }

    @Override
    public ChatConversation conversation(
            ChatRequestContext context,
            ConversationId conversationId) {
        return store.conversation(context, conversationId);
    }

    @Override
    public ChatConversation enableEncryption(
            ChatRequestContext context,
            ConversationId conversationId,
            String algorithm) {
        CanonicalChatStore.PreparedEncryption prepared = store.prepareEncryption(
                context, conversationId, algorithm);
        return prepared.committed()
                ? store.conversation(context, conversationId)
                : store.commitEncryption(context, prepared);
    }

    @Override
    public ChatReadReceipt markRead(
            ChatRequestContext context,
            ConversationId conversationId,
            String eventId) {
        store.requireEventAccess(context, conversationId, eventId);
        return store.saveReadReceipt(context, conversationId, eventId, clock.instant());
    }

    @Override
    public ChatTypingIndicator setTyping(
            ChatRequestContext context,
            ConversationId conversationId,
            boolean typing,
            int timeoutMilliseconds) {
        store.conversation(context, conversationId);
        int bounded = Math.max(0, Math.min(timeoutMilliseconds, 120_000));
        return new ChatTypingIndicator(
                conversationId.value(),
                context.actorRef().value(),
                typing,
                clock.instant().plusMillis(typing ? bounded : 0));
    }

    @Override
    public ChatChangeSet changes(
            ChatRequestContext context,
            ChatCursor cursor,
            int limit) {
        return store.changes(context, cursor, limit);
    }
}
