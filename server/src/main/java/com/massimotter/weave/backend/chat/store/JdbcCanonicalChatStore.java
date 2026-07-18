package com.massimotter.weave.backend.chat.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public final class JdbcCanonicalChatStore implements CanonicalChatStore {

    private static final String COMMITTED = "committed";
    private static final int DEFAULT_RECONCILIATION_ATTEMPTS = 3;
    private static final ChatHistoryPolicy HISTORY_POLICY = new ChatHistoryPolicy(
            "conversation_members",
            "organization_default_retention",
            false,
            true,
            List.of("Weave canonical history policy is independent of provider retention controls."));

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final MatrixSynapseCompatibilityProfile compatibilityProfile;

    public JdbcCanonicalChatStore(JdbcTemplate jdbc, ObjectMapper objectMapper, Clock clock) {
        this(jdbc, objectMapper, clock, MatrixSynapseCompatibilityProfile.pinned());
    }

    public JdbcCanonicalChatStore(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            Clock clock,
            MatrixSynapseCompatibilityProfile compatibilityProfile) {
        if (jdbc == null || jdbc.getDataSource() == null) {
            throw new IllegalArgumentException("JdbcCanonicalChatStore requires a JdbcTemplate with a DataSource.");
        }
        if (compatibilityProfile == null) {
            throw new IllegalArgumentException("JdbcCanonicalChatStore requires a Matrix/Synapse compatibility profile.");
        }
        this.jdbc = jdbc;
        DataSource dataSource = jdbc.getDataSource();
        this.transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        this.objectMapper = objectMapper == null ? new ObjectMapper().findAndRegisterModules() : objectMapper;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.compatibilityProfile = compatibilityProfile;
    }

    @Override
    public String persistencePosture() {
        return "durable-relational-flyway";
    }

    @Override
    public ChatConversations joinedConversations(ChatRequestContext context) {
        List<ChatConversation> conversations = jdbc.query(
                "select c.tenant_id, c.conversation_id, c.title, c.conversation_kind, c.open_to_workspace, "
                        + "c.encryption_mode, c.updated_at_utc from weave_chat_conversations c "
                        + "join weave_chat_memberships m on m.tenant_id = c.tenant_id "
                        + "and m.conversation_id = c.conversation_id "
                        + "where c.tenant_id = ? and c.lifecycle_state = 'committed' "
                        + "and c.context_id = ? "
                        + "and m.identity_issuer = ? and m.actor_ref = ? and m.membership_state = 'joined' "
                        + "and not exists (select 1 from weave_chat_provider_mappings mapping "
                        + "where mapping.tenant_id = c.tenant_id and mapping.object_type = 'conversation' "
                        + "and mapping.canonical_object_id = c.conversation_id "
                        + "and mapping.mapping_state = 'degraded') "
                        + "order by c.updated_at_utc desc, c.conversation_id",
                (rs, row) -> mapConversation(rs, context.actorRef()),
                context.tenantId(), context.contextId(), context.identityIssuer(), context.actorRef().value());
        return new ChatConversations(null, conversations);
    }

    @Override
    public ChatCursor currentCursor(ChatRequestContext context) {
        Long value = jdbc.queryForObject(
                "select coalesce(max(changes.sequence_value), 0) from weave_chat_changes changes "
                        + "join weave_chat_memberships membership on membership.tenant_id = changes.tenant_id "
                        + "and membership.conversation_id = changes.conversation_id "
                        + "join weave_chat_conversations conversation on conversation.tenant_id = changes.tenant_id "
                        + "and conversation.conversation_id = changes.conversation_id "
                        + "where changes.tenant_id = ? and membership.identity_issuer = ? "
                        + "and membership.actor_ref = ? and membership.membership_state = 'joined' "
                        + "and conversation.context_id = ?",
                Long.class,
                context.tenantId(), context.identityIssuer(), context.actorRef().value(), context.contextId());
        return new ChatCursor("chat-revision-" + (value == null ? 0 : value));
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
        List<ChatTimelineEvent> reverse = jdbc.query(
                "select event_id, conversation_id, sender_ref, occurred_at_utc, content_json, delivery_state, redacted "
                        + "from weave_chat_events where tenant_id = ? and conversation_id = ? "
                        + "and delivery_state = 'committed' and sequence_value < ? "
                        + "order by sequence_value desc limit ?",
                (rs, row) -> mapEvent(rs),
                context.tenantId(), conversationId.value(), before, bounded);
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
            jdbc.update(
                    "insert into weave_chat_conversations "
                            + "(tenant_id, context_id, conversation_id, title, conversation_kind, open_to_workspace, "
                            + "lifecycle_state, encryption_mode, created_at_utc, updated_at_utc) "
                            + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    context.tenantId(), context.contextId(), conversationId, safeTitle, safeKind, false, "pending",
                    encryptionMode, utc(now), utc(now));
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
            jdbc.update("update weave_chat_conversations set lifecycle_state = 'committed', updated_at_utc = ? "
                            + "where tenant_id = ? and conversation_id = ?",
                    utc(now), context.tenantId(), prepared.conversationId().value());
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
            int updated = jdbc.update("update weave_chat_memberships set membership_state = ?, "
                            + "joined_at_utc = ?, updated_at_utc = ? where tenant_id = ? and conversation_id = ? "
                            + "and identity_issuer = ? and actor_ref = ?",
                    prepared.targetState(),
                    "joined".equals(prepared.targetState()) ? utc(now) : null,
                    utc(now), context.tenantId(), prepared.conversationId().value(),
                    context.identityIssuer(), context.actorRef().value());
            if (updated == 0 && "joined".equals(prepared.targetState())) {
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
            jdbc.update("update weave_chat_conversations set encryption_mode = ?, updated_at_utc = ? "
                            + "where tenant_id = ? and conversation_id = ? and encryption_mode in ('unencrypted', ?)",
                    prepared.algorithm(), utc(now), context.tenantId(), prepared.conversationId().value(),
                    prepared.algorithm());
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
            jdbc.update("insert into weave_chat_events "
                            + "(tenant_id, conversation_id, event_id, sender_issuer, sender_ref, event_kind, content_json, "
                            + "occurred_at_utc, delivery_state, redacted) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    context.tenantId(), conversationId.value(), eventId, context.identityIssuer(),
                    context.actorRef().value(), content.kind().value(), contentJson, utc(now), "pending", false);
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
            jdbc.update("update weave_chat_events set delivery_state = 'committed' "
                            + "where tenant_id = ? and conversation_id = ? and event_id = ?",
                    context.tenantId(), prepared.event().conversationId(), prepared.event().eventId());
            acknowledgeMapping(context.tenantId(), providerKey, "event", prepared.event().eventId(),
                    providerEventRef, providerSourceVersion);
            acknowledgeOperation(context.tenantId(), prepared.operationId());
            recordLedger(context.tenantId(), providerKey, "outbound", prepared.providerTransactionId(),
                    providerEventRef, prepared.event().eventId(), providerSourceVersion, "acknowledged");
            if (count("select count(*) from weave_chat_changes where tenant_id = ? and conversation_id = ? "
                            + "and canonical_object_id = ?",
                    context.tenantId(), prepared.event().conversationId(), prepared.event().eventId()) == 0) {
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
            jdbc.update("update weave_chat_events set redacted = true where tenant_id = ? "
                            + "and conversation_id = ? and event_id = ?",
                    context.tenantId(), prepared.event().conversationId(), prepared.event().eventId());
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
            jdbc.update("update weave_chat_operations set operation_state = 'failed_retryable', "
                            + "attempt_count = attempt_count + 1, last_error_code = ?, updated_at_utc = ? "
                            + "where tenant_id = ? and operation_id = ? and operation_state <> 'committed'",
                    safeCode(supportSafeCode), utc(now), tenantId, operationId);
            jdbc.update("update weave_chat_outbox set outbox_state = 'pending', attempt_count = attempt_count + 1, "
                            + "next_attempt_at_utc = ?, last_error_code = ?, updated_at_utc = ? "
                            + "where tenant_id = ? and operation_id = ? and outbox_state <> 'acknowledged'",
                    retryAt == null ? null : utc(retryAt), safeCode(supportSafeCode), utc(now), tenantId, operationId);
        });
    }

    @Override
    public Optional<RetryWindow> activeRetryWindow(
            String tenantId,
            String operationId,
            Instant observedAt) {
        Instant now = observedAt == null ? clock.instant() : observedAt;
        return jdbc.query("select operations.last_error_code, outbox.next_attempt_at_utc "
                        + "from weave_chat_operations operations join weave_chat_outbox outbox "
                        + "on outbox.tenant_id = operations.tenant_id and outbox.operation_id = operations.operation_id "
                        + "where operations.tenant_id = ? and operations.operation_id = ? "
                        + "and operations.operation_state = 'failed_retryable' "
                        + "and outbox.outbox_state = 'pending' and outbox.next_attempt_at_utc is not null",
                (rs, row) -> new RetryWindow(
                        safeCode(rs.getString("last_error_code")),
                        instant(rs, "next_attempt_at_utc")),
                tenantId,
                operationId).stream()
                .filter(window -> window.retryAt().isAfter(now))
                .findFirst();
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
            jdbc.update("delete from weave_chat_read_receipts where tenant_id = ? and conversation_id = ? "
                            + "and identity_issuer = ? and actor_ref = ?",
                    context.tenantId(), conversationId.value(), context.identityIssuer(), context.actorRef().value());
            jdbc.update("insert into weave_chat_read_receipts "
                            + "(tenant_id, conversation_id, identity_issuer, actor_ref, event_id, read_at_utc) "
                            + "values (?, ?, ?, ?, ?, ?)",
                    context.tenantId(), conversationId.value(), context.identityIssuer(), context.actorRef().value(),
                    eventId, utc(timestamp));
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
        List<ChatChange> result = jdbc.query(
                "select changes.sequence_value, changes.change_kind, changes.conversation_id, "
                        + "changes.canonical_object_id, changes.occurred_at_utc "
                        + "from weave_chat_changes changes join weave_chat_memberships membership "
                        + "on membership.tenant_id = changes.tenant_id "
                        + "and membership.conversation_id = changes.conversation_id "
                        + "join weave_chat_conversations conversation on conversation.tenant_id = changes.tenant_id "
                        + "and conversation.conversation_id = changes.conversation_id "
                        + "where changes.tenant_id = ? and changes.sequence_value > ? "
                        + "and membership.identity_issuer = ? and membership.actor_ref = ? "
                        + "and membership.membership_state = 'joined' "
                        + "and conversation.context_id = ? "
                        + "and not exists (select 1 from weave_chat_provider_mappings mapping "
                        + "where mapping.tenant_id = conversation.tenant_id "
                        + "and mapping.object_type = 'conversation' "
                        + "and mapping.canonical_object_id = conversation.conversation_id "
                        + "and mapping.mapping_state = 'degraded') "
                        + "order by changes.sequence_value limit ?",
                (rs, row) -> new ChatChange(
                        rs.getLong("sequence_value"),
                        rs.getString("change_kind"),
                        new ConversationId(rs.getString("conversation_id")),
                        rs.getString("canonical_object_id"),
                        instant(rs, "occurred_at_utc")),
                context.tenantId(), after, context.identityIssuer(), context.actorRef().value(),
                context.contextId(), bounded);
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
        jdbc.update("insert into weave_chat_provider_mappings "
                        + "(tenant_id, provider_key, object_type, canonical_object_id, provider_ref, "
                        + "mapping_intent_ref, provider_source_version, mapping_state, updated_at_utc) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?) on conflict do nothing",
                tenantId, providerKey, objectType, canonicalObjectId, providerRef, mappingIntentRef,
                null, "pending", utc(clock.instant()));
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
        int updated = jdbc.update("update weave_chat_provider_mappings set provider_ref = ?, provider_source_version = ?, "
                        + "mapping_state = 'acknowledged', updated_at_utc = ? where tenant_id = ? and provider_key = ? "
                        + "and object_type = ? and canonical_object_id = ?",
                providerRef, providerSourceVersion, utc(clock.instant()), tenantId, providerKey, objectType,
                canonicalObjectId);
        if (updated == 0) {
            reserveMapping(tenantId, providerKey, objectType, canonicalObjectId, providerRef, null);
            jdbc.update("update weave_chat_provider_mappings set provider_ref = ?, provider_source_version = ?, "
                            + "mapping_state = 'acknowledged', updated_at_utc = ? where tenant_id = ? "
                            + "and provider_key = ? and object_type = ? and canonical_object_id = ?",
                    providerRef, providerSourceVersion, utc(clock.instant()), tenantId, providerKey, objectType,
                    canonicalObjectId);
        }
        return mapping(tenantId, providerKey, objectType, canonicalObjectId).orElseThrow();
    }

    @Override
    public Optional<ProviderMapping> mapping(
            String tenantId,
            String providerKey,
            String objectType,
            String canonicalObjectId) {
        return jdbc.query("select tenant_id, provider_key, object_type, canonical_object_id, provider_ref, "
                        + "mapping_intent_ref, provider_source_version, mapping_state "
                        + "from weave_chat_provider_mappings where tenant_id = ? and provider_key = ? "
                        + "and object_type = ? and canonical_object_id = ?",
                (rs, row) -> mapProviderMapping(rs),
                tenantId, providerKey, objectType, canonicalObjectId).stream().findFirst();
    }

    @Override
    public Optional<ProviderMapping> mappingByProviderRef(
            String providerKey,
            String objectType,
            String providerRef) {
        return jdbc.query("select tenant_id, provider_key, object_type, canonical_object_id, provider_ref, "
                        + "mapping_intent_ref, provider_source_version, mapping_state "
                        + "from weave_chat_provider_mappings where provider_key = ? and object_type = ? "
                        + "and provider_ref = ?",
                (rs, row) -> mapProviderMapping(rs),
                providerKey, objectType, providerRef).stream().findFirst();
    }

    @Override
    public Optional<ProviderMapping> mappingByIntent(
            String providerKey,
            String objectType,
            String mappingIntentRef) {
        return jdbc.query("select tenant_id, provider_key, object_type, canonical_object_id, provider_ref, "
                        + "mapping_intent_ref, provider_source_version, mapping_state "
                        + "from weave_chat_provider_mappings where provider_key = ? and object_type = ? "
                        + "and mapping_intent_ref = ?",
                (rs, row) -> mapProviderMapping(rs),
                providerKey, objectType, mappingIntentRef).stream().findFirst();
    }

    @Override
    public Optional<String> contextId(String tenantId, ConversationId conversationId) {
        return jdbc.query("select context_id from weave_chat_conversations where tenant_id = ? "
                        + "and conversation_id = ? and lifecycle_state = 'committed'",
                (rs, row) -> rs.getString("context_id"), tenantId, conversationId.value()).stream().findFirst();
    }

    @Override
    public List<String> acknowledgedProviderEventRefs(
            String tenantId,
            ConversationId conversationId,
            String providerKey) {
        return jdbc.query("select mapping.provider_ref from weave_chat_events event "
                        + "join weave_chat_provider_mappings mapping on mapping.tenant_id = event.tenant_id "
                        + "and mapping.provider_key = ? and mapping.object_type = 'event' "
                        + "and mapping.canonical_object_id = event.event_id "
                        + "where event.tenant_id = ? and event.conversation_id = ? "
                        + "and event.delivery_state = 'committed' and mapping.mapping_state = 'acknowledged' "
                        + "order by event.sequence_value",
                (rs, row) -> rs.getString("provider_ref"),
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
            int inserted = jdbc.update("insert into weave_chat_appservice_transactions "
                            + "(provider_key, homeserver_transaction_id, payload_digest, transaction_state, event_count, "
                            + "duplicate_count, received_at_utc, completed_at_utc, semantic_fingerprint_version, "
                            + "semantic_mismatch_count, semantic_mismatch_hash) "
                            + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) on conflict do nothing",
                    safeProvider, safeTransaction, payloadDigest, "processing", eventCount, 0,
                    utc(clock.instant()), null, compatibilityProfile.semanticFingerprintVersion(), 0, null);
            if (inserted == 1) {
                return CallbackStart.NEW;
            }
            CallbackRow raced = callbackRow(safeProvider, safeTransaction)
                    .orElseThrow(ChatCallbackRetryRequiredException::new);
            return existingCallbackStart(safeProvider, safeTransaction, payloadDigest, eventCount, raced);
        });
    }

    private Optional<CallbackRow> callbackRow(String providerKey, String transactionId) {
        return jdbc.query(
                "select transaction_state, payload_digest, event_count, semantic_fingerprint_version "
                        + "from weave_chat_appservice_transactions "
                        + "where provider_key = ? and homeserver_transaction_id = ?",
                (rs, row) -> new CallbackRow(
                        rs.getString("transaction_state"),
                        rs.getString("payload_digest"),
                        rs.getInt("event_count"),
                        rs.getString("semantic_fingerprint_version")),
                providerKey, transactionId).stream().findFirst();
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
            jdbc.update("update weave_chat_appservice_transactions set "
                            + "transaction_state = 'semantic-mismatch', "
                            + "semantic_mismatch_count = semantic_mismatch_count + 1, "
                            + "semantic_mismatch_hash = ? "
                            + "where provider_key = ? and homeserver_transaction_id = ?",
                    mismatchHash, providerKey, transactionId);
            return CallbackStart.SEMANTIC_MISMATCH;
        }
        if ("completed".equals(callback.state())) {
            jdbc.update("update weave_chat_appservice_transactions set duplicate_count = duplicate_count + 1 "
                            + "where provider_key = ? and homeserver_transaction_id = ?",
                    providerKey, transactionId);
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
            jdbc.update("update weave_chat_events set delivery_state = 'committed' where tenant_id = ? "
                            + "and conversation_id = ? and event_id = ?",
                    echo.tenantId(), echo.conversationId(), echo.canonicalObjectId());
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
        MatrixSynapseCompatibilityProfile.StateClassification stateClassification =
                compatibilityProfile.classify(event.eventType(), event.stateKey() != null);
        if (stateClassification == MatrixSynapseCompatibilityProfile.StateClassification.UNKNOWN_RECOVERABLE) {
            return quarantineMappedConversation(
                    room.tenantId(), room.canonicalObjectId(), providerKey, event,
                    "provider-state-event-type-unsupported");
        }
        if (stateClassification == MatrixSynapseCompatibilityProfile.StateClassification.SUPPORTED_IGNORED) {
            recordLedger(room.tenantId(), providerKey, "inbound", event.providerTransactionId(),
                    event.providerEventRef(), room.canonicalObjectId(), event.providerSourceVersion(),
                    "ignored-supported-state");
            return new CallbackEventResult("ignored", sha256(room.tenantId() + ":" + event.providerEventRef()));
        }
        if ("m.room.message".equals(event.eventType()) && !"unencrypted".equals(encryptionMode)) {
            return quarantineMappedConversation(
                    room.tenantId(), room.canonicalObjectId(), providerKey, event, "plaintext-in-encrypted-room");
        }
        if (stateClassification == MatrixSynapseCompatibilityProfile.StateClassification.KNOWN_STATE_KEY_MISSING) {
            return quarantineMappedConversation(
                    room.tenantId(), room.canonicalObjectId(), providerKey, event,
                    "provider-state-key-missing");
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
                        room.tenantId(), room.canonicalObjectId(), providerKey, event, "encrypted-envelope-invalid");
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
        int inserted = jdbc.update("insert into weave_chat_events "
                        + "(tenant_id, conversation_id, event_id, sender_issuer, sender_ref, event_kind, content_json, "
                        + "occurred_at_utc, delivery_state, redacted) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "on conflict do nothing",
                room.tenantId(), conversationId.value(), canonicalEventId, actor.issuer(), actor.actorRef(),
                content.kind().value(), json(content), utc(clock.instant()), COMMITTED, false);
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
                inserted == 0 ? "deduplicated" : "accepted",
                sha256(room.tenantId() + ":" + canonicalEventId));
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
                || !event.content().isEmpty()) {
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
        jdbc.update("update weave_chat_events set redacted = true where tenant_id = ? "
                        + "and conversation_id = ? and event_id = ?",
                echo.tenantId(), echo.conversationId(), echo.canonicalObjectId());
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

    @Override
    public CallbackEventResult recordMalformedCallbackEvent(
            String providerKey,
            String transactionId,
            String eventDigest,
            String reasonCode) {
        String correlationHash = sha256(providerKey + "\u0000" + transactionId + "\u0000" + eventDigest);
        String quarantineId = "quarantine-" + correlationHash;
        Instant observedAt = clock.instant();
        jdbc.update("insert into weave_chat_quarantine "
                        + "(tenant_id, quarantine_id, provider_key, correlation_hash, reason_code, observed_at_utc, "
                        + "category_code, recoverable, classifier_version, lifecycle_state, attempt_count, max_attempts, "
                        + "private_homeserver_transaction_id, resolved_at_utc, last_outcome_code) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) on conflict do nothing",
                "unknown-private", quarantineId, providerKey, correlationHash, safeCode(reasonCode), utc(observedAt),
                "provider-malformed", false, compatibilityProfile.classifierVersion(), "rejected", 0,
                DEFAULT_RECONCILIATION_ATTEMPTS, transactionId, utc(observedAt), "callback-event-malformed");
        return new CallbackEventResult("quarantined-poison", correlationHash);
    }

    @Override
    public void completeCallback(String providerKey, String transactionId, int duplicateCount) {
        jdbc.update("update weave_chat_appservice_transactions set transaction_state = 'completed', "
                        + "duplicate_count = duplicate_count + ?, completed_at_utc = ? "
                        + "where provider_key = ? and homeserver_transaction_id = ? "
                        + "and transaction_state = 'processing'",
                Math.max(0, duplicateCount), utc(clock.instant()), providerKey, transactionId);
    }

    @Override
    public long systemicCallbackIntegrityFailureCount(String providerKey) {
        return count("select count(*) from weave_chat_appservice_transactions where provider_key = ? "
                + "and transaction_state = 'semantic-mismatch'", requiredText(providerKey, "Chat provider key", 64));
    }

    @Override
    public List<QuarantineReconciliationResult> reconcilePendingQuarantines(
            String providerKey,
            int limit) {
        String safeProvider = requiredText(providerKey, "Chat provider key", 64);
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        List<QuarantineCandidate> candidates = jdbc.query(
                "select tenant_id, correlation_hash from weave_chat_quarantine "
                        + "where provider_key = ? and lifecycle_state = 'pending' and recoverable = true "
                        + "and classifier_version <> ? order by observed_at_utc, quarantine_id limit ?",
                (rs, row) -> new QuarantineCandidate(
                        rs.getString("tenant_id"),
                        rs.getString("correlation_hash")),
                safeProvider,
                compatibilityProfile.classifierVersion(),
                boundedLimit);
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
            QuarantineRow quarantine = jdbc.query(
                            "select quarantine_id, conversation_id, correlation_hash, recoverable, classifier_version, "
                                    + "lifecycle_state, attempt_count, max_attempts, private_normalized_event_json "
                                    + "from weave_chat_quarantine where tenant_id = ? and provider_key = ? "
                                    + "and correlation_hash = ? for update",
                            (rs, row) -> new QuarantineRow(
                                    rs.getString("quarantine_id"),
                                    rs.getString("conversation_id"),
                                    rs.getString("correlation_hash"),
                                    rs.getBoolean("recoverable"),
                                    rs.getString("classifier_version"),
                                    rs.getString("lifecycle_state"),
                                    rs.getInt("attempt_count"),
                                    rs.getInt("max_attempts"),
                                    rs.getString("private_normalized_event_json")),
                            safeTenant, safeProvider, correlationHash).stream().findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Chat quarantine correlation was not found."));
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
            jdbc.update("update weave_chat_quarantine set lifecycle_state = 'reconciled', attempt_count = ?, "
                            + "last_attempt_at_utc = ?, resolved_at_utc = ?, last_outcome_code = ?, "
                            + "classifier_version = ? where tenant_id = ? and quarantine_id = ?",
                    attempts, utc(now), utc(now), "reconciliation-committed",
                    compatibilityProfile.classifierVersion(), safeTenant, quarantine.quarantineId());
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
                count("select count(*) from weave_chat_conversations where tenant_id = ? and conversation_id = ? "
                        + "and lifecycle_state = 'committed'", tenantId, conversation),
                count("select count(*) from weave_chat_memberships where tenant_id = ? and conversation_id = ? "
                        + "and membership_state = 'joined'", tenantId, conversation),
                count("select count(*) from weave_chat_events where tenant_id = ? and conversation_id = ? "
                        + "and delivery_state = 'committed'", tenantId, conversation),
                count("select count(*) from weave_chat_events where tenant_id = ? and conversation_id = ? "
                        + "and delivery_state = 'committed' and event_kind = 'encrypted'", tenantId, conversation),
                count("select count(*) from weave_chat_events where tenant_id = ? and conversation_id = ? "
                        + "and delivery_state = 'committed' and event_kind = 'message'", tenantId, conversation),
                count("select count(*) from weave_chat_operations where tenant_id = ? and conversation_id = ? "
                        + "and operation_state = 'pending'", tenantId, conversation),
                count("select count(*) from weave_chat_operations where tenant_id = ? and conversation_id = ? "
                        + "and operation_state = 'failed_retryable'", tenantId, conversation),
                count("select count(*) from weave_chat_operations where tenant_id = ? and conversation_id = ? "
                        + "and operation_state = 'committed'", tenantId, conversation),
                count("select count(*) from weave_chat_bridge_ledger ledger where ledger.tenant_id = ? "
                                + "and ledger.provider_key = ? and (ledger.canonical_object_id = ? "
                                + "or ledger.canonical_object_id in (select event_id from weave_chat_events "
                                + "where tenant_id = ? and conversation_id = ?) "
                                + "or ledger.provider_transaction_id in (select provider_transaction_id "
                                + "from weave_chat_operations where tenant_id = ? and conversation_id = ?))",
                        tenantId, activeProviderKey, conversation, tenantId, conversation, tenantId, conversation),
                count("select count(*) from weave_chat_appservice_transactions where provider_key = ?", activeProviderKey),
                count("select coalesce(sum(duplicate_count), 0) from weave_chat_appservice_transactions "
                        + "where provider_key = ?", activeProviderKey),
                count("select coalesce(sum(semantic_mismatch_count), 0) "
                        + "from weave_chat_appservice_transactions where provider_key = ?", activeProviderKey),
                count("select count(*) from weave_chat_quarantine where provider_key = ? and tenant_id = ? "
                                + "and conversation_id = ? and lifecycle_state in ('pending', 'rejected')",
                        activeProviderKey, tenantId, conversation),
                count("select count(*) from weave_chat_provider_mappings where tenant_id = ? and provider_key = ? "
                                + "and object_type = 'conversation' and canonical_object_id = ? "
                                + "and mapping_state = 'degraded'",
                        tenantId, activeProviderKey, conversation),
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
        jdbc.update("insert into weave_chat_operations "
                        + "(tenant_id, context_id, operation_id, operation_type, identity_issuer, actor_ref, conversation_id, "
                        + "canonical_object_id, northbound_transaction_id, provider_transaction_id, "
                        + "provider_alias_intent, payload_digest, operation_state, attempt_count, last_error_code, "
                        + "created_at_utc, updated_at_utc) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                context.tenantId(), context.contextId(), operationId, operationType,
                context.identityIssuer(), context.actorRef().value(),
                conversationId, canonicalObjectId, northboundTransactionId, providerTransactionId,
                providerAliasIntent, payloadDigest, "pending", 0, null, utc(now), utc(now));
        jdbc.update("insert into weave_chat_outbox "
                        + "(tenant_id, operation_id, operation_type, payload_json, provider_transaction_id, "
                        + "outbox_state, attempt_count, next_attempt_at_utc, last_error_code, created_at_utc, updated_at_utc) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                context.tenantId(), operationId, operationType, outboxPayload, providerTransactionId,
                "pending", 0, utc(now), null, utc(now), utc(now));
    }

    private void acknowledgeOperation(String tenantId, String operationId) {
        Instant now = clock.instant();
        jdbc.update("update weave_chat_operations set operation_state = 'committed', last_error_code = null, "
                        + "updated_at_utc = ? where tenant_id = ? and operation_id = ?",
                utc(now), tenantId, operationId);
        jdbc.update("update weave_chat_outbox set outbox_state = 'acknowledged', last_error_code = null, "
                        + "next_attempt_at_utc = null, updated_at_utc = ? where tenant_id = ? and operation_id = ?",
                utc(now), tenantId, operationId);
    }

    private Optional<OperationRow> operation(String tenantId, String operationId) {
        return jdbc.query("select canonical_object_id, provider_transaction_id, provider_alias_intent, "
                        + "payload_digest, operation_state from weave_chat_operations where tenant_id = ? "
                        + "and operation_id = ?",
                (rs, row) -> new OperationRow(
                        rs.getString("canonical_object_id"),
                        rs.getString("provider_transaction_id"),
                        rs.getString("provider_alias_intent"),
                        rs.getString("payload_digest"),
                        rs.getString("operation_state")),
                tenantId, operationId).stream().findFirst();
    }

    private Optional<ProviderOperationEcho> providerOperation(String providerTransactionId) {
        if (providerTransactionId == null || providerTransactionId.isBlank()) {
            return Optional.empty();
        }
        return jdbc.query("select operations.tenant_id, operations.operation_id, operations.operation_type, "
                        + "operations.provider_transaction_id, "
                        + "operations.identity_issuer, operations.actor_ref, operations.conversation_id, "
                        + "operations.canonical_object_id, events.content_json "
                        + "from weave_chat_operations operations left join weave_chat_events events "
                        + "on events.tenant_id = operations.tenant_id "
                        + "and events.conversation_id = operations.conversation_id "
                        + "and events.event_id = operations.canonical_object_id "
                        + "where operations.provider_transaction_id = ?",
                (rs, row) -> new ProviderOperationEcho(
                        rs.getString("tenant_id"),
                        rs.getString("operation_id"),
                        rs.getString("operation_type"),
                        rs.getString("provider_transaction_id"),
                        rs.getString("identity_issuer"),
                        rs.getString("actor_ref"),
                        rs.getString("conversation_id"),
                        rs.getString("canonical_object_id"),
                        rs.getString("content_json") == null ? null : readContent(rs.getString("content_json"))),
                providerTransactionId).stream().findFirst();
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
        List<ProviderOperationEcho> candidates = jdbc.query(
                "select operations.tenant_id, operations.operation_id, operations.operation_type, "
                        + "operations.provider_transaction_id, operations.identity_issuer, operations.actor_ref, "
                        + "operations.conversation_id, operations.canonical_object_id, events.content_json "
                        + "from weave_chat_operations operations left join weave_chat_events events "
                        + "on events.tenant_id = operations.tenant_id "
                        + "and events.conversation_id = operations.conversation_id "
                        + "and events.event_id = operations.canonical_object_id "
                        + "left join weave_chat_bridge_ledger ledger on ledger.tenant_id = operations.tenant_id "
                        + "and ledger.provider_key = ? and ledger.direction = 'outbound' "
                        + "and ledger.provider_transaction_id = operations.provider_transaction_id "
                        + "where operations.tenant_id = ? and operations.conversation_id = ? "
                        + "and operations.operation_type in ('send-event', 'redact-event') "
                        + "and (operations.operation_state in ('pending', 'failed_retryable') "
                        + "or (operations.operation_state = 'committed' "
                        + "and operations.operation_type = 'redact-event' and ledger.provider_event_ref = ?))",
                (rs, row) -> new ProviderOperationEcho(
                        rs.getString("tenant_id"),
                        rs.getString("operation_id"),
                        rs.getString("operation_type"),
                        rs.getString("provider_transaction_id"),
                        rs.getString("identity_issuer"),
                        rs.getString("actor_ref"),
                        rs.getString("conversation_id"),
                        rs.getString("canonical_object_id"),
                        rs.getString("content_json") == null ? null : readContent(rs.getString("content_json"))),
                providerKey, room.get().tenantId(), room.get().canonicalObjectId(), event.providerEventRef());
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
                        && event.content().isEmpty()
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
        return count("select count(*) from weave_chat_operations operations "
                        + "where operations.tenant_id = ? and operations.operation_type = 'create-room' "
                        + "and operations.identity_issuer = ? and operations.actor_ref = ? "
                        + "and operations.operation_state in ('pending', 'failed_retryable')",
                sender.get().tenantId(), actor.issuer(), actor.actorRef()) == 1;
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
        jdbc.update("insert into weave_chat_memberships "
                        + "(tenant_id, conversation_id, identity_issuer, actor_ref, member_role, membership_state, "
                        + "invited_at_utc, joined_at_utc, updated_at_utc) values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                tenantId, conversationId, issuer, actor.value(), "member", state,
                "invited".equals(state) ? utc(updated) : null,
                joinedAt == null ? null : utc(joinedAt), utc(updated));
    }

    private Optional<MembershipRow> membership(ChatRequestContext context, ConversationId conversationId) {
        return jdbc.query("select membership.membership_state from weave_chat_memberships membership "
                        + "join weave_chat_conversations conversation on conversation.tenant_id = membership.tenant_id "
                        + "and conversation.conversation_id = membership.conversation_id "
                        + "where membership.tenant_id = ? and membership.conversation_id = ? "
                        + "and membership.identity_issuer = ? and membership.actor_ref = ? "
                        + "and conversation.context_id = ?",
                (rs, row) -> new MembershipRow(rs.getString("membership_state")),
                context.tenantId(), conversationId.value(), context.identityIssuer(), context.actorRef().value(),
                context.contextId())
                .stream().findFirst();
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
        if (count("select count(*) from weave_chat_provider_mappings where tenant_id = ? "
                        + "and object_type = 'conversation' and canonical_object_id = ? "
                        + "and mapping_state = 'degraded'",
                tenantId, conversationId.value()) > 0) {
            throw new ChatProviderUnavailableException("chat-conversation-mapping-degraded");
        }
    }

    private boolean openToWorkspace(String tenantId, String contextId, ConversationId conversationId) {
        return jdbc.query("select open_to_workspace from weave_chat_conversations where tenant_id = ? "
                        + "and context_id = ? and conversation_id = ? and lifecycle_state = 'committed'",
                (rs, row) -> rs.getBoolean("open_to_workspace"), tenantId, contextId, conversationId.value())
                .stream().findFirst().orElse(false);
    }

    private Optional<ChatConversation> findConversation(
            String tenantId,
            ConversationId conversationId,
            ChatActorRef actorRef) {
        return jdbc.query("select tenant_id, conversation_id, title, conversation_kind, open_to_workspace, "
                        + "encryption_mode, updated_at_utc from weave_chat_conversations where tenant_id = ? "
                        + "and conversation_id = ? and lifecycle_state = 'committed'",
                (rs, row) -> mapConversation(rs, actorRef), tenantId, conversationId.value()).stream().findFirst();
    }

    private Optional<ChatConversation> mapConversationWithoutAuthorization(
            String tenantId,
            ConversationId conversationId,
            ChatActorRef actorRef) {
        return findConversation(tenantId, conversationId, actorRef);
    }

    private ChatConversation mapConversation(ResultSet rs, ChatActorRef actorRef) throws SQLException {
        String tenantId = rs.getString("tenant_id");
        String conversationId = rs.getString("conversation_id");
        String mode = rs.getString("encryption_mode");
        List<ChatMembership> memberships = jdbc.query(
                "select actor_ref, member_role, membership_state, joined_at_utc from weave_chat_memberships "
                        + "where tenant_id = ? and conversation_id = ? order by actor_ref",
                (memberRs, row) -> new ChatMembership(
                        "membership-" + sha256(tenantId + ":" + conversationId + ":" + memberRs.getString("actor_ref"))
                                .substring(0, 32),
                        conversationId,
                        memberRs.getString("actor_ref"),
                        memberRs.getString("member_role"),
                        memberRs.getString("membership_state"),
                        nullableInstant(memberRs, "joined_at_utc"),
                        "joined".equals(memberRs.getString("membership_state"))
                                ? List.of("chat.read", "chat.send") : List.of()),
                tenantId, conversationId);
        return new ChatConversation(
                conversationId,
                rs.getString("title"),
                rs.getString("conversation_kind"),
                ChatMemberState.READY,
                "Chat is available through the Weave workspace.",
                instant(rs, "updated_at_utc"),
                "unencrypted".equals(mode) ? ChatEncryptionState.unencrypted() : ChatEncryptionState.matrixMegolm(),
                HISTORY_POLICY,
                memberships,
                List.of());
    }

    private ChatTimelineEvent requireEvent(String tenantId, ConversationId conversationId, String eventId) {
        return jdbc.query("select event_id, conversation_id, sender_ref, occurred_at_utc, content_json, "
                        + "delivery_state, redacted from weave_chat_events where tenant_id = ? and conversation_id = ? "
                        + "and event_id = ?",
                (rs, row) -> mapEvent(rs), tenantId, conversationId.value(), eventId)
                .stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("canonical chat event was not found"));
    }

    private EventOwner eventOwner(String tenantId, ConversationId conversationId, String eventId) {
        return jdbc.query("select sender_issuer, sender_ref from weave_chat_events where tenant_id = ? "
                        + "and conversation_id = ? and event_id = ? and delivery_state = 'committed'",
                (rs, row) -> new EventOwner(rs.getString("sender_issuer"), rs.getString("sender_ref")),
                tenantId, conversationId.value(), eventId).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("canonical chat event was not found"));
    }

    private ChatTimelineEvent mapEvent(ResultSet rs) throws SQLException {
        return new ChatTimelineEvent(
                rs.getString("event_id"),
                rs.getString("conversation_id"),
                rs.getString("sender_ref"),
                instant(rs, "occurred_at_utc"),
                readContent(rs.getString("content_json")),
                rs.getString("delivery_state"),
                rs.getBoolean("redacted"));
    }

    private ChatEventContent readContent(String contentJson) {
        try {
            return objectMapper.readValue(contentJson, ChatEventContent.class);
        } catch (JsonProcessingException exception) {
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
        return jdbc.query("select encryption_mode from weave_chat_conversations where tenant_id = ? "
                        + "and conversation_id = ? and lifecycle_state = 'committed'",
                (rs, row) -> rs.getString("encryption_mode"), tenantId, conversationId.value())
                .stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("canonical chat conversation was not found"));
    }

    private ProviderMapping mapProviderMapping(ResultSet rs) throws SQLException {
        return new ProviderMapping(
                rs.getString("tenant_id"),
                rs.getString("provider_key"),
                rs.getString("object_type"),
                rs.getString("canonical_object_id"),
                rs.getString("provider_ref"),
                rs.getString("mapping_intent_ref"),
                rs.getString("provider_source_version"),
                rs.getString("mapping_state"));
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
        jdbc.update("insert into weave_chat_bridge_ledger "
                        + "(tenant_id, ledger_id, provider_key, direction, provider_transaction_id, "
                        + "provider_event_ref, canonical_object_id, source_version, ledger_state, observed_at_utc) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) on conflict do nothing",
                tenantId, ledgerId, providerKey, direction, providerTransactionId, providerEventRef,
                canonicalObjectId, sourceVersion, state, utc(clock.instant()));
        jdbc.update("update weave_chat_bridge_ledger set ledger_state = ?, source_version = ?, observed_at_utc = ? "
                        + "where tenant_id = ? and ledger_id = ?",
                state, sourceVersion, utc(clock.instant()), tenantId, ledgerId);
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
        jdbc.update("insert into weave_chat_quarantine "
                        + "(tenant_id, quarantine_id, provider_key, correlation_hash, reason_code, observed_at_utc, "
                        + "conversation_id, category_code, recoverable, classifier_version, lifecycle_state, "
                        + "attempt_count, max_attempts, private_homeserver_transaction_id, private_provider_event_ref, "
                        + "private_provider_room_ref, private_normalized_event_json, resolved_at_utc, last_outcome_code) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) on conflict do nothing",
                tenantId, quarantineId, providerKey, correlationHash, safeCode(reason), utc(observedAt),
                conversationId, disposition.categoryCode(), disposition.recoverable(),
                compatibilityProfile.classifierVersion(), disposition.lifecycleState(), 0,
                DEFAULT_RECONCILIATION_ATTEMPTS, event.homeserverTransactionId(), event.providerEventRef(),
                event.providerRoomRef(), json(event),
                "rejected".equals(disposition.lifecycleState()) ? utc(observedAt) : null,
                "quarantine-" + disposition.lifecycleState());
        return new CallbackEventResult("quarantined", correlationHash);
    }

    private CallbackEventResult quarantineMappedConversation(
            String tenantId,
            String conversationId,
            String providerKey,
            ProviderCallbackEvent event,
            String reason) {
        jdbc.update("update weave_chat_provider_mappings set mapping_state = 'degraded', updated_at_utc = ? "
                        + "where tenant_id = ? and provider_key = ? and object_type = 'conversation' "
                        + "and canonical_object_id = ?",
                utc(clock.instant()), tenantId, providerKey, conversationId);
        return quarantine(tenantId, conversationId, providerKey, event, reason);
    }

    private QuarantineDisposition quarantineDisposition(String reason) {
        return switch (reason) {
            case "provider-state-event-type-unsupported" ->
                    new QuarantineDisposition("provider-compatibility", true, "pending");
            case "plaintext-in-encrypted-room", "encrypted-event-policy-mismatch", "encrypted-envelope-invalid" ->
                    new QuarantineDisposition("encryption-policy", false, "rejected");
            case "provider-state-key-missing", "message-content-invalid" ->
                    new QuarantineDisposition("provider-malformed", false, "rejected");
            case "provider-event-type-unsupported" ->
                    new QuarantineDisposition("provider-compatibility", false, "rejected");
            default -> new QuarantineDisposition("canonical-correlation", false, "rejected");
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
        jdbc.update("update weave_chat_quarantine set attempt_count = ?, last_attempt_at_utc = ?, "
                        + "last_outcome_code = ?, classifier_version = ? "
                        + "where tenant_id = ? and quarantine_id = ?",
                attempts, utc(clock.instant()), safeCode(outcomeCode), compatibilityProfile.classifierVersion(),
                tenantId, quarantine.quarantineId());
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
        jdbc.update("update weave_chat_quarantine set lifecycle_state = 'rejected', attempt_count = ?, "
                        + "last_attempt_at_utc = ?, resolved_at_utc = ?, last_outcome_code = ?, "
                        + "classifier_version = ? where tenant_id = ? and quarantine_id = ?",
                attempts, incrementAttempt ? utc(now) : null, utc(now), safeCode(outcomeCode),
                compatibilityProfile.classifierVersion(), tenantId, quarantine.quarantineId());
        return new QuarantineReconciliationResult(
                "rejected", safeCode(outcomeCode), attempts, false, quarantine.correlationHash());
    }

    private QuarantineReconciliationResult supersedeQuarantine(
            String tenantId,
            String providerKey,
            QuarantineRow quarantine) {
        int attempts = quarantine.attemptCount() + 1;
        Instant now = clock.instant();
        jdbc.update("update weave_chat_quarantine set lifecycle_state = 'superseded', attempt_count = ?, "
                        + "last_attempt_at_utc = ?, resolved_at_utc = ?, last_outcome_code = ?, "
                        + "classifier_version = ? where tenant_id = ? and quarantine_id = ?",
                attempts, utc(now), utc(now), "reconciliation-superseded",
                compatibilityProfile.classifierVersion(), tenantId, quarantine.quarantineId());
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
        return count("select count(*) from weave_chat_bridge_ledger where tenant_id = ? and provider_key = ? "
                        + "and provider_event_ref = ? and ledger_state in "
                        + "('acknowledged', 'ignored-supported-state')",
                tenantId, providerKey, providerEventRef) > 0;
    }

    private ProviderCallbackEvent readPrivateCallbackEvent(String normalizedEventJson) {
        if (normalizedEventJson == null || normalizedEventJson.isBlank()) {
            throw new IllegalArgumentException("Private callback reconciliation input is unavailable.");
        }
        try {
            return objectMapper.readValue(normalizedEventJson, ProviderCallbackEvent.class);
        } catch (JsonProcessingException exception) {
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
        long unresolved = count("select count(*) from weave_chat_quarantine where tenant_id = ? "
                        + "and provider_key = ? and conversation_id = ? "
                        + "and lifecycle_state in ('pending', 'rejected')",
                tenantId, providerKey, conversationId);
        if (unresolved > 0) {
            return false;
        }
        int updated = jdbc.update("update weave_chat_provider_mappings set mapping_state = 'acknowledged', "
                        + "updated_at_utc = ? where tenant_id = ? and provider_key = ? "
                        + "and object_type = 'conversation' and canonical_object_id = ? "
                        + "and mapping_state = 'degraded'",
                utc(clock.instant()), tenantId, providerKey, conversationId);
        return updated > 0;
    }

    private Optional<String> callbackEventConversation(String tenantId, String eventId) {
        return jdbc.query("select conversation_id from weave_chat_events where tenant_id = ? and event_id = ?",
                (rs, row) -> rs.getString("conversation_id"),
                tenantId,
                eventId).stream().findFirst();
    }

    private void recordCallbackChangeIfAbsent(
            String tenantId,
            ConversationId conversationId,
            String kind,
            String canonicalObjectId,
            String providerKey,
            String providerEventRef,
            Instant occurredAt) {
        if (count("select count(*) from weave_chat_changes where tenant_id = ? and conversation_id = ? "
                        + "and change_kind = ? and canonical_object_id = ?",
                tenantId, conversationId.value(), kind, canonicalObjectId) > 0) {
            return;
        }
        String deduplicationKey = "callback-" + sha256(providerKey + "\u0000" + providerEventRef);
        jdbc.update("insert into weave_chat_changes "
                        + "(tenant_id, conversation_id, change_kind, canonical_object_id, "
                        + "callback_deduplication_key, occurred_at_utc) values (?, ?, ?, ?, ?, ?) "
                        + "on conflict do nothing",
                tenantId,
                conversationId.value(),
                kind,
                canonicalObjectId,
                deduplicationKey,
                utc(occurredAt));
    }

    private ActorIdentity parseActorKey(String canonicalObjectId) {
        try {
            Map<String, String> value = objectMapper.readValue(canonicalObjectId, new TypeReference<>() { });
            return new ActorIdentity(value.get("issuer"), value.get("actorRef"));
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IllegalStateException("Canonical Chat actor mapping is invalid.", exception);
        }
    }

    private void recordChange(
            String tenantId,
            ConversationId conversationId,
            String kind,
            String canonicalObjectId,
            Instant occurredAt) {
        jdbc.update("insert into weave_chat_changes "
                        + "(tenant_id, conversation_id, change_kind, canonical_object_id, occurred_at_utc) "
                        + "values (?, ?, ?, ?, ?)",
                tenantId, conversationId.value(), kind, canonicalObjectId, utc(occurredAt));
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
        } catch (JsonProcessingException exception) {
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

    private long count(String sql, Object... arguments) {
        Long count = jdbc.queryForObject(sql, Long.class, arguments);
        return count == null ? 0 : count;
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

    private OffsetDateTime utc(Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class).toInstant();
    }

    private Instant nullableInstant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? Instant.EPOCH : value.toInstant();
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
