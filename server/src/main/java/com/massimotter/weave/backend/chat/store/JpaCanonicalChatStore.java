package com.massimotter.weave.backend.chat.store;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.chat.domain.ChatAccessDeniedException;
import com.massimotter.weave.backend.chat.domain.ChatCallbackRetryRequiredException;
import com.massimotter.weave.backend.chat.domain.ChatActorRef;
import com.massimotter.weave.backend.chat.domain.ChatChange;
import com.massimotter.weave.backend.chat.domain.ChatChangeSet;
import com.massimotter.weave.backend.chat.domain.ChatConversation;
import com.massimotter.weave.backend.chat.domain.ChatConversations;
import com.massimotter.weave.backend.chat.domain.ChatCursor;
import com.massimotter.weave.backend.chat.domain.ChatEncryptedEnvelope;
import com.massimotter.weave.backend.chat.domain.ChatEncryptionState;
import com.massimotter.weave.backend.chat.domain.ChatEventContent;
import com.massimotter.weave.backend.chat.domain.ChatEventKind;
import com.massimotter.weave.backend.chat.domain.ChatHistoryPolicy;
import com.massimotter.weave.backend.chat.domain.ChatIdentityRef;
import com.massimotter.weave.backend.chat.domain.ChatResolvedIdentity;
import com.massimotter.weave.backend.chat.domain.ChatMemberState;
import com.massimotter.weave.backend.chat.domain.ChatMembership;
import com.massimotter.weave.backend.chat.domain.ChatMessage;
import com.massimotter.weave.backend.chat.domain.ChatMessages;
import com.massimotter.weave.backend.chat.domain.ChatProviderUnavailableException;
import com.massimotter.weave.backend.chat.domain.ChatReadReceipt;
import com.massimotter.weave.backend.chat.domain.ChatRedactionReceipt;
import com.massimotter.weave.backend.chat.domain.ChatRequestContext;
import com.massimotter.weave.backend.chat.domain.ChatTimeline;
import com.massimotter.weave.backend.chat.domain.ChatTimelineEvent;
import com.massimotter.weave.backend.chat.domain.ChatTransactionId;
import com.massimotter.weave.backend.chat.domain.ConversationId;
import com.massimotter.weave.backend.chat.port.CanonicalChatStore;
import com.massimotter.weave.backend.chat.provider.synapse.MatrixSynapseCompatibilityProfile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionTemplate;

public final class JpaCanonicalChatStore implements CanonicalChatStore {

    private static final String COMMITTED = "committed";
    private static final int DEFAULT_RECONCILIATION_ATTEMPTS = 3;
    private static final ChatHistoryPolicy HISTORY_POLICY = new ChatHistoryPolicy(
            "conversation_members",
            "organization_default_retention",
            false,
            true,
            List.of("Weave canonical history policy is independent of provider retention controls."));

    private final CanonicalChatJpaAuthority jpa;
    private final TransactionTemplate transactions;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final MatrixSynapseCompatibilityProfile compatibilityProfile;

    public JpaCanonicalChatStore(
            CanonicalChatJpaAuthority jpa,
            ObjectMapper objectMapper,
            Clock clock) {
        this(jpa, objectMapper, clock, MatrixSynapseCompatibilityProfile.pinned());
    }

    public JpaCanonicalChatStore(
            CanonicalChatJpaAuthority jpa,
            ObjectMapper objectMapper,
            Clock clock,
            MatrixSynapseCompatibilityProfile compatibilityProfile) {
        if (jpa == null) {
            throw new IllegalArgumentException("JpaCanonicalChatStore requires managed relational JPA repositories.");
        }
        if (compatibilityProfile == null) {
            throw new IllegalArgumentException("JpaCanonicalChatStore requires a Matrix/Synapse compatibility profile.");
        }
        this.jpa = jpa;
        this.transactions = new TransactionTemplate(jpa.transactionManager());
        this.objectMapper = objectMapper == null
                ? tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build()
                : objectMapper;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.compatibilityProfile = compatibilityProfile;
    }

    @Override
    public String persistencePosture() {
        return "durable-relational-jpa-code-first";
    }

    @Override
    public ChatConversations joinedConversations(ChatRequestContext context) {
        List<ChatConversationJpaEntity> rows = jpa.conversations().findJoined(
                context.tenantId(),
                context.contextId(),
                context.identityIssuer(),
                context.actorRef().value());
        if (rows.isEmpty()) {
            return new ChatConversations(null, List.of());
        }
        Map<String, List<ChatMembershipJpaEntity>> memberships = jpa.memberships()
                .findByIdPart1AndIdPart2InOrderByIdPart2AscIdPart4Asc(
                        context.tenantId(),
                        rows.stream().map(ChatConversationJpaEntity::conversationId).toList())
                .stream()
                .collect(Collectors.groupingBy(
                        ChatMembershipJpaEntity::conversationId,
                        LinkedHashMap::new,
                        Collectors.toList()));
        List<ChatConversation> conversations = rows.stream()
                .map(row -> mapConversation(
                        row,
                        memberships.getOrDefault(row.conversationId(), List.of())))
                .toList();
        return new ChatConversations(null, conversations);
    }

    @Override
    public ChatCursor currentCursor(ChatRequestContext context) {
        long value = jpa.changes().currentCursor(
                context.tenantId(),
                context.identityIssuer(),
                context.actorRef().value(),
                context.contextId());
        return new ChatCursor("chat-revision-" + value);
    }

    @Override
    public ChatMessages timeline(
            ChatRequestContext context,
            ConversationId conversationId,
            ChatCursor cursor,
            int limit) {
        ChatTimeline timeline = timelineEvents(context, conversationId, cursor, limit);
        List<ChatMessage> messages = timeline.events().stream()
                .filter(event -> event.content().kind() == ChatEventKind.MESSAGE)
                .map(this::message)
                .toList();
        return new ChatMessages(null, conversationId.value(), messages);
    }

    @Override
    public ChatTimeline timelineEvents(
            ChatRequestContext context,
            ConversationId conversationId,
            ChatCursor cursor,
            int limit) {
        requireJoined(context, conversationId);
        int bounded = Math.max(1, Math.min(limit, 100));
        long before = cursor == null ? Long.MAX_VALUE : cursorSequence(cursor);
        List<ChatTimelineEvent> reverse = jpa.events()
                .findVisibleBefore(
                        context.tenantId(),
                        conversationId.value(),
                        before,
                        PageRequest.of(0, bounded))
                .stream()
                .map(this::mapEvent)
                .toList();
        List<ChatTimelineEvent> ordered = new ArrayList<>(reverse);
        java.util.Collections.reverse(ordered);
        return new ChatTimeline(conversationId.value(), ordered);
    }

    @Override
    public ChatConversation conversation(ChatRequestContext context, ConversationId conversationId) {
        requireJoined(context, conversationId);
        return findConversation(context.tenantId(), conversationId, context.actorRef())
                .orElseThrow(() -> new IllegalArgumentException("canonical chat conversation was not found"));
    }

    @Override
    public PreparedConversation prepareConversation(
            ChatRequestContext context,
            ChatTransactionId transactionId,
            String title,
            String kind,
            List<ChatResolvedIdentity> invitedIdentities,
            String providerKey,
            String providerAliasIntent,
            ChatEncryptionState initialEncryption) {
        String safeTitle = requiredText(title, "conversation title", 512);
        String safeKind = requiredText(kind, "conversation kind", 64);
        String activeProviderKey = requiredText(providerKey, "Chat provider key", 64);
        ChatEncryptionState requestedEncryption = initialEncryption == null
                ? ChatEncryptionState.unencrypted()
                : initialEncryption;
        String encryptionMode = requestedEncryption.encrypted()
                ? ChatEncryptedEnvelope.MEGOLM_V1
                : "unencrypted";
        if (requestedEncryption.encrypted()
                && !ChatEncryptedEnvelope.MEGOLM_V1.equals(requestedEncryption.mode())) {
            throw new IllegalArgumentException("canonical initial Chat encryption mode is unsupported");
        }
        ChatIdentityRef author = ChatIdentityRef.from(context);
        List<ChatResolvedIdentity> invites = invitedIdentities == null ? List.of() : invitedIdentities.stream()
                .peek(identity -> {
                    if (!context.tenantId().equals(identity.tenantId())) {
                        throw new ChatAccessDeniedException();
                    }
                })
                .filter(identity -> !identity.identity().equals(author))
                .distinct()
                .sorted(Comparator.comparing(ChatResolvedIdentity::identityIssuer)
                        .thenComparing(identity -> identity.actorRef().value()))
                .toList();
        String operationId = operationId(context, "create-room", "new", transactionId.value());
        String conversationId = "room-" + UUID.nameUUIDFromBytes(operationId.getBytes(StandardCharsets.UTF_8));
        String digest = digestJson(Map.of(
                "title", safeTitle,
                "kind", safeKind,
                "encryptionMode", encryptionMode,
                "providerKey", activeProviderKey,
                "invites", invites.stream()
                        .map(identity -> identity.identityIssuer() + "\n" + identity.actorRef().value()
                                + "\n" + identity.authorizationPrincipalRef())
                        .toList()));
        return transactions.execute(status -> {
            Optional<OperationRow> existing = operation(context.tenantId(), operationId);
            if (existing.isPresent()) {
                requireSameDigest(existing.get(), digest);
                return new PreparedConversation(
                        operationId,
                        new ConversationId(existing.get().canonicalObjectId()),
                        activeProviderKey,
                        existing.get().providerTransactionId(),
                        existing.get().providerAliasIntent(),
                        invites,
                        encryptionMode,
                        COMMITTED.equals(existing.get().state()));
            }
            Instant now = clock.instant();
            jpa.conversations().save(ChatConversationJpaEntity.pending(
                    context.tenantId(),
                    context.contextId(),
                    conversationId,
                    safeTitle,
                    safeKind,
                    encryptionMode,
                    now));
            insertMembership(context.tenantId(), conversationId, context.identityIssuer(), context.actorRef(),
                    "joined", now, now);
            for (ChatResolvedIdentity invited : invites) {
                insertMembership(context.tenantId(), conversationId, invited.identityIssuer(), invited.actorRef(),
                        "invited", now, null);
            }
            String providerTxn = providerTransaction(operationId);
            insertOperation(context, operationId, "create-room", conversationId, conversationId,
                    transactionId.value(), providerTxn, providerAliasIntent, digest,
                    json(Map.of("conversationId", conversationId, "inviteCount", invites.size())), now);
            reserveMapping(
                    context.tenantId(),
                    activeProviderKey,
                    "conversation",
                    conversationId,
                    null,
                    providerAliasIntent);
            return new PreparedConversation(
                    operationId,
                    new ConversationId(conversationId),
                    activeProviderKey,
                    providerTxn,
                    providerAliasIntent,
                    invites,
                    encryptionMode,
                    false);
        });
    }

    @Override
    public ChatConversation acknowledgeConversation(
            ChatRequestContext context,
            PreparedConversation prepared,
            String providerKey,
            String providerRoomRef,
            String providerSourceVersion) {
        if (!prepared.providerKey().equals(providerKey)) {
            throw new IllegalArgumentException("Chat provider acknowledgment does not match the prepared operation.");
        }
        return transactions.execute(status -> {
            acknowledgeMapping(context.tenantId(), providerKey, "conversation", prepared.conversationId().value(),
                    providerRoomRef, providerSourceVersion);
            acknowledgeOperation(context.tenantId(), prepared.operationId());
            Instant now = clock.instant();
            ChatConversationJpaEntity conversation = requireConversationEntity(
                    context.tenantId(), prepared.conversationId());
            conversation.commit(now);
            recordChange(context.tenantId(), prepared.conversationId(), "conversation.created",
                    prepared.conversationId().value(), now);
            return findConversation(context.tenantId(), prepared.conversationId(), context.actorRef()).orElseThrow();
        });
    }

