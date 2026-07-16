package com.massimotter.weave.backend.chat.port;

import com.massimotter.weave.backend.chat.domain.ChatActorRef;
import com.massimotter.weave.backend.chat.domain.ChatChangeSet;
import com.massimotter.weave.backend.chat.domain.ChatConversation;
import com.massimotter.weave.backend.chat.domain.ChatConversations;
import com.massimotter.weave.backend.chat.domain.ChatCursor;
import com.massimotter.weave.backend.chat.domain.ChatEventContent;
import com.massimotter.weave.backend.chat.domain.ChatEncryptionState;
import com.massimotter.weave.backend.chat.domain.ChatMessage;
import com.massimotter.weave.backend.chat.domain.ChatResolvedIdentity;
import com.massimotter.weave.backend.chat.domain.ChatMessages;
import com.massimotter.weave.backend.chat.domain.ChatReadReceipt;
import com.massimotter.weave.backend.chat.domain.ChatRedactionReceipt;
import com.massimotter.weave.backend.chat.domain.ChatRequestContext;
import com.massimotter.weave.backend.chat.domain.ChatTimeline;
import com.massimotter.weave.backend.chat.domain.ChatTimelineEvent;
import com.massimotter.weave.backend.chat.domain.ChatTransactionId;
import com.massimotter.weave.backend.chat.domain.ConversationId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Durable canonical Chat boundary. Provider references accepted here are private
 * persistence values and must never be projected northbound or into diagnostics.
 */
public interface CanonicalChatStore {

    String persistencePosture();

    ChatConversations joinedConversations(ChatRequestContext context);

    ChatCursor currentCursor(ChatRequestContext context);

    ChatMessages timeline(ChatRequestContext context, ConversationId conversationId, ChatCursor cursor, int limit);

    ChatTimeline timelineEvents(ChatRequestContext context, ConversationId conversationId, ChatCursor cursor, int limit);

    ChatConversation conversation(ChatRequestContext context, ConversationId conversationId);

    PreparedConversation prepareConversation(
            ChatRequestContext context,
            ChatTransactionId transactionId,
            String title,
            String kind,
            List<ChatResolvedIdentity> invitedIdentities,
            String providerKey,
            String providerAliasIntent,
            ChatEncryptionState initialEncryption);

    ChatConversation acknowledgeConversation(
            ChatRequestContext context,
            PreparedConversation prepared,
            String providerKey,
            String providerRoomRef,
            String providerSourceVersion);

    PreparedMembership prepareMembership(
            ChatRequestContext context,
            ConversationId conversationId,
            String targetState);

    ChatConversation acknowledgeMembership(
            ChatRequestContext context,
            PreparedMembership prepared,
            String providerKey,
            String providerSourceVersion);

    PreparedEncryption prepareEncryption(
            ChatRequestContext context,
            ConversationId conversationId,
            String algorithm);

    ChatConversation acknowledgeEncryption(
            ChatRequestContext context,
            PreparedEncryption prepared,
            String providerKey,
            String providerEventRef,
            String providerSourceVersion);

    PreparedEvent prepareEvent(
            ChatRequestContext context,
            ConversationId conversationId,
            ChatTransactionId transactionId,
            ChatEventContent content);

    ChatTimelineEvent acknowledgeEvent(
            ChatRequestContext context,
            PreparedEvent prepared,
            String providerKey,
            String providerEventRef,
            String providerSourceVersion);

    PreparedRedaction prepareRedaction(
            ChatRequestContext context,
            ConversationId conversationId,
            ChatTransactionId transactionId,
            String eventId);

    ChatRedactionReceipt acknowledgeRedaction(
            ChatRequestContext context,
            PreparedRedaction prepared,
            String providerKey,
            String providerEventRef,
            String providerSourceVersion);

    void failOperation(String tenantId, String operationId, String supportSafeCode, Instant retryAt);

    Optional<RetryWindow> activeRetryWindow(String tenantId, String operationId, Instant observedAt);

    ChatReadReceipt saveReadReceipt(
            ChatRequestContext context,
            ConversationId conversationId,
            String eventId,
            Instant readAt);

    void requireEventAccess(ChatRequestContext context, ConversationId conversationId, String eventId);

    ChatChangeSet changes(ChatRequestContext context, ChatCursor cursor, int limit);

