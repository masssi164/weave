package com.massimotter.weave.backend.chat.port;

import com.massimotter.weave.backend.spaces.authorization.ContextPermission;
import org.springframework.security.oauth2.jwt.Jwt;

/** Authorization and canonical conversation lookup needed by conversation-attached future features. */
public interface ChatConversationAdmission {
    AuthorizedContext authorize(Jwt jwt, String capability, String operation, ContextPermission permission);
    String requireConversation(Jwt jwt, String conversationId);

    record AuthorizedContext(String tenantId, String contextId, String principalRef) {}
}