    @Override
    public PreparedMembership prepareMembership(
            ChatRequestContext context,
            ConversationId conversationId,
            String targetState) {
        requireConversationHealthy(context.tenantId(), conversationId);
        String normalized = switch (targetState) {
            case "joined", "join" -> "joined";
            case "left", "leave" -> "left";
            default -> throw new IllegalArgumentException("canonical chat membership state is invalid");
        };
        MembershipRow membership = membership(context, conversationId).orElse(null);
        boolean open = openToWorkspace(context.tenantId(), context.contextId(), conversationId);
        if ("joined".equals(normalized)) {
            if (membership != null && "joined".equals(membership.state())) {
                return preparedMembership(context, conversationId, normalized, true);
            }
            if (!open && (membership == null || !"invited".equals(membership.state()))) {
                throw new ChatAccessDeniedException();
            }
        } else if (membership == null || !("joined".equals(membership.state()) || "invited".equals(membership.state()))) {
            throw new ChatAccessDeniedException();
        }
        PreparedMembership prepared = preparedMembership(context, conversationId, normalized, false);
        transactions.executeWithoutResult(status -> {
            if (operation(context.tenantId(), prepared.operationId()).isEmpty()) {
                Instant now = clock.instant();
                insertOperation(context, prepared.operationId(), "membership-" + normalized,
                        conversationId.value(), context.actorRef().value(), prepared.providerTransactionId(),
                        prepared.providerTransactionId(), null, sha256(normalized),
                        json(Map.of("targetState", normalized)), now);
            }
        });
        return operation(context.tenantId(), prepared.operationId())
                .map(row -> new PreparedMembership(prepared.operationId(), conversationId,
                        row.providerTransactionId(), normalized, COMMITTED.equals(row.state())))
                .orElse(prepared);
    }

    @Override
    public ChatConversation acknowledgeMembership(
            ChatRequestContext context,
            PreparedMembership prepared,
            String providerKey,
            String providerSourceVersion) {
        return transactions.execute(status -> {
            Instant now = clock.instant();
            ChatQuadId membershipId = membershipId(context, prepared.conversationId());
            Optional<ChatMembershipJpaEntity> persisted = jpa.memberships().findById(membershipId);
            if (persisted.isPresent()) {
                persisted.orElseThrow().transition(prepared.targetState(), now);
            } else if ("joined".equals(prepared.targetState())) {
                insertMembership(context.tenantId(), prepared.conversationId().value(), context.identityIssuer(),
                        context.actorRef(), "joined", now, now);
            }
            acknowledgeOperation(context.tenantId(), prepared.operationId());
            recordLedger(context.tenantId(), providerKey, "outbound", prepared.providerTransactionId(), null,
                    context.actorRef().value(), providerSourceVersion, "acknowledged");
            recordChange(context.tenantId(), prepared.conversationId(), "membership." + prepared.targetState(),
                    context.actorRef().value(), now);
            if ("left".equals(prepared.targetState())) {
                return mapConversationWithoutAuthorization(context.tenantId(), prepared.conversationId(), context.actorRef())
                        .orElseThrow();
            }
            return findConversation(context.tenantId(), prepared.conversationId(), context.actorRef()).orElseThrow();
        });
    }

    @Override
    public PreparedEncryption prepareEncryption(
            ChatRequestContext context,
            ConversationId conversationId,
            String algorithm) {
        requireJoined(context, conversationId);
        if (!ChatEncryptedEnvelope.MEGOLM_V1.equals(algorithm)) {
            throw new IllegalArgumentException("canonical Chat encryption algorithm is unsupported");
        }
        String existingMode = encryptionMode(context.tenantId(), conversationId);
        String operationId = operationId(context, "enable-encryption", conversationId.value(), algorithm);
        String providerTxn = providerTransaction(operationId);
        if (algorithm.equals(existingMode)) {
            return new PreparedEncryption(operationId, conversationId, providerTxn, algorithm, true);
        }
        if (!"unencrypted".equals(existingMode)) {
            throw new IllegalArgumentException("canonical Chat encryption cannot be changed or disabled");
        }
        transactions.executeWithoutResult(status -> {
            if (operation(context.tenantId(), operationId).isEmpty()) {
                Instant now = clock.instant();
                insertOperation(context, operationId, "enable-encryption", conversationId.value(),
                        conversationId.value(), providerTxn, providerTxn, null, sha256(algorithm),
                        json(Map.of("algorithm", algorithm)), now);
            }
        });
        boolean committed = operation(context.tenantId(), operationId)
                .map(row -> COMMITTED.equals(row.state())).orElse(false);
        return new PreparedEncryption(operationId, conversationId, providerTxn, algorithm, committed);
    }

    @Override
    public ChatConversation acknowledgeEncryption(
            ChatRequestContext context,
            PreparedEncryption prepared,
            String providerKey,
            String providerEventRef,
            String providerSourceVersion) {
        return transactions.execute(status -> {
            Instant now = clock.instant();
            requireConversationEntity(context.tenantId(), prepared.conversationId())
                    .enableEncryption(prepared.algorithm(), now);
            acknowledgeOperation(context.tenantId(), prepared.operationId());
            recordLedger(context.tenantId(), providerKey, "outbound", prepared.providerTransactionId(),
                    providerEventRef, prepared.conversationId().value(), providerSourceVersion, "acknowledged");
            recordChange(context.tenantId(), prepared.conversationId(), "encryption.enabled",
                    prepared.conversationId().value(), now);
            return findConversation(context.tenantId(), prepared.conversationId(), context.actorRef()).orElseThrow();
        });
    }

    @Override
    public PreparedEvent prepareEvent(
            ChatRequestContext context,
            ConversationId conversationId,
            ChatTransactionId transactionId,
            ChatEventContent content) {
        requireJoined(context, conversationId);
        requireContentCompatible(context.tenantId(), conversationId, content);
        if (content != null && content.relation() != null) {
            requireEvent(context.tenantId(), conversationId, content.relation().targetEventId());
        }
        String operationId = operationId(context, "send-event", conversationId.value(), transactionId.value());
        String eventId = "event-" + UUID.nameUUIDFromBytes(operationId.getBytes(StandardCharsets.UTF_8));
        String contentJson = json(content);
        String digest = sha256(contentJson);
        return transactions.execute(status -> {
            Optional<OperationRow> existing = operation(context.tenantId(), operationId);
            if (existing.isPresent()) {
                requireSameDigest(existing.get(), digest);
                ChatTimelineEvent event = requireEvent(context.tenantId(), conversationId, existing.get().canonicalObjectId());
                return new PreparedEvent(operationId, event, existing.get().providerTransactionId(),
                        COMMITTED.equals(existing.get().state()));
            }
            Instant now = clock.instant();
            ChatTimelineEvent event = new ChatTimelineEvent(eventId, conversationId.value(),
                    context.actorRef().value(), now, content, "pending", false);
            jpa.events().save(ChatEventJpaEntity.create(
                    context.tenantId(),
                    conversationId.value(),
                    eventId,
                    allocateEventSequence(context.tenantId(), conversationId),
                    context.identityIssuer(),
                    context.actorRef().value(),
                    content.kind().value(),
                    contentJson,
                    now,
                    "pending"));
            String providerTxn = providerTransaction(operationId);
            insertOperation(context, operationId, "send-event", conversationId.value(), eventId,
                    transactionId.value(), providerTxn, null, digest,
                    json(Map.of("eventId", eventId, "eventKind", content.kind().value(), "content", content)), now);
            return new PreparedEvent(operationId, event, providerTxn, false);
        });
    }

    @Override
    public ChatTimelineEvent acknowledgeEvent(
            ChatRequestContext context,
            PreparedEvent prepared,
            String providerKey,
            String providerEventRef,
            String providerSourceVersion) {
        return transactions.execute(status -> {
            requireEventEntity(
                    context.tenantId(),
                    new ConversationId(prepared.event().conversationId()),
                    prepared.event().eventId()).commit();
            acknowledgeMapping(context.tenantId(), providerKey, "event", prepared.event().eventId(),
                    providerEventRef, providerSourceVersion);
            acknowledgeOperation(context.tenantId(), prepared.operationId());
            recordLedger(context.tenantId(), providerKey, "outbound", prepared.providerTransactionId(),
                    providerEventRef, prepared.event().eventId(), providerSourceVersion, "acknowledged");
            if (!jpa.changes().existsByTenantIdAndConversationIdAndCanonicalObjectId(
                    context.tenantId(),
                    prepared.event().conversationId(),
                    prepared.event().eventId())) {
                recordChange(context.tenantId(), new ConversationId(prepared.event().conversationId()),
                        prepared.event().content().kind() == ChatEventKind.REACTION
                                ? "reaction.created" : "message.created",
                        prepared.event().eventId(), prepared.event().occurredAt());
            }
            return requireEvent(context.tenantId(), new ConversationId(prepared.event().conversationId()),
                    prepared.event().eventId());
        });
    }

    @Override
    public PreparedRedaction prepareRedaction(
            ChatRequestContext context,
            ConversationId conversationId,
            ChatTransactionId transactionId,
            String eventId) {
        requireJoined(context, conversationId);
        ChatTimelineEvent event = requireEvent(context.tenantId(), conversationId, eventId);
        EventOwner owner = eventOwner(context.tenantId(), conversationId, eventId);
        if (!context.identityIssuer().equals(owner.identityIssuer())
                || !context.actorRef().value().equals(owner.actorRef())) {
            throw new ChatAccessDeniedException();
        }
        if (!COMMITTED.equals(event.deliveryState())) {
            throw new IllegalArgumentException("canonical chat event was not found");
        }
        String operationId = operationId(context, "redact-event", conversationId.value(), transactionId.value());
        String providerTxn = providerTransaction(operationId);
        String digest = sha256(eventId);
        transactions.executeWithoutResult(status -> {
            Optional<OperationRow> existing = operation(context.tenantId(), operationId);
            existing.ifPresent(row -> requireSameDigest(row, digest));
            if (existing.isEmpty()) {
                Instant now = clock.instant();
                insertOperation(context, operationId, "redact-event", conversationId.value(), eventId,
                        transactionId.value(), providerTxn, null, digest, json(Map.of("eventId", eventId)), now);
            }
        });
        boolean committed = operation(context.tenantId(), operationId)
                .map(row -> COMMITTED.equals(row.state())).orElse(false);
        return new PreparedRedaction(
                operationId,
                event,
                providerTxn,
                redactionEventId(operationId),
                clock.instant(),
                committed);
    }

    @Override
    public ChatRedactionReceipt acknowledgeRedaction(
            ChatRequestContext context,
            PreparedRedaction prepared,
            String providerKey,
            String providerEventRef,
            String providerSourceVersion) {
        return transactions.execute(status -> {
            requireEventEntity(
                    context.tenantId(),
                    new ConversationId(prepared.event().conversationId()),
                    prepared.event().eventId()).redact();
            acknowledgeMapping(
                    context.tenantId(),
                    providerKey,
                    "redaction",
                    prepared.redactionEventId(),
                    providerEventRef,
                    providerSourceVersion);
            acknowledgeOperation(context.tenantId(), prepared.operationId());
            recordLedger(context.tenantId(), providerKey, "outbound", prepared.providerTransactionId(),
                    providerEventRef, prepared.redactionEventId(), providerSourceVersion, "acknowledged");
            recordCallbackChangeIfAbsent(
                    context.tenantId(), new ConversationId(prepared.event().conversationId()),
                    "event.redacted", prepared.event().eventId(), providerKey, providerEventRef, clock.instant());
            return new ChatRedactionReceipt(
                    prepared.redactionEventId(),
                    prepared.event().eventId(),
                    prepared.event().conversationId(),
                    context.actorRef().value(),
                    prepared.occurredAt());
        });
    }

