package com.massimotter.weave.backend.chat.port;

import com.massimotter.weave.backend.chat.domain.ChatConversation;
import com.massimotter.weave.backend.chat.domain.ChatConversations;
import com.massimotter.weave.backend.chat.domain.ChatActorRef;
import com.massimotter.weave.backend.chat.domain.ChatChangeSet;
import com.massimotter.weave.backend.chat.domain.ChatCursor;
import com.massimotter.weave.backend.chat.domain.ChatMessage;
import com.massimotter.weave.backend.chat.domain.ChatResolvedIdentity;
import com.massimotter.weave.backend.chat.domain.ChatMessages;
import com.massimotter.weave.backend.chat.domain.ChatReadReceipt;
import com.massimotter.weave.backend.chat.domain.ChatRedactionReceipt;
import com.massimotter.weave.backend.chat.domain.ChatEventContent;
import com.massimotter.weave.backend.chat.domain.ChatEncryptionState;
import com.massimotter.weave.backend.chat.domain.ChatTimeline;
import com.massimotter.weave.backend.chat.domain.ChatTimelineEvent;
import com.massimotter.weave.backend.chat.domain.ChatTransactionId;
import com.massimotter.weave.backend.chat.domain.ChatTypingIndicator;
import com.massimotter.weave.backend.chat.domain.ChatRequestContext;
import com.massimotter.weave.backend.chat.domain.ConversationId;
import com.massimotter.weave.backend.portability.ProviderConformanceProfile;
import com.massimotter.weave.backend.portability.ProviderReadiness;

public interface ChatProviderPort {

    /** Stable southbound adapter key selected by the organization control plane. */
    String providerKey();

    default java.util.Set<String> providerSelectionKeys() {
        return java.util.Set.of(providerKey());
    }

    boolean configured();

    ProviderReadiness readiness();

    ProviderConformanceProfile conformanceProfile();

    ChatConversations joinedConversations(ChatRequestContext context);

    default ChatConversations joinedConversations(ChatActorRef actorRef) {
        return joinedConversations(ChatRequestContext.isolatedTest(actorRef));
    }

    ChatCursor currentCursor(ChatRequestContext context);

    default ChatCursor currentCursor(ChatActorRef actorRef) {
        return currentCursor(ChatRequestContext.isolatedTest(actorRef));
    }

    ChatMessages timeline(ChatRequestContext context, ConversationId conversationId, ChatCursor cursor, int limit);

    default ChatMessages timeline(ChatActorRef actorRef, ConversationId conversationId, ChatCursor cursor, int limit) {
        return timeline(ChatRequestContext.isolatedTest(actorRef), conversationId, cursor, limit);
    }

    ChatMessage send(
            ChatRequestContext context,
            ConversationId conversationId,
            ChatTransactionId transactionId,
            String body);

    default ChatMessage send(
            ChatActorRef actorRef,
            ConversationId conversationId,
            ChatTransactionId transactionId,
            String body) {
        return send(ChatRequestContext.isolatedTest(actorRef), conversationId, transactionId, body);
    }

    ChatTimeline timelineEvents(ChatRequestContext context, ConversationId conversationId, ChatCursor cursor, int limit);

    default ChatTimeline timelineEvents(
            ChatActorRef actorRef,
            ConversationId conversationId,
            ChatCursor cursor,
            int limit) {
        return timelineEvents(ChatRequestContext.isolatedTest(actorRef), conversationId, cursor, limit);
    }

    ChatTimelineEvent sendEvent(
            ChatRequestContext context,
            ConversationId conversationId,
            ChatTransactionId transactionId,
            ChatEventContent content);

    default ChatTimelineEvent sendEvent(
            ChatActorRef actorRef,
            ConversationId conversationId,
            ChatTransactionId transactionId,
            ChatEventContent content) {
        return sendEvent(ChatRequestContext.isolatedTest(actorRef), conversationId, transactionId, content);
    }

