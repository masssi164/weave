package com.massimotter.weave.backend.chat;

import com.massimotter.weave.backend.chat.domain.ChatRequestContext;
import com.massimotter.weave.backend.context.authz.ContextPermission;
import com.massimotter.weave.backend.identity.IdentityReferences;
import com.massimotter.weave.backend.spaces.port.SpaceAccessPort;
import org.springframework.stereotype.Component;

/** Current durable Space admission for canonical Chat identities. */
@Component
public final class DurableChatSpaceAccess {
    private static final String HUMAN_ACTOR_PREFIX = "user:";
    private final SpaceAccessPort spaces;

    public DurableChatSpaceAccess(SpaceAccessPort spaces) {
        this.spaces = spaces;
    }

    public boolean allows(ChatRequestContext context, ContextPermission permission) {
        String actorRef = context.actorRef().value();
        if (!actorRef.startsWith(HUMAN_ACTOR_PREFIX)
                || actorRef.length() == HUMAN_ACTOR_PREFIX.length()) {
            return false;
        }
        String accountRef;
        try {
            accountRef = IdentityReferences.accountId(context.identityIssuer(),
                    actorRef.substring(HUMAN_ACTOR_PREFIX.length()));
        } catch (IllegalArgumentException invalid) {
            return false;
        }
        return spaces.allows(context.tenantId(), context.contextId(), accountRef,
                switch (permission) {
                    case VIEW -> SpaceAccessPort.Permission.VIEW;
                    case EDIT -> SpaceAccessPort.Permission.EDIT;
                    case ADMIN -> SpaceAccessPort.Permission.ADMIN;
                });
    }
}