    @Override
    public void failOperation(String tenantId, String operationId, String supportSafeCode, Instant retryAt) {
        transactions.executeWithoutResult(status -> {
            Instant now = clock.instant();
            ChatPairId id = new ChatPairId(tenantId, operationId);
            jpa.operations().findById(id)
                    .ifPresent(operation -> operation.fail(safeCode(supportSafeCode), now));
            jpa.outbox().findById(id)
                    .ifPresent(outbox -> outbox.fail(safeCode(supportSafeCode), retryAt, now));
        });
    }

    @Override
    public Optional<RetryWindow> activeRetryWindow(
            String tenantId,
            String operationId,
            Instant observedAt) {
        Instant now = observedAt == null ? clock.instant() : observedAt;
        ChatPairId id = new ChatPairId(tenantId, operationId);
        return jpa.operations().findById(id)
                .filter(operation -> "failed_retryable".equals(operation.state()))
                .flatMap(operation -> jpa.outbox().findById(id)
                        .filter(outbox -> "pending".equals(outbox.state()))
                        .map(ChatOutboxJpaEntity::nextAttemptAt)
                        .filter(java.util.Objects::nonNull)
                        .map(retryAt -> new RetryWindow(
                                safeCode(operation.lastErrorCode()), retryAt)))
                .filter(window -> window.retryAt().isAfter(now));
    }

    @Override
    public ChatReadReceipt saveReadReceipt(
            ChatRequestContext context,
            ConversationId conversationId,
            String eventId,
            Instant readAt) {
        requireJoined(context, conversationId);
        requireEvent(context.tenantId(), conversationId, eventId);
        Instant timestamp = readAt == null ? clock.instant() : readAt;
        transactions.executeWithoutResult(status -> {
            ChatQuadId id = membershipId(context, conversationId);
            ChatReadReceiptJpaEntity receipt = jpa.receipts().findById(id)
                    .orElseGet(() -> ChatReadReceiptJpaEntity.create(
                            context.tenantId(),
                            conversationId.value(),
                            context.identityIssuer(),
                            context.actorRef().value(),
                            eventId,
                            timestamp));
            receipt.advance(eventId, timestamp);
            jpa.receipts().save(receipt);
        });
        return new ChatReadReceipt(conversationId.value(), context.actorRef().value(), eventId, timestamp);
    }

    @Override
    public void requireEventAccess(
            ChatRequestContext context,
            ConversationId conversationId,
            String eventId) {
        requireJoined(context, conversationId);
        ChatTimelineEvent event = requireEvent(context.tenantId(), conversationId, eventId);
        if (!COMMITTED.equals(event.deliveryState())) {
            throw new IllegalArgumentException("canonical chat event was not found");
        }
    }

    @Override
    public ChatChangeSet changes(ChatRequestContext context, ChatCursor cursor, int limit) {
        long after = cursor == null ? 0 : cursorSequence(cursor);
        int bounded = Math.max(1, Math.min(limit, 100));
        List<ChatChange> result = jpa.changes().findVisibleChanges(
                        context.tenantId(),
                        after,
                        context.identityIssuer(),
                        context.actorRef().value(),
                        context.contextId(),
                        PageRequest.of(0, bounded))
                .stream()
                .map(change -> new ChatChange(
                        change.sequence(),
                        change.kind(),
                        new ConversationId(change.conversationId()),
                        change.canonicalObjectId(),
                        change.occurredAt()))
                .toList();
        long delivered = result.isEmpty() ? after : result.getLast().sequence();
        return new ChatChangeSet(new ChatCursor("chat-revision-" + delivered), result);
    }

    @Override
    public ProviderMapping reserveMapping(
            String tenantId,
            String providerKey,
            String objectType,
            String canonicalObjectId,
            String providerRef,
            String mappingIntentRef) {
        return transactions.execute(status -> reserveMappingInTransaction(
                tenantId,
                providerKey,
                objectType,
                canonicalObjectId,
                providerRef,
                mappingIntentRef));
    }

    private ProviderMapping reserveMappingInTransaction(
            String tenantId,
            String providerKey,
            String objectType,
            String canonicalObjectId,
            String providerRef,
            String mappingIntentRef) {
        ChatQuadId id = mappingId(tenantId, providerKey, objectType, canonicalObjectId);
        if (!jpa.mappings().existsById(id)) {
            try {
                jpa.mappings().saveAndFlush(ChatProviderMappingJpaEntity.pending(
                        tenantId,
                        providerKey,
                        objectType,
                        canonicalObjectId,
                        providerRef,
                        mappingIntentRef,
                        clock.instant()));
            } catch (DataIntegrityViolationException race) {
                if (!jpa.mappings().existsById(id)) {
                    throw race;
                }
            }
        }
        return mapping(tenantId, providerKey, objectType, canonicalObjectId).orElseThrow();
    }

    @Override
    public ProviderMapping acknowledgeMapping(
            String tenantId,
            String providerKey,
            String objectType,
            String canonicalObjectId,
            String providerRef,
            String providerSourceVersion) {
        return transactions.execute(status -> acknowledgeMappingInTransaction(
                tenantId,
                providerKey,
                objectType,
                canonicalObjectId,
                providerRef,
                providerSourceVersion));
    }

    private ProviderMapping acknowledgeMappingInTransaction(
            String tenantId,
            String providerKey,
            String objectType,
            String canonicalObjectId,
            String providerRef,
            String providerSourceVersion) {
        ChatQuadId id = mappingId(tenantId, providerKey, objectType, canonicalObjectId);
        ChatProviderMappingJpaEntity mapping = jpa.mappings().findById(id).orElse(null);
        if (mapping == null) {
            reserveMappingInTransaction(
                    tenantId, providerKey, objectType, canonicalObjectId, providerRef, null);
            mapping = jpa.mappings().findById(id).orElseThrow();
        }
        mapping.acknowledge(providerRef, providerSourceVersion, clock.instant());
        return mapProviderMapping(mapping);
    }

    @Override
    public Optional<ProviderMapping> mapping(
            String tenantId,
            String providerKey,
            String objectType,
            String canonicalObjectId) {
        return jpa.mappings().findById(mappingId(tenantId, providerKey, objectType, canonicalObjectId))
                .map(this::mapProviderMapping);
    }

    @Override
    public Optional<ProviderMapping> mappingByProviderRef(
            String providerKey,
            String objectType,
            String providerRef) {
        return jpa.mappings().findFirstByIdPart2AndIdPart3AndProviderRef(
                        providerKey, objectType, providerRef)
                .map(this::mapProviderMapping);
    }

    @Override
    public Optional<ProviderMapping> mappingByIntent(
            String providerKey,
            String objectType,
            String mappingIntentRef) {
        return jpa.mappings().findFirstByIdPart2AndIdPart3AndMappingIntentRef(
                        providerKey, objectType, mappingIntentRef)
                .map(this::mapProviderMapping);
    }

    @Override
    public Optional<String> contextId(String tenantId, ConversationId conversationId) {
        return jpa.conversations()
                .findByIdAndLifecycleState(
                        new ChatPairId(tenantId, conversationId.value()), COMMITTED)
                .map(ChatConversationJpaEntity::contextId);
    }

    @Override
    public List<String> acknowledgedProviderEventRefs(
            String tenantId,
            ConversationId conversationId,
            String providerKey) {
        return jpa.mappings().findAcknowledgedEventRefs(
                providerKey, tenantId, conversationId.value());
    }

    @Override
    public CallbackStart beginCallback(String providerKey, String transactionId, String payloadDigest, int eventCount) {
        String safeProvider = requiredText(providerKey, "Chat provider key", 64);
        String safeTransaction = requiredText(transactionId, "homeserver transaction", 255);
        if (payloadDigest == null || !payloadDigest.matches("[0-9a-f]{64}") || eventCount < 0) {
            throw new IllegalArgumentException("Application Service semantic fingerprint is invalid.");
        }
        return transactions.execute(status -> {
            Optional<CallbackRow> existing = callbackRow(safeProvider, safeTransaction);
            if (existing.isPresent()) {
                return existingCallbackStart(
                        safeProvider, safeTransaction, payloadDigest, eventCount, existing.orElseThrow());
            }
            boolean claimed = jpa.claimCallback(
                    safeProvider,
                    safeTransaction,
                    payloadDigest,
                    eventCount,
                    compatibilityProfile.semanticFingerprintVersion(),
                    clock.instant());
            if (claimed) {
                return CallbackStart.NEW;
            }
            CallbackRow raced = callbackRow(safeProvider, safeTransaction)
                    .orElseThrow(ChatCallbackRetryRequiredException::new);
            return existingCallbackStart(safeProvider, safeTransaction, payloadDigest, eventCount, raced);
        });
    }

    private Optional<CallbackRow> callbackRow(String providerKey, String transactionId) {
        return jpa.callbacks().findById(new ChatPairId(providerKey, transactionId))
                .map(callback -> new CallbackRow(
                        callback.state(),
                        callback.payloadDigest(),
                        callback.eventCount(),
                        callback.fingerprintVersion()));
    }

    private CallbackStart existingCallbackStart(
            String providerKey,
            String transactionId,
            String payloadDigest,
            int eventCount,
            CallbackRow callback) {
        boolean sameSemanticSet = callback.eventCount() == eventCount
                && compatibilityProfile.semanticFingerprintVersion().equals(callback.fingerprintVersion())
                && constantTimeTextEquals(callback.payloadDigest(), payloadDigest);
        if (!sameSemanticSet || "semantic-mismatch".equals(callback.state())) {
            String mismatchHash = sha256(providerKey + "\u0000" + transactionId + "\u0000"
                    + callback.fingerprintVersion() + "\u0000" + callback.payloadDigest() + "\u0000"
                    + callback.eventCount() + "\u0000" + compatibilityProfile.semanticFingerprintVersion()
                    + "\u0000" + payloadDigest + "\u0000" + eventCount);
            requireCallback(providerKey, transactionId).semanticMismatch(mismatchHash);
            return CallbackStart.SEMANTIC_MISMATCH;
        }
        if ("completed".equals(callback.state())) {
            requireCallback(providerKey, transactionId).duplicate();
            return CallbackStart.DUPLICATE;
        }
        return CallbackStart.RESUME;
    }

    @Override
    public CallbackEventResult recordCallbackEvent(String providerKey, ProviderCallbackEvent event) {
        CallbackEventResult result = transactions.execute(status -> recordCallbackEventInTransaction(providerKey, event));
        if (result == null) {
            throw new IllegalStateException("Application Service callback transaction returned no result.");
        }
        return result;
    }