    ChatRedactionReceipt redactEvent(
            ChatRequestContext context,
            ConversationId conversationId,
            ChatTransactionId transactionId,
            String eventId);

    default ChatRedactionReceipt redactEvent(
            ChatActorRef actorRef,
            ConversationId conversationId,
            ChatTransactionId transactionId,
            String eventId) {
        return redactEvent(ChatRequestContext.isolatedTest(actorRef), conversationId, transactionId, eventId);
    }

    ChatConversation createConversation(
            ChatRequestContext context,
            ChatTransactionId transactionId,
            String title,
            String kind,
            java.util.List<ChatResolvedIdentity> invitedIdentities,
            ChatEncryptionState initialEncryption);

    default ChatConversation createConversation(
            ChatRequestContext context,
            ChatTransactionId transactionId,
            String title,
            String kind,
            java.util.List<ChatResolvedIdentity> invitedIdentities) {
        return createConversation(
                context,
                transactionId,
                title,
                kind,
                invitedIdentities,
                ChatEncryptionState.unencrypted());
    }

    default ChatConversation createConversation(
            ChatActorRef actorRef,
            ChatTransactionId transactionId,
            String title,
            String kind,
            java.util.List<ChatResolvedIdentity> invitedIdentities) {
        return createConversation(
                ChatRequestContext.isolatedTest(actorRef), transactionId, title, kind, invitedIdentities);
    }

    default ChatConversation createConversation(
            ChatActorRef actorRef,
            ChatTransactionId transactionId,
            String title,
            String kind,
            java.util.List<ChatResolvedIdentity> invitedIdentities,
            ChatEncryptionState initialEncryption) {
        return createConversation(
                ChatRequestContext.isolatedTest(actorRef),
                transactionId,
                title,
                kind,
                invitedIdentities,
                initialEncryption);
    }

    ChatConversation joinConversation(ChatRequestContext context, ConversationId conversationId);

    default ChatConversation joinConversation(ChatActorRef actorRef, ConversationId conversationId) {
        return joinConversation(ChatRequestContext.isolatedTest(actorRef), conversationId);
    }

    ChatConversation leaveConversation(ChatRequestContext context, ConversationId conversationId);

    default ChatConversation leaveConversation(ChatActorRef actorRef, ConversationId conversationId) {
        return leaveConversation(ChatRequestContext.isolatedTest(actorRef), conversationId);
    }

    ChatConversation conversation(ChatRequestContext context, ConversationId conversationId);

    default ChatConversation conversation(ChatActorRef actorRef, ConversationId conversationId) {
        return conversation(ChatRequestContext.isolatedTest(actorRef), conversationId);
    }

    ChatConversation enableEncryption(ChatRequestContext context, ConversationId conversationId, String algorithm);

    default ChatConversation enableEncryption(ChatActorRef actorRef, ConversationId conversationId, String algorithm) {
        return enableEncryption(ChatRequestContext.isolatedTest(actorRef), conversationId, algorithm);
    }

    ChatReadReceipt markRead(ChatRequestContext context, ConversationId conversationId, String eventId);

    default ChatReadReceipt markRead(ChatActorRef actorRef, ConversationId conversationId, String eventId) {
        return markRead(ChatRequestContext.isolatedTest(actorRef), conversationId, eventId);
    }

    ChatTypingIndicator setTyping(
            ChatRequestContext context,
            ConversationId conversationId,
            boolean typing,
            int timeoutMilliseconds);

    default ChatTypingIndicator setTyping(
            ChatActorRef actorRef,
            ConversationId conversationId,
            boolean typing,
            int timeoutMilliseconds) {
        return setTyping(ChatRequestContext.isolatedTest(actorRef), conversationId, typing, timeoutMilliseconds);
    }

    ChatChangeSet changes(ChatRequestContext context, ChatCursor cursor, int limit);

    default ChatChangeSet changes(ChatActorRef actorRef, ChatCursor cursor, int limit) {
        return changes(ChatRequestContext.isolatedTest(actorRef), cursor, limit);
    }
}
