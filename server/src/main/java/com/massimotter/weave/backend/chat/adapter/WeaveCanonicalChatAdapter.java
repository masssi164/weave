package com.massimotter.weave.backend.chat.adapter;

import com.massimotter.weave.backend.chat.domain.ChatConversation;
import com.massimotter.weave.backend.chat.domain.ChatConversations;
import com.massimotter.weave.backend.chat.domain.ChatActorRef;
import com.massimotter.weave.backend.chat.domain.ChatChange;
import com.massimotter.weave.backend.chat.domain.ChatChangeSet;
import com.massimotter.weave.backend.chat.domain.ChatCursor;
import com.massimotter.weave.backend.chat.domain.ChatEncryptionState;
import com.massimotter.weave.backend.chat.domain.ChatHistoryPolicy;
import com.massimotter.weave.backend.chat.domain.ChatMemberState;
import com.massimotter.weave.backend.chat.domain.ChatMembership;
import com.massimotter.weave.backend.chat.domain.ChatMessage;
import com.massimotter.weave.backend.chat.domain.ChatMessages;
import com.massimotter.weave.backend.chat.domain.ChatTransactionId;
import com.massimotter.weave.backend.chat.domain.ConversationId;
import com.massimotter.weave.backend.chat.port.ChatProviderPort;
import com.massimotter.weave.backend.portability.ProviderConformanceProfile;
import com.massimotter.weave.backend.portability.ProviderReadiness;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class WeaveCanonicalChatAdapter implements ChatProviderPort {

    private static final ChatHistoryPolicy HISTORY_POLICY = new ChatHistoryPolicy(
            "conversation_members",
            "organization_default_retention",
            false,
            true,
            List.of("Weave canonical history policy is independent of provider retention controls."));

    private final Map<String, ConversationState> conversations = new ConcurrentHashMap<>();
    private final Map<String, ChatMessage> transactions = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<ChatChange> changes = new CopyOnWriteArrayList<>();
    private final AtomicLong revision = new AtomicLong(1);

    public WeaveCanonicalChatAdapter() {
        ConversationState general = new ConversationState(
                "channel-general",
                "General",
                "channel",
                new CopyOnWriteArrayList<>());
        general.messages().add(new ChatMessage(
                "msg-1",
                general.conversationId(),
                "user:alice",
                Instant.parse("2026-07-08T10:00:00Z"),
                "Hello from Weave Chat",
                "sent",
                List.of()));
        conversations.put(general.conversationId(), general);
        changes.add(new ChatChange(
                1,
                "message.created",
                new ConversationId(general.conversationId()),
                "msg-1",
                Instant.parse("2026-07-08T10:00:00Z")));
    }

    @Override
    public boolean configured() {
        return true;
    }

    @Override
    public ProviderReadiness readiness() {
        return ProviderReadiness.ready("weave-canonical-chat-ready");
    }

    @Override
    public ProviderConformanceProfile conformanceProfile() {
        Map<String, ProviderConformanceProfile.MappingClass> mappings = new LinkedHashMap<>();
        mappings.put("conversation", ProviderConformanceProfile.MappingClass.PORTABLE);
        mappings.put("message", ProviderConformanceProfile.MappingClass.PORTABLE);
        mappings.put("membership", ProviderConformanceProfile.MappingClass.PORTABLE);
        mappings.put("attachment", ProviderConformanceProfile.MappingClass.ARCHIVE_ONLY);
        mappings.put("thread", ProviderConformanceProfile.MappingClass.UNSUPPORTED);
        mappings.put("reaction", ProviderConformanceProfile.MappingClass.UNSUPPORTED);
        mappings.put("encrypted-history", ProviderConformanceProfile.MappingClass.UNSUPPORTED);
        return new ProviderConformanceProfile(
                "chat",
                "weave-canonical-chat",
                Set.of("joined-conversations", "timeline", "send", "conversation", "changes", "idempotent-send"),
                mappings,
                true,
                true,
                true);
    }

    @Override
    public ChatConversations joinedConversations(ChatActorRef actorRef) {
        List<ChatConversation> result = conversations.values().stream()
                .map(conversation -> conversation(conversation, actorRef))
                .sorted(Comparator.comparing(ChatConversation::conversationId))
                .toList();
        return new ChatConversations(null, result);
    }

    @Override
    public ChatCursor currentCursor(ChatActorRef actorRef) {
        return new ChatCursor("chat-revision-" + revision.get());
    }

    @Override
    public ChatMessages timeline(
            ChatActorRef actorRef,
            ConversationId conversationId,
            ChatCursor cursor,
            int limit) {
        ConversationState conversation = requireConversation(conversationId.value());
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        List<ChatMessage> messages = conversation.messages().stream()
                .sorted(Comparator.comparing(ChatMessage::sentAt))
                .skip(Math.max(0, conversation.messages().size() - boundedLimit))
                .toList();
        return new ChatMessages(null, conversation.conversationId(), messages);
    }

    @Override
    public ChatMessage send(
            ChatActorRef actorRef,
            ConversationId conversationId,
            ChatTransactionId transactionId,
            String body) {
        ConversationState conversation = requireConversation(conversationId.value());
        String transactionKey = conversationId.value() + ":" + transactionId.value();
        return transactions.computeIfAbsent(transactionKey, ignored -> {
            String messageId = "msg-" + UUID.nameUUIDFromBytes(transactionKey.getBytes(StandardCharsets.UTF_8));
            ChatMessage message = new ChatMessage(
                    messageId,
                    conversationId.value(),
                    actorRef.value(),
                    Instant.now(),
                    requireText(body, "message body"),
                    "sent",
                    List.of());
            conversation.messages().add(message);
            long sequence = revision.incrementAndGet();
            changes.add(new ChatChange(
                    sequence,
                    "message.created",
                    conversationId,
                    message.messageId(),
                    message.sentAt()));
            return message;
        });
    }

    @Override
    public ChatConversation conversation(ChatActorRef actorRef, ConversationId conversationId) {
        return conversation(requireConversation(conversationId.value()), actorRef);
    }

    @Override
    public ChatChangeSet changes(ChatActorRef actorRef, ChatCursor cursor, int limit) {
        long after = cursor == null ? 0 : cursorSequence(cursor);
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        List<ChatChange> result = changes.stream()
                .filter(change -> change.sequence() > after)
                .limit(boundedLimit)
                .toList();
        return new ChatChangeSet(currentCursor(actorRef), result);
    }

    private ChatConversation conversation(ConversationState state, ChatActorRef actorRef) {
        Instant updatedAt = state.messages().stream()
                .map(ChatMessage::sentAt)
                .max(Instant::compareTo)
                .orElse(Instant.EPOCH);
        ChatMembership membership = new ChatMembership(
                "membership-" + state.conversationId() + "-" + actorRef.value(),
                state.conversationId(),
                actorRef.value(),
                "member",
                "joined",
                Instant.EPOCH,
                List.of("chat.read", "chat.send"));
        return new ChatConversation(
                state.conversationId(),
                state.title(),
                state.kind(),
                ChatMemberState.READY,
                "Chat is available through the Weave workspace.",
                updatedAt,
                ChatEncryptionState.unencrypted(),
                HISTORY_POLICY,
                List.of(membership),
                List.of());
    }

    private ConversationState requireConversation(String conversationId) {
        ConversationState conversation = conversations.get(conversationId);
        if (conversation == null) {
            throw new IllegalArgumentException("canonical chat conversation was not found");
        }
        return conversation;
    }

    private long cursorSequence(ChatCursor cursor) {
        String prefix = "chat-revision-";
        if (!cursor.value().startsWith(prefix)) {
            throw new IllegalArgumentException("chat cursor is invalid");
        }
        try {
            return Long.parseLong(cursor.value().substring(prefix.length()));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("chat cursor is invalid", exception);
        }
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private record ConversationState(
            String conversationId,
            String title,
            String kind,
            CopyOnWriteArrayList<ChatMessage> messages) {
    }
}