    private CallbackEventResult recordCallbackEventInTransaction(String providerKey, ProviderCallbackEvent event) {
        Optional<ProviderOperationEcho> operationEcho = providerOperation(event.providerTransactionId());
        if (operationEcho.isEmpty() && event.providerTransactionId() == null) {
            List<ProviderOperationEcho> fallbackEchoes = pendingProviderEchoes(providerKey, event);
            if (fallbackEchoes.size() > 1) {
                Optional<ProviderMapping> room = mappingByProviderRef(
                        providerKey, "conversation", event.providerRoomRef());
                if (room.isPresent()) {
                    return quarantineMappedConversation(
                            room.get().tenantId(), room.get().canonicalObjectId(), providerKey, event,
                            "provider-echo-correlation-ambiguous");
                }
                throw new ChatCallbackRetryRequiredException();
            }
            operationEcho = fallbackEchoes.stream().findFirst();
        }
        if (operationEcho.isPresent() && "redact-event".equals(operationEcho.get().operationType())) {
            return recordRedactionEcho(providerKey, event, operationEcho.get());
        }
        if (operationEcho.isPresent() && "send-event".equals(operationEcho.get().operationType())) {
            ProviderOperationEcho echo = operationEcho.get();
            Optional<ProviderMapping> expectedRoom = mapping(
                    echo.tenantId(), providerKey, "conversation", echo.conversationId());
            if (expectedRoom.isEmpty()
                    || !correlatableConversationMapping(expectedRoom.get())
                    || !event.providerRoomRef().equals(expectedRoom.get().providerRef())) {
                return quarantineMappedConversation(
                        echo.tenantId(), echo.conversationId(), providerKey, event, "provider-echo-room-mismatch");
            }
            Optional<ProviderMapping> sender = mappingByProviderRef(
                    providerKey, "actor", event.providerSenderRef());
            if (sender.isEmpty()
                    || !"acknowledged".equals(sender.get().state())
                    || !echo.tenantId().equals(sender.get().tenantId())) {
                return quarantineMappedConversation(
                        echo.tenantId(), echo.conversationId(), providerKey, event, "provider-echo-sender-mismatch");
            }
            ActorIdentity senderIdentity = parseActorKey(sender.get().canonicalObjectId());
            if (!echo.identityIssuer().equals(senderIdentity.issuer())
                    || !echo.actorRef().equals(senderIdentity.actorRef())) {
                return quarantineMappedConversation(
                        echo.tenantId(), echo.conversationId(), providerKey, event, "provider-echo-sender-mismatch");
            }
            String echoContextId = contextId(echo.tenantId(), new ConversationId(echo.conversationId()))
                    .orElseThrow(() -> new IllegalStateException("Canonical Chat context binding is missing."));
            ChatRequestContext echoContext = new ChatRequestContext(
                    echo.tenantId(), echoContextId, echo.identityIssuer(), new ChatActorRef(echo.actorRef()));
            try {
                requireJoinedMembership(echoContext, new ConversationId(echo.conversationId()));
            } catch (ChatAccessDeniedException exception) {
                return quarantineMappedConversation(
                        echo.tenantId(), echo.conversationId(), providerKey, event, "provider-echo-sender-not-authorized");
            }
            String expectedType = providerEventType(echo.content());
            if (!expectedType.equals(event.eventType())) {
                return quarantineMappedConversation(
                        echo.tenantId(), echo.conversationId(), providerKey, event, "provider-echo-type-mismatch");
            }
            if (!contentPolicyMatches(echo.tenantId(), new ConversationId(echo.conversationId()), echo.content())) {
                return quarantineMappedConversation(
                        echo.tenantId(), echo.conversationId(), providerKey, event, "provider-echo-policy-mismatch");
            }
            Map<String, Object> expectedContent = providerContent(
                    echo.tenantId(), providerKey, echo.content());
            if (!constantTimeDigestEquals(expectedContent, event.content())) {
                return quarantineMappedConversation(
                        echo.tenantId(), echo.conversationId(), providerKey, event, "provider-echo-content-mismatch");
            }
            Optional<ProviderMapping> existingProviderEvent = mappingByProviderRef(
                    providerKey, "event", event.providerEventRef());
            if (existingProviderEvent.isPresent()
                    && (!echo.tenantId().equals(existingProviderEvent.get().tenantId())
                    || !echo.canonicalObjectId().equals(existingProviderEvent.get().canonicalObjectId()))) {
                return quarantineMappedConversation(
                        echo.tenantId(), echo.conversationId(), providerKey, event, "provider-echo-event-mismatch");
            }
            requireEventEntity(
                    echo.tenantId(),
                    new ConversationId(echo.conversationId()),
                    echo.canonicalObjectId()).commit();
            acknowledgeMapping(echo.tenantId(), providerKey, "event", echo.canonicalObjectId(),
                    event.providerEventRef(), event.providerSourceVersion());
            acknowledgeOperation(echo.tenantId(), echo.operationId());
            recordLedger(echo.tenantId(), providerKey, "inbound-echo", echo.providerTransactionId(),
                    event.providerEventRef(), echo.canonicalObjectId(), event.providerSourceVersion(), "acknowledged");
            recordCallbackChangeIfAbsent(
                    echo.tenantId(),
                    new ConversationId(echo.conversationId()),
                    "message.created",
                    echo.canonicalObjectId(),
                    providerKey,
                    event.providerEventRef(),
                    clock.instant());
            return new CallbackEventResult(
                    "acknowledged-echo",
                    sha256(echo.tenantId() + ":" + echo.canonicalObjectId()));
        }
        Optional<ProviderMapping> roomMapping = mappingByProviderRef(providerKey, "conversation", event.providerRoomRef());
        if (roomMapping.isEmpty() || !correlatableConversationMapping(roomMapping.get())) {
            if (pendingCreateCouldOwnCallback(providerKey, event, operationEcho)) {
                throw new ChatCallbackRetryRequiredException();
            }
            return quarantine("unknown-private", providerKey, event, "provider-room-unmapped");
        }
        ProviderMapping room = roomMapping.get();
        if (event.providerRedacted()) {
            return recordRedactedProviderProjection(providerKey, event, room);
        }
        Optional<ProviderMapping> actorMapping = mappingByProviderRef(providerKey, "actor", event.providerSenderRef());
        if (actorMapping.isEmpty()
                || !"acknowledged".equals(actorMapping.get().state())
                || !actorMapping.get().tenantId().equals(room.tenantId())) {
            return quarantineMappedConversation(
                    room.tenantId(), room.canonicalObjectId(), providerKey, event, "provider-sender-unmapped");
        }
        String canonicalActorKey = actorMapping.get().canonicalObjectId();
        ActorIdentity actor = parseActorKey(canonicalActorKey);
        ConversationId conversationId = new ConversationId(room.canonicalObjectId());
        MatrixSynapseCompatibilityProfile.StateClassification stateClassification =
                compatibilityProfile.classify(event.eventType(), event.stateKey() != null);
        if (stateClassification == MatrixSynapseCompatibilityProfile.StateClassification.UNKNOWN_RECOVERABLE) {
            return quarantineMappedConversation(
                    room.tenantId(), room.canonicalObjectId(), providerKey, event,
                    "provider-state-event-type-unsupported");
        }
        if (stateClassification == MatrixSynapseCompatibilityProfile.StateClassification.SUPPORTED_IGNORED) {
            // Membership transitions can be delivered after the canonical
            // leave operation has already committed. They are provider state,
            // not a new canonical member-authored event, so current joined
            // membership is not a valid correlation prerequisite.
            recordLedger(room.tenantId(), providerKey, "inbound", event.providerTransactionId(),
                    event.providerEventRef(), room.canonicalObjectId(), event.providerSourceVersion(),
                    "ignored-supported-state");
            return new CallbackEventResult("ignored", sha256(room.tenantId() + ":" + event.providerEventRef()));
        }
        if (stateClassification == MatrixSynapseCompatibilityProfile.StateClassification.KNOWN_STATE_KEY_MISSING) {
            return quarantineMappedConversation(
                    room.tenantId(), room.canonicalObjectId(), providerKey, event,
                    "provider-state-key-missing");
        }
        String roomContextId = contextId(room.tenantId(), conversationId)
                .orElseThrow(() -> new IllegalStateException("Canonical Chat context binding is missing."));
        ChatRequestContext context = new ChatRequestContext(
                room.tenantId(), roomContextId, actor.issuer(), new ChatActorRef(actor.actorRef()));
        try {
            requireJoinedMembership(context, conversationId);
        } catch (ChatAccessDeniedException exception) {
            return quarantineMappedConversation(
                    room.tenantId(), room.canonicalObjectId(), providerKey, event, "provider-sender-not-authorized");
        }
        String encryptionMode = encryptionMode(room.tenantId(), conversationId);
        if ("m.room.message".equals(event.eventType()) && !"unencrypted".equals(encryptionMode)) {
            return quarantineMappedConversation(
                    room.tenantId(), room.canonicalObjectId(), providerKey, event, "plaintext-in-encrypted-room");
        }
        ChatEventContent content;
        if ("m.room.encrypted".equals(event.eventType())) {
            if (!ChatEncryptedEnvelope.MEGOLM_V1.equals(encryptionMode)) {
                return quarantineMappedConversation(
                        room.tenantId(), room.canonicalObjectId(), providerKey, event,
                        "encrypted-event-policy-mismatch");
            }
            try {
                content = ChatEventContent.encrypted(event.content());
            } catch (IllegalArgumentException exception) {
                return quarantineMappedConversation(
                        room.tenantId(), room.canonicalObjectId(), providerKey, event,
                        encryptedEnvelopeReason(exception));
            }
        } else if ("m.room.message".equals(event.eventType()) && "unencrypted".equals(encryptionMode)) {
            Object body = event.content().get("body");
            if (!(body instanceof String text) || text.isBlank()) {
                return quarantineMappedConversation(
                        room.tenantId(), room.canonicalObjectId(), providerKey, event, "message-content-invalid");
            }
            content = ChatEventContent.text(text);
        } else {
            return quarantineMappedConversation(
                    room.tenantId(), room.canonicalObjectId(), providerKey, event, "provider-event-type-unsupported");
        }
        Optional<ProviderMapping> existingEvent = mappingByProviderRef(
                providerKey, "event", event.providerEventRef());
        if (existingEvent.isPresent() && !existingEvent.get().tenantId().equals(room.tenantId())) {
            return quarantineMappedConversation(
                    room.tenantId(), room.canonicalObjectId(), providerKey, event, "provider-event-tenant-mismatch");
        }
        String canonicalEventId = existingEvent
                .map(ProviderMapping::canonicalObjectId)
                .orElseGet(() -> "event-provider-"
                        + sha256(room.tenantId() + ":" + event.providerEventRef()).substring(0, 48));
        Optional<String> existingConversation = callbackEventConversation(room.tenantId(), canonicalEventId);
        if (existingConversation.isPresent() && !existingConversation.get().equals(conversationId.value())) {
            return quarantineMappedConversation(
                    room.tenantId(), room.canonicalObjectId(), providerKey, event,
                    "provider-event-conversation-mismatch");
        }
        ChatTripleId eventId = new ChatTripleId(
                room.tenantId(), conversationId.value(), canonicalEventId);
        boolean inserted = !jpa.events().existsById(eventId);
        if (inserted) {
            jpa.events().save(ChatEventJpaEntity.create(
                    room.tenantId(),
                    conversationId.value(),
                    canonicalEventId,
                    allocateEventSequence(room.tenantId(), conversationId),
                    actor.issuer(),
                    actor.actorRef(),
                    content.kind().value(),
                    json(content),
                    clock.instant(),
                    COMMITTED));
        }
        acknowledgeMapping(room.tenantId(), providerKey, "event", canonicalEventId,
                event.providerEventRef(), event.providerSourceVersion());
        recordLedger(room.tenantId(), providerKey, "inbound", event.providerTransactionId(), event.providerEventRef(),
                canonicalEventId, event.providerSourceVersion(), "acknowledged");
        recordCallbackChangeIfAbsent(
                room.tenantId(),
                conversationId,
                "message.created",
                canonicalEventId,
                providerKey,
                event.providerEventRef(),
                clock.instant());
        return new CallbackEventResult(
                inserted ? "accepted" : "deduplicated",
                sha256(room.tenantId() + ":" + canonicalEventId));
    }