    ProviderMapping reserveMapping(
            String tenantId,
            String providerKey,
            String objectType,
            String canonicalObjectId,
            String providerRef,
            String mappingIntentRef);

    ProviderMapping acknowledgeMapping(
            String tenantId,
            String providerKey,
            String objectType,
            String canonicalObjectId,
            String providerRef,
            String providerSourceVersion);

    Optional<ProviderMapping> mapping(
            String tenantId,
            String providerKey,
            String objectType,
            String canonicalObjectId);

    Optional<ProviderMapping> mappingByProviderRef(
            String providerKey,
            String objectType,
            String providerRef);

    Optional<ProviderMapping> mappingByIntent(
            String providerKey,
            String objectType,
            String mappingIntentRef);

    Optional<String> contextId(String tenantId, ConversationId conversationId);

    /** Private provider references used only for isolated, support-safe convergence proof. */
    List<String> acknowledgedProviderEventRefs(
            String tenantId,
            ConversationId conversationId,
            String providerKey);

    CallbackStart beginCallback(String providerKey, String transactionId, String payloadDigest, int eventCount);

    CallbackEventResult recordCallbackEvent(String providerKey, ProviderCallbackEvent event);

    CallbackEventResult recordMalformedCallbackEvent(
            String providerKey,
            String transactionId,
            String eventDigest,
            String reasonCode);

    void completeCallback(String providerKey, String transactionId, int duplicateCount);

    long degradedMappingCount(String providerKey);

    EvidenceSnapshot evidence(String tenantId, ConversationId conversationId, String providerKey);

    record PreparedConversation(
            String operationId,
            ConversationId conversationId,
            String providerKey,
            String providerTransactionId,
            String providerAliasIntent,
            List<ChatResolvedIdentity> invitedIdentities,
            String encryptionMode,
            boolean committed) {
    }

    record PreparedMembership(
            String operationId,
            ConversationId conversationId,
            String providerTransactionId,
            String targetState,
            boolean committed) {
    }

    record PreparedEncryption(
            String operationId,
            ConversationId conversationId,
            String providerTransactionId,
            String algorithm,
            boolean committed) {
    }

    record PreparedEvent(
            String operationId,
            ChatTimelineEvent event,
            String providerTransactionId,
            boolean committed) {
    }

    record PreparedRedaction(
            String operationId,
            ChatTimelineEvent event,
            String providerTransactionId,
            String redactionEventId,
            Instant occurredAt,
            boolean committed) {
    }

    record ProviderMapping(
            String tenantId,
            String providerKey,
            String objectType,
            String canonicalObjectId,
            String providerRef,
            String mappingIntentRef,
            String providerSourceVersion,
            String state) {
    }

    record RetryWindow(String supportSafeCode, Instant retryAt) {
    }

    enum CallbackStart {
        NEW,
        RESUME,
        DUPLICATE
    }

    record ProviderCallbackEvent(
            String homeserverTransactionId,
            String providerTransactionId,
            String providerEventRef,
            String providerRoomRef,
            String providerSenderRef,
            String eventType,
            String stateKey,
            String providerRedactsRef,
            Map<String, Object> content,
            String providerSourceVersion) {
        public ProviderCallbackEvent {
            content = content == null ? Map.of() : Map.copyOf(content);
        }

        public ProviderCallbackEvent(
                String homeserverTransactionId,
                String providerTransactionId,
                String providerEventRef,
                String providerRoomRef,
                String providerSenderRef,
                String eventType,
                Map<String, Object> content,
                String providerSourceVersion) {
            this(homeserverTransactionId, providerTransactionId, providerEventRef, providerRoomRef,
                    providerSenderRef, eventType, null, null, content, providerSourceVersion);
        }
    }

    record CallbackEventResult(String state, String correlationHash) {
    }

    record EvidenceSnapshot(
            String persistencePosture,
            long canonicalConversationCount,
            long canonicalJoinedMemberCount,
            long canonicalCommittedEventCount,
            long canonicalEncryptedEventCount,
            long canonicalPlaintextEventCount,
            long pendingOperationCount,
            long failedOperationCount,
            long committedOperationCount,
            long bridgeLedgerCount,
            long callbackTransactionCount,
            long callbackDuplicateCount,
            long quarantineCount,
            long degradedMappingCount,
            Instant observedAt) {
    }
}
