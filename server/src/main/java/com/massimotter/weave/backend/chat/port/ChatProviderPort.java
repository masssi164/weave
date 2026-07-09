package com.massimotter.weave.backend.chat.port;

import com.massimotter.weave.backend.chat.domain.ChatConversation;
import com.massimotter.weave.backend.chat.domain.ChatConversations;
import com.massimotter.weave.backend.chat.domain.ChatMessage;
import com.massimotter.weave.backend.chat.domain.ChatMessages;
import com.massimotter.weave.backend.portability.ProviderConformanceProfile;

public interface ChatProviderPort {

    boolean configured();

    ProviderConformanceProfile conformanceProfile();

    ChatConversations joinedConversations(String actorRef);

    ChatMessages timeline(String actorRef, String conversationId, String cursor, int limit);

    ChatMessage send(String actorRef, String conversationId, String transactionId, String body);

    ChatConversation conversation(String actorRef, String conversationId);
}