    private CallbackEventResult recordRedactedProviderProjection(
            String providerKey,
            ProviderCallbackEvent event,
            ProviderMapping room) {
        if (!"m.room.encrypted".equals(event.eventType())
                || event.stateKey() != null
                || !event.content().isEmpty()) {
            return quarantineMappedConversation(
                    room.tenantId(), room.canonicalObjectId(), providerKey, event,
                    "provider-redacted-projection-invalid");
        }
        Optional<ProviderMapping> existingEvent = mappingByProviderRef(
                providerKey, "event", event.providerEventRef());
        if (existingEvent.isEmpty()) {
            throw new ChatCallbackRetryRequiredException();
        }
        ProviderMapping mapping = existingEvent.get();
        Optional<String> existingConversation = callbackEventConversation(
                room.tenantId(), mapping.canonicalObjectId());
        if (!"acknowledged".equals(mapping.state())
                || !room.tenantId().equals(mapping.tenantId())
                || existingConversation.isEmpty()
                || !room.canonicalObjectId().equals(existingConversation.get())) {
            return quarantineMappedConversation(
                    room.tenantId(), room.canonicalObjectId(), providerKey, event,
                    "provider-redacted-projection-mismatch");
        }
        boolean updated = requireEventEntity(
                room.tenantId(),
                new ConversationId(room.canonicalObjectId()),
                mapping.canonicalObjectId()).redact();
        recordLedger(room.tenantId(), providerKey, "inbound", event.providerTransactionId(),
                event.providerEventRef(), mapping.canonicalObjectId(), event.providerSourceVersion(),
                "acknowledged-redacted-projection");
        if (updated) {
            recordCallbackChangeIfAbsent(
                    room.tenantId(), new ConversationId(room.canonicalObjectId()), "event.redacted",
                    mapping.canonicalObjectId(), providerKey, event.providerEventRef(), clock.instant());
        }
        return new CallbackEventResult(
                updated ? "acknowledged-redacted-projection" : "deduplicated-redacted-projection",
                sha256(room.tenantId() + ":" + mapping.canonicalObjectId()));
    }

    private CallbackEventResult recordRedactionEcho(
            String providerKey,
            ProviderCallbackEvent event,
            ProviderOperationEcho echo) {
        Optional<ProviderMapping> expectedRoom = mapping(
                echo.tenantId(), providerKey, "conversation", echo.conversationId());
        if (expectedRoom.isEmpty()
                || !correlatableConversationMapping(expectedRoom.get())
                || !event.providerRoomRef().equals(expectedRoom.get().providerRef())) {
            return quarantineMappedConversation(
                    echo.tenantId(), echo.conversationId(), providerKey, event,
                    "provider-redaction-room-mismatch");
        }
        Optional<ProviderMapping> sender = mappingByProviderRef(
                providerKey, "actor", event.providerSenderRef());
        if (sender.isEmpty()
                || !"acknowledged".equals(sender.get().state())
                || !echo.tenantId().equals(sender.get().tenantId())) {
            return quarantineMappedConversation(
                    echo.tenantId(), echo.conversationId(), providerKey, event,
                    "provider-redaction-sender-mismatch");
        }
        ActorIdentity senderIdentity = parseActorKey(sender.get().canonicalObjectId());
        if (!echo.identityIssuer().equals(senderIdentity.issuer())
                || !echo.actorRef().equals(senderIdentity.actorRef())) {
            return quarantineMappedConversation(
                    echo.tenantId(), echo.conversationId(), providerKey, event,
                    "provider-redaction-sender-mismatch");
        }
        String echoContextId = contextId(echo.tenantId(), new ConversationId(echo.conversationId()))
                .orElseThrow(() -> new IllegalStateException("Canonical Chat context binding is missing."));
        ChatRequestContext echoContext = new ChatRequestContext(
                echo.tenantId(), echoContextId, echo.identityIssuer(), new ChatActorRef(echo.actorRef()));
        try {
            requireJoinedMembership(echoContext, new ConversationId(echo.conversationId()));
            EventOwner owner = eventOwner(
                    echo.tenantId(), new ConversationId(echo.conversationId()), echo.canonicalObjectId());
            if (!echo.identityIssuer().equals(owner.identityIssuer())
                    || !echo.actorRef().equals(owner.actorRef())) {
                throw new ChatAccessDeniedException();
            }
        } catch (ChatAccessDeniedException | IllegalArgumentException exception) {
            return quarantineMappedConversation(
                    echo.tenantId(), echo.conversationId(), providerKey, event,
                    "provider-redaction-sender-not-authorized");
        }
        Optional<ProviderMapping> target = mapping(
                echo.tenantId(), providerKey, "event", echo.canonicalObjectId());
        if (!"m.room.redaction".equals(event.eventType())
                || event.stateKey() != null
                || target.isEmpty()
                || !"acknowledged".equals(target.get().state())
                || !java.util.Objects.equals(target.get().providerRef(), event.providerRedactsRef())
                || !supportedRedactionPresentationContent(event.content())) {
            return quarantineMappedConversation(
                    echo.tenantId(), echo.conversationId(), providerKey, event,
                    "provider-redaction-echo-mismatch");
        }
        String canonicalRedactionEventId = redactionEventId(echo.operationId());
        Optional<ProviderMapping> existingRedaction = mappingByProviderRef(
                providerKey, "redaction", event.providerEventRef());
        if (existingRedaction.isPresent()
                && (!echo.tenantId().equals(existingRedaction.get().tenantId())
                || !canonicalRedactionEventId.equals(existingRedaction.get().canonicalObjectId()))) {
            return quarantineMappedConversation(
                    echo.tenantId(), echo.conversationId(), providerKey, event,
                    "provider-redaction-event-mismatch");
        }
        requireEventEntity(
                echo.tenantId(),
                new ConversationId(echo.conversationId()),
                echo.canonicalObjectId()).redact();
        acknowledgeMapping(
                echo.tenantId(),
                providerKey,
                "redaction",
                canonicalRedactionEventId,
                event.providerEventRef(),
                event.providerSourceVersion());
        acknowledgeOperation(echo.tenantId(), echo.operationId());
        recordLedger(echo.tenantId(), providerKey, "inbound-echo", echo.providerTransactionId(),
                event.providerEventRef(), canonicalRedactionEventId, event.providerSourceVersion(), "acknowledged");
        recordCallbackChangeIfAbsent(
                echo.tenantId(), new ConversationId(echo.conversationId()), "event.redacted",
                echo.canonicalObjectId(), providerKey, event.providerEventRef(), clock.instant());
        return new CallbackEventResult(
                "acknowledged-redaction-echo",
                sha256(echo.tenantId() + ":" + echo.canonicalObjectId()));
    }

    static boolean supportedRedactionPresentationContent(Map<String, Object> content) {
        if (content.isEmpty()) {
            return true;
        }
        if (content.size() != 1 || !(content.get("reason") instanceof String reason)) {
            return false;
        }
        // Matrix permits a human-readable redaction reason. Weave does not
        // ingest or republish that presentation field, but its presence must
        // not turn a correctly correlated redaction echo into conversation
        // degradation. Keep acceptance bounded and reject control characters.
        return reason.length() <= 512 && reason.chars().noneMatch(Character::isISOControl);
    }

    @Override
    public CallbackEventResult recordMalformedCallbackEvent(
            String providerKey,
            String transactionId,
            String eventDigest,
            String reasonCode) {
        String correlationHash = sha256(providerKey + "\u0000" + transactionId + "\u0000" + eventDigest);
        String quarantineId = "quarantine-" + correlationHash;
        Instant observedAt = clock.instant();
        ChatPairId id = new ChatPairId("unknown-private", quarantineId);
        if (!jpa.quarantines().existsById(id)) {
            jpa.quarantines().save(ChatQuarantineJpaEntity.create(
                    "unknown-private",
                    quarantineId,
                    providerKey,
                    null,
                    correlationHash,
                    safeCode(reasonCode),
                    "provider-malformed",
                    false,
                    compatibilityProfile.classifierVersion(),
                    "rejected",
                    DEFAULT_RECONCILIATION_ATTEMPTS,
                    observedAt,
                    transactionId,
                    null,
                    null,
                    null,
                    "callback-event-malformed"));
        }
        return new CallbackEventResult("quarantined-poison", correlationHash);
    }

    @Override
    public void completeCallback(String providerKey, String transactionId, int duplicateCount) {
        transactions.executeWithoutResult(status -> jpa.callbacks()
                .findById(new ChatPairId(providerKey, transactionId))
                .ifPresent(callback -> callback.complete(duplicateCount, clock.instant())));
    }

    @Override
    public long systemicCallbackIntegrityFailureCount(String providerKey) {
        return jpa.callbacks().countByIdPart1AndState(
                requiredText(providerKey, "Chat provider key", 64),
                "semantic-mismatch");
    }

    @Override
    public List<QuarantineReconciliationResult> reconcilePendingQuarantines(
            String providerKey,
            int limit) {
        String safeProvider = requiredText(providerKey, "Chat provider key", 64);
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        List<QuarantineCandidate> candidates = jpa.quarantines()
                .findReconciliationCandidates(
                        safeProvider,
                        compatibilityProfile.classifierVersion(),
                        PageRequest.of(0, boundedLimit))
                .stream()
                .map(quarantine -> new QuarantineCandidate(
                        quarantine.tenantId(), quarantine.correlationHash()))
                .toList();
        List<QuarantineReconciliationResult> results = new ArrayList<>(candidates.size());
        for (QuarantineCandidate candidate : candidates) {
            results.add(reconcileQuarantine(
                    candidate.tenantId(), safeProvider, candidate.correlationHash()));
        }
        return List.copyOf(results);
    }

    @Override
    public QuarantineReconciliationResult reconcileQuarantine(
            String tenantId,
            String providerKey,
            String correlationHash) {
        String safeTenant = requiredText(tenantId, "Chat tenant", 160);
        String safeProvider = requiredText(providerKey, "Chat provider key", 64);
        if (correlationHash == null || !correlationHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Chat quarantine correlation is invalid.");
        }
        QuarantineReconciliationResult result = transactions.execute(status -> {
            ChatQuarantineJpaEntity persisted = jpa.quarantines().lockByCorrelation(
                            safeTenant, safeProvider, correlationHash)
                    .orElseThrow(() -> new IllegalArgumentException("Chat quarantine correlation was not found."));
            QuarantineRow quarantine = new QuarantineRow(
                    persisted.quarantineId(),
                    persisted.conversationId(),
                    persisted.correlationHash(),
                    persisted.recoverable(),
                    persisted.classifierVersion(),
                    persisted.lifecycleState(),
                    persisted.attemptCount(),
                    persisted.maxAttempts(),
                    persisted.normalizedEventJson());
            if (!"pending".equals(quarantine.lifecycleState())) {
                return new QuarantineReconciliationResult(
                        quarantine.lifecycleState(),
                        "quarantine-already-" + quarantine.lifecycleState(),
                        quarantine.attemptCount(),
                        false,
                        quarantine.correlationHash());
            }
            if (!quarantine.recoverable()) {
                return rejectQuarantine(
                        safeTenant, quarantine, "quarantine-not-recoverable", false);
            }
            if (compatibilityProfile.classifierVersion().equals(quarantine.classifierVersion())) {
                return new QuarantineReconciliationResult(
                        "pending",
                        "classifier-not-advanced",
                        quarantine.attemptCount(),
                        false,
                        quarantine.correlationHash());
            }
            if (quarantine.attemptCount() >= quarantine.maxAttempts()) {
                return rejectQuarantine(
                        safeTenant, quarantine, "reconciliation-attempt-limit", false);
            }
            ProviderCallbackEvent event;
            try {
                event = readPrivateCallbackEvent(quarantine.normalizedEventJson());
            } catch (IllegalArgumentException exception) {
                return rejectQuarantine(
                        safeTenant, quarantine, "private-reconciliation-input-invalid", true);
            }
            MatrixSynapseCompatibilityProfile.StateClassification classification =
                    compatibilityProfile.classify(event.eventType(), event.stateKey() != null);
            if (classification != MatrixSynapseCompatibilityProfile.StateClassification.SUPPORTED_IGNORED) {
                return deferOrRejectQuarantine(
                        safeTenant, quarantine, "classifier-still-unsupported");
            }
            if (providerEventAlreadyCommitted(safeTenant, safeProvider, event.providerEventRef())) {
                return supersedeQuarantine(safeTenant, safeProvider, quarantine);
            }
            CallbackEventResult callback = recordCallbackEventInTransaction(safeProvider, event);
            if (!("ignored".equals(callback.state())
                    || "accepted".equals(callback.state())
                    || "deduplicated".equals(callback.state())
                    || callback.state().startsWith("acknowledged-"))) {
                return rejectQuarantine(
                        safeTenant, quarantine, "reconciliation-policy-rejected", true);
            }
            int attempts = quarantine.attemptCount() + 1;
            Instant now = clock.instant();
            persisted.resolve(
                    "reconciled",
                    "reconciliation-committed",
                    compatibilityProfile.classifierVersion(),
                    now);
            boolean healed = healConversationIfResolved(
                    safeTenant, safeProvider, quarantine.conversationId());
            return new QuarantineReconciliationResult(
                    "reconciled",
                    "reconciliation-committed",
                    attempts,
                    healed,
                    quarantine.correlationHash());
        });
        if (result == null) {
            throw new IllegalStateException("Chat quarantine reconciliation returned no result.");
        }
        return result;
    }

