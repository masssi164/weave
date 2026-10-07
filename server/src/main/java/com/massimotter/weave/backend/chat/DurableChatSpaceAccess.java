package com.massimotter.weave.backend.chat;

import com.massimotter.weave.backend.chat.domain.ChatRequestContext;
import com.massimotter.weave.backend.context.authz.ContextPermission;
import com.massimotter.weave.backend.identity.IdentityReferences;
import com.massimotter.weave.backend.spaces.port.SpaceAccessPort;
import java.util.List;
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
        String accountRef = accountRef(context);
        return accountRef != null && spaces.allows(context.tenantId(), context.contextId(), accountRef,
                switch (permission) {
                    case VIEW -> SpaceAccessPort.Permission.VIEW;
                    case EDIT -> SpaceAccessPort.Permission.EDIT;
                    case ADMIN -> SpaceAccessPort.Permission.ADMIN;
                });
    }

    public List<String> visibleSpaces(ChatRequestContext context, String afterSpaceRef, int limit) {
        String accountRef = accountRef(context);
        return accountRef == null ? List.of()
                : spaces.visibleSpaceRefs(context.tenantId(), accountRef, afterSpaceRef, limit);
    }

    private static String accountRef(ChatRequestContext context) {
        String actorRef = context.actorRef().value();
        if (!actorRef.startsWith(HUMAN_ACTOR_PREFIX)
                || actorRef.length() == HUMAN_ACTOR_PREFIX.length()) {
            return null;
        }
        try {
            return IdentityReferences.accountId(context.identityIssuer(),
                    actorRef.substring(HUMAN_ACTOR_PREFIX.length()));
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
}
