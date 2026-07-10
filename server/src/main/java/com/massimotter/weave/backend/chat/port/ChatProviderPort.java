package com.massimotter.weave.backend.chat.port;

import com.massimotter.weave.backend.chat.domain.ChatConversation;
import com.massimotter.weave.backend.chat.domain.ChatConversations;
import com.massimotter.weave.backend.chat.domain.ChatActorRef;
import com.massimotter.weave.backend.chat.domain.ChatChangeSet;
import com.massimotter.weave.backend.chat.domain.ChatCursor;
import com.massimotter.weave.backend.chat.domain.ChatMessage;
import com.massimotter.weave.backend.chat.domain.ChatMessages;
import com.massimotter.weave.backend.chat.domain.ChatReadReceipt;
import com.massimotter.weave.backend.chat.domain.ChatEventContent;
import com.massimotter.weave.backend.chat.domain.ChatTimeline;
import com.massimotter.weave.backend.chat.domain.ChatTimelineEvent;
import com.massimotter.weave.backend.chat.domain.ChatTransactionId;
import com.massimotter.weave.backend.chat.domain.ChatTypingIndicator;
import com.massimotter.weave.backend.chat.domain.ConversationId;
import com.massimotter.weave.backend.portability.ProviderConformanceProfile;
import com.massimotter.weave.backend.portability.ProviderReadiness;

public interface ChatProviderPort {

    boolean configured();

    ProviderReadiness readiness();

    ProviderConformanceProfile conformanceProfile();

    ChatConversations joinedConversations(ChatActorRef actorRef);

    ChatCursor currentCursor(ChatActorRef actorRef);

    ChatMessages timeline(ChatActorRef actorRef, ConversationId conversationId, ChatCursor cursor, int limit);

    ChatMessage send(
            ChatActorRef actorRef,
            ConversationId conversationId,
            ChatTransactionId transactionId,
            String body);

    ChatTimeline timelineEvents(ChatActorRef actorRef, ConversationId conversationId, ChatCursor cursor, int limit);

    ChatTimelineEvent sendEvent(
            ChatActorRef actorRef,
            ConversationId conversationId,
            ChatTransactionId transactionId,
            ChatEventContent content);

    ChatTimelineEvent redactEvent(
            ChatActorRef actorRef,
            ConversationId conversationId,
            ChatTransactionId transactionId,
            String eventId);

    ChatConversation createConversation(
            ChatActorRef actorRef,
            ChatTransactionId transactionId,
            String title,
            String kind,
            java.util.List<ChatActorRef> invitedActors);

    ChatConversation joinConversation(ChatActorRef actorRef, ConversationId conversationId);

    ChatConversation leaveConversation(ChatActorRef actorRef, ConversationId conversationId);

    ChatConversation conversation(ChatActorRef actorRef, ConversationId conversationId);

    ChatReadReceipt markRead(ChatActorRef actorRef, ConversationId conversationId, String eventId);

    ChatTypingIndicator setTyping(
            ChatActorRef actorRef,
            ConversationId conversationId,
            boolean typing,
            int timeoutMilliseconds);

    ChatChangeSet changes(ChatActorRef actorRef, ChatCursor cursor, int limit);
}