    @Override
    public EvidenceSnapshot evidence(String tenantId, ConversationId conversationId, String providerKey) {
        String conversation = conversationId.value();
        String activeProviderKey = requiredText(providerKey, "Chat provider key", 64);
        return new EvidenceSnapshot(
                persistencePosture(),
                jpa.conversations().findByIdAndLifecycleState(
                        new ChatPairId(tenantId, conversation), COMMITTED).isPresent() ? 1 : 0,
                jpa.memberships().countByIdPart1AndIdPart2AndState(
                        tenantId, conversation, "joined"),
                jpa.events().countByIdPart1AndIdPart2AndDeliveryState(
                        tenantId, conversation, COMMITTED),
                jpa.events().countByIdPart1AndIdPart2AndDeliveryStateAndKind(
                        tenantId, conversation, COMMITTED, "encrypted"),
                jpa.events().countByIdPart1AndIdPart2AndDeliveryStateAndKind(
                        tenantId, conversation, COMMITTED, "message"),
                jpa.operations().countByIdPart1AndConversationIdAndState(
                        tenantId, conversation, "pending"),
                jpa.operations().countByIdPart1AndConversationIdAndState(
                        tenantId, conversation, "failed_retryable"),
                jpa.operations().countByIdPart1AndConversationIdAndState(
                        tenantId, conversation, COMMITTED),
                jpa.ledger().countEvidence(tenantId, activeProviderKey, conversation),
                jpa.callbacks().countByIdPart1(activeProviderKey),
                jpa.callbacks().sumDuplicates(activeProviderKey),
                jpa.callbacks().sumSemanticMismatches(activeProviderKey),
                jpa.quarantines().countByProviderKeyAndIdPart1AndConversationIdAndLifecycleStateIn(
                        activeProviderKey,
                        tenantId,
                        conversation,
                        List.of("pending", "rejected")),
                jpa.mappings().countByIdPart1AndIdPart2AndIdPart3AndIdPart4AndState(
                        tenantId,
                        activeProviderKey,
                        "conversation",
                        conversation,
                        "degraded"),
                clock.instant());
    }

    private PreparedMembership preparedMembership(
            ChatRequestContext context,
            ConversationId conversationId,
            String targetState,
            boolean committed) {
        String operationId = operationId(context, "membership-" + targetState, conversationId.value(), targetState);
        return new PreparedMembership(operationId, conversationId, providerTransaction(operationId), targetState, committed);
    }

    private void insertOperation(
            ChatRequestContext context,
            String operationId,
            String operationType,
            String conversationId,
            String canonicalObjectId,
            String northboundTransactionId,
            String providerTransactionId,
            String providerAliasIntent,
            String payloadDigest,
            String outboxPayload,
            Instant now) {
        jpa.operations().saveAndFlush(ChatOperationJpaEntity.create(
                context.tenantId(),
                context.contextId(),
                operationId,
                operationType,
                context.identityIssuer(),
                context.actorRef().value(),
                conversationId,
                canonicalObjectId,
                northboundTransactionId,
                providerTransactionId,
                providerAliasIntent,
                payloadDigest,
                now));
        jpa.outbox().save(ChatOutboxJpaEntity.create(
                context.tenantId(),
                operationId,
                operationType,
                outboxPayload,
                providerTransactionId,
                now));
    }

    private void acknowledgeOperation(String tenantId, String operationId) {
        Instant now = clock.instant();
        ChatPairId id = new ChatPairId(tenantId, operationId);
        jpa.operations().findById(id).ifPresent(operation -> operation.acknowledge(now));
        jpa.outbox().findById(id).ifPresent(outbox -> outbox.acknowledge(now));
    }

    private Optional<OperationRow> operation(String tenantId, String operationId) {
        return jpa.operations().findById(new ChatPairId(tenantId, operationId))
                .map(operation -> new OperationRow(
                        operation.canonicalObjectId(),
                        operation.providerTransactionId(),
                        operation.providerAliasIntent(),
                        operation.payloadDigest(),
                        operation.state()));
    }

    private Optional<ProviderOperationEcho> providerOperation(String providerTransactionId) {
        if (providerTransactionId == null || providerTransactionId.isBlank()) {
            return Optional.empty();
        }
        return jpa.operations().findFirstByProviderTransactionId(providerTransactionId)
                .map(this::providerOperationEcho);
    }

    private List<ProviderOperationEcho> pendingProviderEchoes(
            String providerKey,
            ProviderCallbackEvent event) {
        if (event.stateKey() != null) {
            return List.of();
        }
        Optional<ProviderMapping> room = mappingByProviderRef(
                providerKey, "conversation", event.providerRoomRef());
        Optional<ProviderMapping> sender = mappingByProviderRef(
                providerKey, "actor", event.providerSenderRef());
        if (room.isEmpty() || sender.isEmpty()
                || !correlatableConversationMapping(room.get())
                || !"acknowledged".equals(sender.get().state())
                || !room.get().tenantId().equals(sender.get().tenantId())) {
            return List.of();
        }
        ActorIdentity senderIdentity = parseActorKey(sender.get().canonicalObjectId());
        List<ProviderOperationEcho> candidates = jpa.operations()
                .findByIdPart1AndConversationIdAndOperationTypeInAndStateIn(
                        room.get().tenantId(),
                        room.get().canonicalObjectId(),
                        List.of("send-event", "redact-event"),
                        List.of("pending", "failed_retryable", COMMITTED))
                .stream()
                .filter(operation -> !"committed".equals(operation.state())
                        || "redact-event".equals(operation.operationType())
                        && jpa.ledger()
                                .existsByIdPart1AndProviderKeyAndDirectionAndProviderTransactionIdAndProviderEventRef(
                                        operation.tenantId(),
                                        providerKey,
                                        "outbound",
                                        operation.providerTransactionId(),
                                        event.providerEventRef()))
                .map(this::providerOperationEcho)
                .toList();
        return candidates.stream()
                .filter(candidate -> senderIdentity.issuer().equals(candidate.identityIssuer())
                        && senderIdentity.actorRef().equals(candidate.actorRef()))
                .filter(candidate -> fallbackEchoMatches(providerKey, event, candidate))
                .toList();
    }

    private boolean fallbackEchoMatches(
            String providerKey,
            ProviderCallbackEvent event,
            ProviderOperationEcho candidate) {
        try {
            if ("send-event".equals(candidate.operationType())) {
                return candidate.content() != null
                        && providerEventType(candidate.content()).equals(event.eventType())
                        && contentPolicyMatches(
                                candidate.tenantId(), new ConversationId(candidate.conversationId()),
                                candidate.content())
                        && constantTimeDigestEquals(
                                providerContent(candidate.tenantId(), providerKey, candidate.content()),
                                event.content());
            }
            if ("redact-event".equals(candidate.operationType())) {
                Optional<ProviderMapping> target = mapping(
                        candidate.tenantId(), providerKey, "event", candidate.canonicalObjectId());
                return "m.room.redaction".equals(event.eventType())
                        && supportedRedactionPresentationContent(event.content())
                        && target.filter(mapping -> "acknowledged".equals(mapping.state()))
                                .map(ProviderMapping::providerRef)
                                .filter(ref -> java.util.Objects.equals(ref, event.providerRedactsRef()))
                                .isPresent();
            }
            return false;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return false;
        }
    }

    private boolean pendingCreateCouldOwnCallback(
            String providerKey,
            ProviderCallbackEvent event,
            Optional<ProviderOperationEcho> correlatedOperation) {
        if (correlatedOperation.filter(operation -> "create-room".equals(operation.operationType()))
                .isPresent()) {
            return true;
        }
        Optional<ProviderMapping> sender = mappingByProviderRef(
                providerKey, "actor", event.providerSenderRef());
        if (sender.isEmpty() || !"acknowledged".equals(sender.get().state())) {
            return false;
        }
        ActorIdentity actor = parseActorKey(sender.get().canonicalObjectId());
        return jpa.operations()
                .countByIdPart1AndOperationTypeAndIdentityIssuerAndActorRefAndStateIn(
                        sender.get().tenantId(),
                        "create-room",
                        actor.issuer(),
                        actor.actorRef(),
                        List.of("pending", "failed_retryable")) == 1;
    }

    private String providerEventType(ChatEventContent content) {
        if (content == null) {
            throw new IllegalStateException("Canonical Chat provider operation has no event content.");
        }
        return switch (content.kind()) {
            case MESSAGE -> "m.room.message";
            case REACTION -> "m.reaction";
            case ENCRYPTED -> "m.room.encrypted";
        };
    }

    private String redactionEventId(String operationId) {
        return "redaction-" + UUID.nameUUIDFromBytes(operationId.getBytes(StandardCharsets.UTF_8));
    }

    private boolean contentPolicyMatches(
            String tenantId,
            ConversationId conversationId,
            ChatEventContent content) {
        String mode = encryptionMode(tenantId, conversationId);
        return ChatEncryptedEnvelope.MEGOLM_V1.equals(mode)
                ? content.kind() == ChatEventKind.ENCRYPTED
                : "unencrypted".equals(mode) && content.kind() != ChatEventKind.ENCRYPTED;
    }

    private Map<String, Object> providerContent(
            String tenantId,
            String providerKey,
            ChatEventContent content) {
        if (content.kind() == ChatEventKind.ENCRYPTED) {
            return content.encryptedEnvelope().content();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        if (content.kind() == ChatEventKind.MESSAGE) {
            result.putAll(plainProviderMessageContent(content));
        }
        if (content.relation() != null) {
            ProviderMapping target = mapping(tenantId, providerKey, "event", content.relation().targetEventId())
                    .filter(mapping -> "acknowledged".equals(mapping.state()) && mapping.providerRef() != null)
                    .orElseThrow(() -> new IllegalStateException("Canonical Chat provider relation target is unmapped."));
            Map<String, Object> relation = new LinkedHashMap<>();
            if ("reply".equals(content.relation().kind())) {
                relation.put("m.in_reply_to", Map.of("event_id", target.providerRef()));
            } else {
                relation.put("rel_type", switch (content.relation().kind()) {
                    case "reaction" -> "m.annotation";
                    case "replace" -> "m.replace";
                    case "thread" -> "m.thread";
                    default -> throw new IllegalArgumentException("provider relation kind is unsupported");
                });
                relation.put("event_id", target.providerRef());
            }
            if (content.reactionKey() != null) {
                relation.put("key", content.reactionKey());
            }
            result.put("m.relates_to", Map.copyOf(relation));
            if ("replace".equals(content.relation().kind())) {
                result.put("m.new_content", plainProviderMessageContent(content));
            }
        }
        result.putAll(content.presentationExtensions());
        return Map.copyOf(result);
    }

    private Map<String, Object> plainProviderMessageContent(ChatEventContent content) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("msgtype", content.messageType() == null ? "m.text" : content.messageType());
        value.put("body", content.body());
        if (content.format() != null) {
            value.put("format", content.format());
        }
        if (content.formattedBody() != null) {
            value.put("formatted_body", content.formattedBody());
        }
        return Map.copyOf(value);
    }

