package com.massimotter.weave.backend.chat.port;

import com.massimotter.weave.backend.chat.domain.ChatConversation;
import com.massimotter.weave.backend.chat.domain.ChatConversations;
import com.massimotter.weave.backend.chat.domain.ChatActorRef;
import com.massimotter.weave.backend.chat.domain.ChatChangeSet;
import com.massimotter.weave.backend.chat.domain.ChatCursor;
import com.massimotter.weave.backend.chat.domain.ChatMessage;
import com.massimotter.weave.backend.chat.domain.ChatMessages;
import com.massimotter.weave.backend.chat.domain.ChatTransactionId;
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

    ChatConversation conversation(ChatActorRef actorRef, ConversationId conversationId);

    ChatChangeSet changes(ChatActorRef actorRef, ChatCursor cursor, int limit);
}
