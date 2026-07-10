package com.massimotter.weave.backend.chat.adapter;

import com.massimotter.weave.backend.chat.domain.ChatConversation;
import com.massimotter.weave.backend.chat.domain.ChatConversations;
import com.massimotter.weave.backend.chat.domain.ChatActorRef;
import com.massimotter.weave.backend.chat.domain.ChatChange;
import com.massimotter.weave.backend.chat.domain.ChatChangeSet;
import com.massimotter.weave.backend.chat.domain.ChatCursor;
import com.massimotter.weave.backend.chat.domain.ChatEncryptionState;
import com.massimotter.weave.backend.chat.domain.ChatEventContent;
import com.massimotter.weave.backend.chat.domain.ChatEventKind;
import com.massimotter.weave.backend.chat.domain.ChatHistoryPolicy;
import com.massimotter.weave.backend.chat.domain.ChatMemberState;
import com.massimotter.weave.backend.chat.domain.ChatMembership;
import com.massimotter.weave.backend.chat.domain.ChatMessage;
import com.massimotter.weave.backend.chat.domain.ChatMessages;
import com.massimotter.weave.backend.chat.domain.ChatReadReceipt;
import com.massimotter.weave.backend.chat.domain.ChatTimeline;
import com.massimotter.weave.backend.chat.domain.ChatTimelineEvent;
import com.massimotter.weave.backend.chat.domain.ChatTransactionId;
import com.massimotter.weave.backend.chat.domain.ChatTypingIndicator;
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
    private final Map<String, ChatTimelineEvent> transactions = new ConcurrentHashMap<>();
    private final Map<String, ChatConversation> conversationTransactions = new ConcurrentHashMap<>();
    private final Map<String, ChatReadReceipt> readReceipts = new ConcurrentHashMap<>();
    private final Map<String, ChatTypingIndicator> typingIndicators = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<ChatChange> changes = new CopyOnWriteArrayList<>();
    private final AtomicLong revision = new AtomicLong(1);

    public WeaveCanonicalChatAdapter() {
        ConversationState general = new ConversationState(
                "channel-general",
                "General",
                "channel",
                true,
                new ConcurrentHashMap<>(),
                new CopyOnWriteArrayList<>());
        general.events().add(new ChatTimelineEvent(
                "msg-1",
                general.conversationId(),
                "user:alice",
                Instant.parse("2026-07-08T10:00:00Z"),
                ChatEventContent.text("Hello from Weave Chat"),
                "sent",
                false));
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
        mappings.put("thread", ProviderConformanceProfile.MappingClass.PORTABLE);
        mappings.put("reaction", ProviderConformanceProfile.MappingClass.PORTABLE);
        mappings.put("read-receipt", ProviderConformanceProfile.MappingClass.PORTABLE);
        mappings.put("typing", ProviderConformanceProfile.MappingClass.PORTABLE);
        mappings.put("encrypted-history", ProviderConformanceProfile.MappingClass.UNSUPPORTED);
        return new ProviderConformanceProfile(
                "chat",
                "weave-canonical-chat",
                Set.of(
                        "joined-conversations",
                        "timeline",
                        "send",
                        "reactions",
                        "redactions",
                        "conversation",
                        "create-conversation",
                        "membership",
                        "read-receipts",
                        "typing",
                        "changes",
                        "idempotent-send"),
                mappings,
                true,
                true,
                true);
    }

    @Override
    public ChatConversations joinedConversations(ChatActorRef actorRef) {
        List<ChatConversation> result = conversations.values().stream()
                .filter(conversation -> conversation.openToWorkspace()
                        || "joined".equals(conversation.membershipStates().get(actorRef.value())))
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
        List<ChatMessage> messages = conversation.events().stream()
                .filter(event -> event.content().kind() == ChatEventKind.MESSAGE)
                .sorted(Comparator.comparing(ChatTimelineEvent::occurredAt))
                .skip(Math.max(0, conversation.events().size() - boundedLimit))
                .map(this::message)
                .toList();
        return new ChatMessages(null, conversation.conversationId(), messages);
    }

    @Override
    public ChatMessage send(
            ChatActorRef actorRef,
            ConversationId conversationId,
            ChatTransactionId transactionId,
            String body) {
        return message(sendEvent(actorRef, conversationId, transactionId, ChatEventContent.text(body)));
    }

    @Override
    public ChatTimeline timelineEvents(
            ChatActorRef actorRef,
            ConversationId conversationId,
            ChatCursor cursor,
            int limit) {
        ConversationState conversation = requireConversation(conversationId.value());
        requireJoined(conversation, actorRef);
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        List<ChatTimelineEvent> events = conversation.events().stream()
                .sorted(Comparator.comparing(ChatTimelineEvent::occurredAt))
                .skip(Math.max(0, conversation.events().size() - boundedLimit))
                .toList();
        return new ChatTimeline(conversation.conversationId(), events);
    }

    @Override
    public ChatTimelineEvent sendEvent(
            ChatActorRef actorRef,
            ConversationId conversationId,
            ChatTransactionId transactionId,
            ChatEventContent content) {
        ConversationState conversation = requireConversation(conversationId.value());
        requireJoined(conversation, actorRef);
        String transactionKey = conversationId.value() + ":" + transactionId.value();
        return transactions.computeIfAbsent(transactionKey, ignored -> {
            String eventId = "event-" + UUID.nameUUIDFromBytes(transactionKey.getBytes(StandardCharsets.UTF_8));
            ChatTimelineEvent event = new ChatTimelineEvent(
                    eventId,
                    conversationId.value(),
                    actorRef.value(),
                    Instant.now(),
                    content,
                    "sent",
                    false);
            conversation.events().add(event);
            recordChange(conversationId, event, content.kind() == ChatEventKind.REACTION
                    ? "reaction.created"
                    : "message.created");
            return event;
        });
    }

    @Override
    public ChatTimelineEvent redactEvent(
            ChatActorRef actorRef,
            ConversationId conversationId,
            ChatTransactionId transactionId,
            String eventId) {
        ConversationState conversation = requireConversation(conversationId.value());
        requireJoined(conversation, actorRef);
        String transactionKey = conversationId.value() + ":redact:" + transactionId.value();
        return transactions.computeIfAbsent(transactionKey, ignored -> {
            for (int index = 0; index < conversation.events().size(); index++) {
                ChatTimelineEvent existing = conversation.events().get(index);
                if (existing.eventId().equals(eventId)) {
                    ChatTimelineEvent redacted = existing.redact();
                    conversation.events().set(index, redacted);
                    recordChange(conversationId, redacted, "event.redacted");
                    return redacted;
                }
            }
            throw new IllegalArgumentException("canonical chat event was not found");
        });
    }

    @Override
    public ChatConversation createConversation(
            ChatActorRef actorRef,
            ChatTransactionId transactionId,
            String title,
            String kind,
            List<ChatActorRef> invitedActors) {
        String transactionKey = actorRef.value() + ":create:" + transactionId.value();
        return conversationTransactions.computeIfAbsent(transactionKey, ignored -> {
            String conversationId = "room-" + UUID.nameUUIDFromBytes(transactionKey.getBytes(StandardCharsets.UTF_8));
            Map<String, String> memberships = new ConcurrentHashMap<>();
            memberships.put(actorRef.value(), "joined");
            for (ChatActorRef invited : invitedActors == null ? List.<ChatActorRef>of() : invitedActors) {
                memberships.put(invited.value(), "joined");
            }
            ConversationState state = new ConversationState(
                    conversationId,
                    requireText(title, "conversation title"),
                    requireText(kind, "conversation kind"),
                    false,
                    memberships,
                    new CopyOnWriteArrayList<>());
            conversations.put(conversationId, state);
            revision.incrementAndGet();
            return conversation(state, actorRef);
        });
    }

    @Override
    public ChatConversation joinConversation(ChatActorRef actorRef, ConversationId conversationId) {
        ConversationState conversation = requireConversation(conversationId.value());
        conversation.membershipStates().put(actorRef.value(), "joined");
        revision.incrementAndGet();
        return conversation(conversation, actorRef);
    }

    @Override
    public ChatConversation leaveConversation(ChatActorRef actorRef, ConversationId conversationId) {
        ConversationState conversation = requireConversation(conversationId.value());
        conversation.membershipStates().put(actorRef.value(), "left");
        revision.incrementAndGet();
        return conversation(conversation, actorRef);
    }

    @Override
    public ChatConversation conversation(ChatActorRef actorRef, ConversationId conversationId) {
        return conversation(requireConversation(conversationId.value()), actorRef);
    }

    @Override
    public ChatReadReceipt markRead(ChatActorRef actorRef, ConversationId conversationId, String eventId) {
        ConversationState conversation = requireConversation(conversationId.value());
        requireJoined(conversation, actorRef);
        if (conversation.events().stream().noneMatch(event -> event.eventId().equals(eventId))) {
            throw new IllegalArgumentException("canonical chat event was not found");
        }
        ChatReadReceipt receipt = new ChatReadReceipt(
                conversationId.value(),
                actorRef.value(),
                eventId,
                Instant.now());
        readReceipts.put(conversationId.value() + ":" + actorRef.value(), receipt);
        return receipt;
    }

    @Override
    public ChatTypingIndicator setTyping(
            ChatActorRef actorRef,
            ConversationId conversationId,
            boolean typing,
            int timeoutMilliseconds) {
        ConversationState conversation = requireConversation(conversationId.value());
        requireJoined(conversation, actorRef);
        int boundedTimeout = Math.max(0, Math.min(timeoutMilliseconds, 120_000));
        ChatTypingIndicator indicator = new ChatTypingIndicator(
                conversationId.value(),
                actorRef.value(),
                typing,
                Instant.now().plusMillis(typing ? boundedTimeout : 0));
        String key = conversationId.value() + ":" + actorRef.value();
        if (typing) {
            typingIndicators.put(key, indicator);
        } else {
            typingIndicators.remove(key);
        }
        return indicator;
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
        Instant updatedAt = state.events().stream()
                .map(ChatTimelineEvent::occurredAt)
                .max(Instant::compareTo)
                .orElse(Instant.EPOCH);
        List<ChatMembership> memberships = state.membershipStates().entrySet().stream()
                .map(entry -> membership(state, entry.getKey(), entry.getValue()))
                .toList();
        if (memberships.stream().noneMatch(value -> value.memberRef().equals(actorRef.value()))) {
            memberships = List.of(membership(state, actorRef.value(), state.openToWorkspace() ? "joined" : "left"));
        }
        return new ChatConversation(
                state.conversationId(),
                state.title(),
                state.kind(),
                ChatMemberState.READY,
                "Chat is available through the Weave workspace.",
                updatedAt,
                ChatEncryptionState.unencrypted(),
                HISTORY_POLICY,
                memberships,
                List.of());
    }

    private ChatMembership membership(ConversationState state, String actorRef, String membershipState) {
        return new ChatMembership(
                "membership-" + state.conversationId() + "-" + actorRef,
                state.conversationId(),
                actorRef,
                "member",
                membershipState,
                Instant.EPOCH,
                "joined".equals(membershipState) ? List.of("chat.read", "chat.send") : List.of());
    }

    private ChatMessage message(ChatTimelineEvent event) {
        return new ChatMessage(
                event.eventId(),
                event.conversationId(),
                event.senderRef(),
                event.occurredAt(),
                event.redacted() ? "" : event.content().body(),
                event.deliveryState(),
                List.of());
    }

    private void recordChange(ConversationId conversationId, ChatTimelineEvent event, String kind) {
        long sequence = revision.incrementAndGet();
        changes.add(new ChatChange(
                sequence,
                kind,
                conversationId,
                event.eventId(),
                event.occurredAt()));
    }

    private void requireJoined(ConversationState conversation, ChatActorRef actorRef) {
        if (!conversation.openToWorkspace()
                && !"joined".equals(conversation.membershipStates().get(actorRef.value()))) {
            throw new IllegalArgumentException("chat actor is not joined to the conversation");
        }
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
            boolean openToWorkspace,
            Map<String, String> membershipStates,
            CopyOnWriteArrayList<ChatTimelineEvent> events) {
    }
}
