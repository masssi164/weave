package com.massimotter.weave.backend.chat.spaces;

import com.massimotter.weave.backend.chat.application.ChatDomainFacadeService;
import com.massimotter.weave.backend.chat.domain.ChatAccessDeniedException;
import com.massimotter.weave.backend.chat.domain.ConversationId;
import com.massimotter.weave.backend.api.error.ApiErrorException;
import com.massimotter.weave.backend.chat.port.MatrixProtocolCodec;
import com.massimotter.weave.backend.spaces.relationship.SpaceRelationshipContribution;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ChatSpaceRelationshipContribution implements SpaceRelationshipContribution {
    private final ChatDomainFacadeService chat;
    private final MatrixProtocolCodec matrix;
    public ChatSpaceRelationshipContribution(ChatDomainFacadeService chat, MatrixProtocolCodec matrix) {
        this.chat = chat; this.matrix = matrix;
    }
    @Override public String domain() { return "chat"; }
    @Override public String relationPrefix() { return "relation:room:"; }
    @Override public String targetKind() { return "ROOM"; }
    @Override public boolean validTargetId(String value) {
        try { new ConversationId(value); return true; } catch (IllegalArgumentException invalid) { return false; }
    }

    @Override
    public List<Target> list(Context context, String afterTargetId, int limit) {
        var visible = new ArrayList<Target>();
        String after = afterTargetId;
        int scanned = 0;
        while (visible.size() < limit) {
            List<String> candidates;
            try {
                candidates = chat.joinedConversationRefsInSpace(context.jwt(), context.spaceRef(), after, 100);
            } catch (ApiErrorException denied) {
                if (denied.status() == HttpStatus.FORBIDDEN) break;
                throw new Unavailable();
            } catch (ChatAccessDeniedException denied) { break; }
            if (candidates == null || candidates.size() > 100) throw new Unavailable();
            for (String conversationId : candidates) {
                if (!validTargetId(conversationId) || conversationId.compareTo(after) <= 0) throw new Unavailable();
                after = conversationId;
                try {
                    var checked = chat.conversationInSpace(context.jwt(), context.spaceRef(), conversationId);
                    if (checked == null || !conversationId.equals(checked.conversationId())) throw new Unavailable();
                    String roomId = matrix.roomId(conversationId);
                    if (roomId == null || roomId.isBlank()) throw new Unavailable();
                    visible.add(new Target(conversationId, roomId));
                    if (visible.size() == limit) return List.copyOf(visible);
                } catch (ChatAccessDeniedException denied) {
                    // Membership may change between candidate selection and current readback.
                }
            }
            scanned += candidates.size();
            if (candidates.size() < 100) break;
            if (scanned >= 1000) throw new Unavailable();
        }
        return List.copyOf(visible);
    }
}