    private boolean constantTimeDigestEquals(Object expected, Object actual) {
        byte[] expectedDigest = sha256Bytes(canonicalJsonValue(expected));
        byte[] actualDigest = sha256Bytes(canonicalJsonValue(actual));
        return MessageDigest.isEqual(expectedDigest, actualDigest);
    }

    private boolean constantTimeTextEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private String canonicalJsonValue(Object value) {
        return json(canonicalize(value));
    }

    private Object canonicalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((key, nested) -> {
                if (!(key instanceof String text)) {
                    throw new IllegalArgumentException("Canonical Chat provider content key is invalid.");
                }
                sorted.put(text, canonicalize(nested));
            });
            return sorted;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> ordered = new ArrayList<>();
            iterable.forEach(nested -> ordered.add(canonicalize(nested)));
            return ordered;
        }
        return value;
    }

    private byte[] sha256Bytes(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void requireSameDigest(OperationRow row, String digest) {
        if (!MessageDigest.isEqual(row.payloadDigest().getBytes(StandardCharsets.UTF_8),
                digest.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("A Chat transaction was replayed with different content.");
        }
    }

    private void insertMembership(
            String tenantId,
            String conversationId,
            String issuer,
            ChatActorRef actor,
            String state,
            Instant updated,
            Instant joinedAt) {
        jpa.memberships().save(ChatMembershipJpaEntity.create(
                tenantId,
                conversationId,
                issuer,
                actor.value(),
                state,
                "invited".equals(state) ? updated : null,
                joinedAt));
    }

    private Optional<MembershipRow> membership(ChatRequestContext context, ConversationId conversationId) {
        return jpa.conversations()
                .findByIdAndLifecycleState(
                        new ChatPairId(context.tenantId(), conversationId.value()),
                        COMMITTED)
                .filter(conversation -> context.contextId().equals(conversation.contextId()))
                .flatMap(conversation -> jpa.memberships().findById(
                        membershipId(context, conversationId)))
                .map(persisted -> new MembershipRow(persisted.state()));
    }

    private void requireJoined(ChatRequestContext context, ConversationId conversationId) {
        requireJoinedMembership(context, conversationId);
        requireConversationHealthy(context.tenantId(), conversationId);
    }

    private void requireJoinedMembership(ChatRequestContext context, ConversationId conversationId) {
        boolean joined = membership(context, conversationId)
                .map(value -> "joined".equals(value.state()))
                .orElse(false);
        if (!joined) {
            throw new ChatAccessDeniedException();
        }
        if (findConversation(context.tenantId(), conversationId, context.actorRef()).isEmpty()) {
            throw new IllegalArgumentException("canonical chat conversation was not found");
        }
    }

    private void requireConversationHealthy(String tenantId, ConversationId conversationId) {
        if (jpa.mappings().countByIdPart1AndIdPart3AndIdPart4AndState(
                tenantId, "conversation", conversationId.value(), "degraded") > 0) {
            String reason = jpa.quarantines()
                    .findFirstByIdPart1AndConversationIdAndLifecycleStateInOrderByObservedAtDescIdPart2Desc(
                            tenantId,
                            conversationId.value(),
                            List.of("pending", "rejected"))
                    .map(ChatQuarantineJpaEntity::reasonCode)
                    .map(this::safeCode)
                    .orElse("unknown");
            throw new ChatProviderUnavailableException("chat-conversation-mapping-degraded-" + reason);
        }
    }

    private boolean openToWorkspace(String tenantId, String contextId, ConversationId conversationId) {
        return jpa.conversations()
                .findByIdAndLifecycleState(
                        new ChatPairId(tenantId, conversationId.value()), COMMITTED)
                .filter(conversation -> contextId.equals(conversation.contextId()))
                .map(ChatConversationJpaEntity::openToWorkspace)
                .orElse(false);
    }

    private Optional<ChatConversation> findConversation(
            String tenantId,
            ConversationId conversationId,
            ChatActorRef actorRef) {
        return jpa.conversations()
                .findByIdAndLifecycleState(
                        new ChatPairId(tenantId, conversationId.value()), COMMITTED)
                .map(conversation -> mapConversation(
                        conversation,
                        jpa.memberships().findByIdPart1AndIdPart2OrderByIdPart4(
                                tenantId, conversationId.value())));
    }

    private Optional<ChatConversation> mapConversationWithoutAuthorization(
            String tenantId,
            ConversationId conversationId,
            ChatActorRef actorRef) {
        return findConversation(tenantId, conversationId, actorRef);
    }

    private ChatConversation mapConversation(
            ChatConversationJpaEntity conversation,
            List<ChatMembershipJpaEntity> persistedMemberships) {
        String tenantId = conversation.tenantId();
        String conversationId = conversation.conversationId();
        String mode = conversation.encryptionMode();
        List<ChatMembership> memberships = persistedMemberships.stream()
                .map(membership -> new ChatMembership(
                        "membership-" + sha256(
                                tenantId + ":" + conversationId + ":" + membership.actorRef())
                                .substring(0, 32),
                        conversationId,
                        membership.actorRef(),
                        membership.role(),
                        membership.state(),
                        membership.joinedAt(),
                        "joined".equals(membership.state())
                                ? List.of("chat.read", "chat.send") : List.of()))
                .toList();
        return new ChatConversation(
                conversationId,
                conversation.title(),
                conversation.kind(),
                ChatMemberState.READY,
                "Chat is available through the Weave workspace.",
                conversation.updatedAt(),
                "unencrypted".equals(mode) ? ChatEncryptionState.unencrypted() : ChatEncryptionState.matrixMegolm(),
                HISTORY_POLICY,
                memberships,
                List.of());
    }

    private ChatTimelineEvent requireEvent(String tenantId, ConversationId conversationId, String eventId) {
        return mapEvent(requireEventEntity(tenantId, conversationId, eventId));
    }

    private EventOwner eventOwner(String tenantId, ConversationId conversationId, String eventId) {
        ChatEventJpaEntity event = requireEventEntity(tenantId, conversationId, eventId);
        if (!COMMITTED.equals(event.deliveryState())) {
            throw new IllegalArgumentException("canonical chat event was not found");
        }
        return new EventOwner(event.senderIssuer(), event.senderRef());
    }

    private ChatTimelineEvent mapEvent(ChatEventJpaEntity event) {
        return new ChatTimelineEvent(
                event.eventId(),
                event.conversationId(),
                event.senderRef(),
                event.occurredAt(),
                readContent(event.contentJson()),
                event.deliveryState(),
                event.redacted());
    }

    private ChatEventContent readContent(String contentJson) {
        try {
            return objectMapper.readValue(contentJson, ChatEventContent.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Canonical Chat persistence is invalid.", exception);
        }
    }

    private ChatMessage message(ChatTimelineEvent event) {
        return new ChatMessage(event.eventId(), event.conversationId(), event.senderRef(), event.occurredAt(),
                event.redacted() ? "" : event.content().body(), event.deliveryState(), List.of());
    }

    private void requireContentCompatible(String tenantId, ConversationId conversationId, ChatEventContent content) {
        if (content == null) {
            throw new IllegalArgumentException("chat event content is required");
        }
        String mode = encryptionMode(tenantId, conversationId);
        if (!"unencrypted".equals(mode) && content.kind() != ChatEventKind.ENCRYPTED) {
            throw new IllegalArgumentException("plaintext Chat events are forbidden after room encryption is enabled");
        }
        if ("unencrypted".equals(mode) && content.kind() == ChatEventKind.ENCRYPTED) {
            throw new IllegalArgumentException("encrypted Chat events require room encryption state");
        }
    }

    private String encryptionMode(String tenantId, ConversationId conversationId) {
        return jpa.conversations()
                .findByIdAndLifecycleState(
                        new ChatPairId(tenantId, conversationId.value()), COMMITTED)
                .map(ChatConversationJpaEntity::encryptionMode)
                .orElseThrow(() -> new IllegalArgumentException("canonical chat conversation was not found"));
    }

    private ProviderMapping mapProviderMapping(ChatProviderMappingJpaEntity mapping) {
        return new ProviderMapping(
                mapping.tenantId(),
                mapping.providerKey(),
                mapping.objectType(),
                mapping.canonicalObjectId(),
                mapping.providerRef(),
                mapping.mappingIntentRef(),
                mapping.providerSourceVersion(),
                mapping.state());
    }

    private boolean correlatableConversationMapping(ProviderMapping mapping) {
        return mapping != null
                && "conversation".equals(mapping.objectType())
                && mapping.providerRef() != null
                && !mapping.providerRef().isBlank()
                && ("acknowledged".equals(mapping.state()) || "degraded".equals(mapping.state()));
    }

    private void recordLedger(
            String tenantId,
            String providerKey,
            String direction,
            String providerTransactionId,
            String providerEventRef,
            String canonicalObjectId,
            String sourceVersion,
            String state) {
        String ledgerId = "ledger-" + sha256(tenantId + "\u0000" + providerKey + "\u0000" + direction
                + "\u0000" + providerTransactionId + "\u0000" + providerEventRef + "\u0000" + canonicalObjectId);
        ChatPairId id = new ChatPairId(tenantId, ledgerId);
        Instant now = clock.instant();
        ChatBridgeLedgerJpaEntity ledger = jpa.ledger().findById(id)
                .orElseGet(() -> ChatBridgeLedgerJpaEntity.create(
                        tenantId,
                        ledgerId,
                        providerKey,
                        direction,
                        providerTransactionId,
                        providerEventRef,
                        canonicalObjectId,
                        sourceVersion,
                        state,
                        now));
        ledger.update(state, sourceVersion, now);
        jpa.ledger().save(ledger);
    }

    private CallbackEventResult quarantine(
            String tenantId,
            String providerKey,
            ProviderCallbackEvent event,
            String reason) {
        return quarantine(tenantId, null, providerKey, event, reason);
    }

    private CallbackEventResult quarantine(
            String tenantId,
            String conversationId,
            String providerKey,
            ProviderCallbackEvent event,
            String reason) {
        String correlationHash = sha256(providerKey + "\u0000" + event.providerEventRef());
        String quarantineId = "quarantine-" + correlationHash;
        QuarantineDisposition disposition = quarantineDisposition(reason);
        Instant observedAt = clock.instant();
        ChatPairId id = new ChatPairId(tenantId, quarantineId);
        if (!jpa.quarantines().existsById(id)) {
            jpa.quarantines().save(ChatQuarantineJpaEntity.create(
                    tenantId,
                    quarantineId,
                    providerKey,
                    conversationId,
                    correlationHash,
                    safeCode(reason),
                    disposition.categoryCode(),
                    disposition.recoverable(),
                    compatibilityProfile.classifierVersion(),
                    disposition.lifecycleState(),
                    DEFAULT_RECONCILIATION_ATTEMPTS,
                    observedAt,
                    event.homeserverTransactionId(),
                    event.providerEventRef(),
                    event.providerRoomRef(),
                    json(event),
                    "quarantine-" + disposition.lifecycleState()));
        }
        return new CallbackEventResult("quarantined", correlationHash);
    }

    private CallbackEventResult quarantineMappedConversation(
            String tenantId,
            String conversationId,
            String providerKey,
            ProviderCallbackEvent event,
            String reason) {
        jpa.mappings()
                .findById(mappingId(tenantId, providerKey, "conversation", conversationId))
                .ifPresent(mapping -> mapping.degrade(clock.instant()));
        return quarantine(tenantId, conversationId, providerKey, event, reason);
    }

    private QuarantineDisposition quarantineDisposition(String reason) {
        if (reason.startsWith("encrypted-envelope-")) {
            return new QuarantineDisposition("encryption-policy", false, "rejected");
        }
        return switch (reason) {
            case "provider-state-event-type-unsupported" ->
                    new QuarantineDisposition("provider-compatibility", true, "pending");
            case "plaintext-in-encrypted-room", "encrypted-event-policy-mismatch" ->
                    new QuarantineDisposition("encryption-policy", false, "rejected");
            case "provider-state-key-missing", "message-content-invalid" ->
                    new QuarantineDisposition("provider-malformed", false, "rejected");
            case "provider-event-type-unsupported" ->
                    new QuarantineDisposition("provider-compatibility", false, "rejected");
            default -> new QuarantineDisposition("canonical-correlation", false, "rejected");
        };
    }

    private String encryptedEnvelopeReason(IllegalArgumentException exception) {
        return switch (java.util.Objects.toString(exception.getMessage(), "")) {
            case "encrypted Chat algorithm is unsupported" -> "encrypted-envelope-algorithm-unsupported";
            case "encrypted Chat algorithm is invalid" -> "encrypted-envelope-algorithm-invalid";
            case "encrypted Chat ciphertext is invalid" -> "encrypted-envelope-ciphertext-invalid";
            case "encrypted Chat session_id is invalid" -> "encrypted-envelope-session-id-invalid";
            case "encrypted Chat sender_key is invalid" -> "encrypted-envelope-sender-key-invalid";
            case "encrypted Chat device_id is invalid" -> "encrypted-envelope-device-id-invalid";
            case "encrypted Chat envelope nesting is too deep" -> "encrypted-envelope-nesting-invalid";
            case "encrypted Chat envelope value is too large" -> "encrypted-envelope-value-too-large";
            case "encrypted Chat envelope has too many fields" -> "encrypted-envelope-field-count-invalid";
            case "encrypted Chat envelope key is invalid" -> "encrypted-envelope-key-invalid";
            case "encrypted Chat envelope array is too large" -> "encrypted-envelope-array-too-large";
            case "encrypted Chat envelope contains an unsupported value" -> "encrypted-envelope-value-invalid";
            default -> "encrypted-envelope-invalid";
        };
    }

    private QuarantineReconciliationResult deferOrRejectQuarantine(
            String tenantId,
            QuarantineRow quarantine,
            String outcomeCode) {
        int attempts = quarantine.attemptCount() + 1;
        if (attempts >= quarantine.maxAttempts()) {
            return rejectQuarantine(tenantId, quarantine, outcomeCode, true);
        }
        requireQuarantine(tenantId, quarantine.quarantineId()).defer(
                safeCode(outcomeCode),
                compatibilityProfile.classifierVersion(),
                clock.instant());
        return new QuarantineReconciliationResult(
                "pending", safeCode(outcomeCode), attempts, false, quarantine.correlationHash());
    }

    private QuarantineReconciliationResult rejectQuarantine(
            String tenantId,
            QuarantineRow quarantine,
            String outcomeCode,
            boolean incrementAttempt) {
        int attempts = quarantine.attemptCount() + (incrementAttempt ? 1 : 0);
        Instant now = clock.instant();
        requireQuarantine(tenantId, quarantine.quarantineId()).reject(
                safeCode(outcomeCode),
                compatibilityProfile.classifierVersion(),
                now,
                incrementAttempt);
        return new QuarantineReconciliationResult(
                "rejected", safeCode(outcomeCode), attempts, false, quarantine.correlationHash());
    }

    private QuarantineReconciliationResult supersedeQuarantine(
            String tenantId,
            String providerKey,
            QuarantineRow quarantine) {
        int attempts = quarantine.attemptCount() + 1;
        Instant now = clock.instant();
        requireQuarantine(tenantId, quarantine.quarantineId()).resolve(
                "superseded",
                "reconciliation-superseded",
                compatibilityProfile.classifierVersion(),
                now);
        boolean healed = healConversationIfResolved(
                tenantId, providerKey, quarantine.conversationId());
        return new QuarantineReconciliationResult(
                "superseded",
                "reconciliation-superseded",
                attempts,
                healed,
                quarantine.correlationHash());
    }

    private boolean providerEventAlreadyCommitted(
            String tenantId,
            String providerKey,
            String providerEventRef) {
        return jpa.ledger().existsByIdPart1AndProviderKeyAndProviderEventRefAndStateIn(
                tenantId,
                providerKey,
                providerEventRef,
                List.of("acknowledged", "ignored-supported-state"));
    }

    private ProviderCallbackEvent readPrivateCallbackEvent(String normalizedEventJson) {
        if (normalizedEventJson == null || normalizedEventJson.isBlank()) {
            throw new IllegalArgumentException("Private callback reconciliation input is unavailable.");
        }
        try {
            return objectMapper.readValue(normalizedEventJson, ProviderCallbackEvent.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Private callback reconciliation input is invalid.", exception);
        }
    }

    private boolean healConversationIfResolved(
            String tenantId,
            String providerKey,
            String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return false;
        }
        long unresolved = jpa.quarantines()
                .countByIdPart1AndProviderKeyAndConversationIdAndLifecycleStateIn(
                        tenantId,
                        providerKey,
                        conversationId,
                        List.of("pending", "rejected"));
        if (unresolved > 0) {
            return false;
        }
        Optional<ChatProviderMappingJpaEntity> mapping = jpa.mappings().findById(
                mappingId(tenantId, providerKey, "conversation", conversationId));
        if (mapping.isEmpty() || !"degraded".equals(mapping.orElseThrow().state())) {
            return false;
        }
        mapping.orElseThrow().heal(clock.instant());
        return true;
    }

    private Optional<String> callbackEventConversation(String tenantId, String eventId) {
        return jpa.events().findFirstByIdPart1AndIdPart3(tenantId, eventId)
                .map(ChatEventJpaEntity::conversationId);
    }

    private void recordCallbackChangeIfAbsent(
            String tenantId,
            ConversationId conversationId,
            String kind,
            String canonicalObjectId,
            String providerKey,
            String providerEventRef,
            Instant occurredAt) {
        if (jpa.changes().existsByTenantIdAndConversationIdAndKindAndCanonicalObjectId(
                tenantId, conversationId.value(), kind, canonicalObjectId)) {
            return;
        }
        String deduplicationKey = "callback-" + sha256(providerKey + "\u0000" + providerEventRef);
        if (!jpa.changes().existsByTenantIdAndCallbackDeduplicationKey(
                tenantId, deduplicationKey)) {
            jpa.changes().save(ChatChangeJpaEntity.create(
                    tenantId,
                    conversationId.value(),
                    kind,
                    canonicalObjectId,
                    deduplicationKey,
                    occurredAt));
        }
    }

    private ActorIdentity parseActorKey(String canonicalObjectId) {
        try {
            Map<String, String> value = objectMapper.readValue(canonicalObjectId, new TypeReference<>() { });
            return new ActorIdentity(value.get("issuer"), value.get("actorRef"));
        } catch (JacksonException | IllegalArgumentException exception) {
            throw new IllegalStateException("Canonical Chat actor mapping is invalid.", exception);
        }
    }

    private void recordChange(
            String tenantId,
            ConversationId conversationId,
            String kind,
            String canonicalObjectId,
            Instant occurredAt) {
        jpa.changes().save(ChatChangeJpaEntity.create(
                tenantId,
                conversationId.value(),
                kind,
                canonicalObjectId,
                null,
                occurredAt));
    }

    private String operationId(
            ChatRequestContext context,
            String operation,
            String conversationId,
            String transactionId) {
        return "operation-" + sha256(context.tenantId() + "\u0000" + context.identityIssuer() + "\u0000"
                + context.contextId() + "\u0000" + context.actorRef().value() + "\u0000"
                + operation + "\u0000" + conversationId + "\u0000"
                + transactionId);
    }

    private String providerTransaction(String operationId) {
        return "weave_" + sha256(operationId).substring(0, 48);
    }

    private String digestJson(Object value) {
        return sha256(json(value));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Canonical Chat value could not be persisted.", exception);
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
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

    private String requiredText(String value, String label, int maximum) {
        if (value == null || value.isBlank() || value.length() > maximum) {
            throw new IllegalArgumentException(label + " is invalid");
        }
        return value.trim();
    }

    private String safeCode(String code) {
        if (code == null || !code.matches("[a-z0-9][a-z0-9_-]{0,95}")) {
            return "chat-provider-unavailable";
        }
        return code;
    }

    private ChatConversationJpaEntity requireConversationEntity(
            String tenantId,
            ConversationId conversationId) {
        return jpa.conversations()
                .findById(new ChatPairId(tenantId, conversationId.value()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "canonical chat conversation was not found"));
    }

    private long allocateEventSequence(
            String tenantId,
            ConversationId conversationId) {
        return jpa.conversations()
                .lockForEventSequence(tenantId, conversationId.value())
                .orElseThrow(() -> new IllegalArgumentException(
                        "canonical chat conversation was not found"))
                .allocateEventSequence();
    }

    private ChatEventJpaEntity requireEventEntity(
            String tenantId,
            ConversationId conversationId,
            String eventId) {
        return jpa.events()
                .findById(new ChatTripleId(
                        tenantId, conversationId.value(), eventId))
                .orElseThrow(() -> new IllegalArgumentException(
                        "canonical chat event was not found"));
    }

    private ChatAppserviceTransactionJpaEntity requireCallback(
            String providerKey,
            String transactionId) {
        return jpa.callbacks()
                .findById(new ChatPairId(providerKey, transactionId))
                .orElseThrow(ChatCallbackRetryRequiredException::new);
    }

    private ChatQuarantineJpaEntity requireQuarantine(
            String tenantId,
            String quarantineId) {
        return jpa.quarantines()
                .findById(new ChatPairId(tenantId, quarantineId))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Chat quarantine correlation was not found."));
    }

    private ChatQuadId mappingId(
            String tenantId,
            String providerKey,
            String objectType,
            String canonicalObjectId) {
        return new ChatQuadId(
                tenantId, providerKey, objectType, canonicalObjectId);
    }

    private ChatQuadId membershipId(
            ChatRequestContext context,
            ConversationId conversationId) {
        return new ChatQuadId(
                context.tenantId(),
                conversationId.value(),
                context.identityIssuer(),
                context.actorRef().value());
    }

    private ProviderOperationEcho providerOperationEcho(
            ChatOperationJpaEntity operation) {
        ChatEventContent content = jpa.events()
                .findById(new ChatTripleId(
                        operation.tenantId(),
                        operation.conversationId(),
                        operation.canonicalObjectId()))
                .map(ChatEventJpaEntity::contentJson)
                .map(this::readContent)
                .orElse(null);
        return new ProviderOperationEcho(
                operation.tenantId(),
                operation.operationId(),
                operation.operationType(),
                operation.providerTransactionId(),
                operation.identityIssuer(),
                operation.actorRef(),
                operation.conversationId(),
                operation.canonicalObjectId(),
                content);
    }

    private record OperationRow(
            String canonicalObjectId,
            String providerTransactionId,
            String providerAliasIntent,
            String payloadDigest,
            String state) {
    }

    private record EventOwner(String identityIssuer, String actorRef) {
    }

    private record MembershipRow(String state) {
    }

    private record CallbackRow(
            String state,
            String payloadDigest,
            int eventCount,
            String fingerprintVersion) {
    }

    private record QuarantineDisposition(
            String categoryCode,
            boolean recoverable,
            String lifecycleState) {
    }

    private record QuarantineCandidate(String tenantId, String correlationHash) {
    }

    private record QuarantineRow(
            String quarantineId,
            String conversationId,
            String correlationHash,
            boolean recoverable,
            String classifierVersion,
            String lifecycleState,
            int attemptCount,
            int maxAttempts,
            String normalizedEventJson) {
    }

    private record ProviderOperationEcho(
            String tenantId,
            String operationId,
            String operationType,
            String providerTransactionId,
            String identityIssuer,
            String actorRef,
            String conversationId,
            String canonicalObjectId,
            ChatEventContent content) {
    }

    private record ActorIdentity(String issuer, String actorRef) {
    }
}
