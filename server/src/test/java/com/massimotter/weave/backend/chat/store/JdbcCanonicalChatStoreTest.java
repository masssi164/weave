package com.massimotter.weave.backend.chat.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.chat.domain.ChatAccessDeniedException;
import com.massimotter.weave.backend.chat.domain.ChatActorRef;
import com.massimotter.weave.backend.chat.domain.ChatCallbackRetryRequiredException;
import com.massimotter.weave.backend.chat.domain.ChatEncryptedEnvelope;
import com.massimotter.weave.backend.chat.domain.ChatEventContent;
import com.massimotter.weave.backend.chat.domain.ChatEncryptionState;
import com.massimotter.weave.backend.chat.domain.ChatRequestContext;
import com.massimotter.weave.backend.chat.domain.ChatResolvedIdentity;
import com.massimotter.weave.backend.chat.domain.ChatTransactionId;
import com.massimotter.weave.backend.chat.port.CanonicalChatStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcCanonicalChatStoreTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-07-15T08:00:00Z"), ZoneOffset.UTC);
    private static final String PROVIDER = "matrix-synapse";

    @Test
    void invitedMembershipIsDistinctAndOutsiderCannotJoinOrRead() {
        JdbcCanonicalChatStore store = store(dataSource());
        ChatRequestContext author = context("author");
        ChatRequestContext collaborator = context("collaborator");
        ChatRequestContext outsider = context("outsider");

        CanonicalChatStore.PreparedConversation prepared = store.prepareConversation(
                author,
                new ChatTransactionId("create-1"),
                "Encrypted collaboration",
                "channel",
                List.of(ChatResolvedIdentity.from(collaborator)),
                PROVIDER,
                "#_weave_opaque:matrix.internal",
                ChatEncryptionState.unencrypted());
        store.acknowledgeConversation(
                author, prepared, PROVIDER, "!opaque-room:matrix.internal", "room-version-1");

        assertThat(store.joinedConversations(author).conversations()).hasSize(1);
        assertThat(store.joinedConversations(collaborator).conversations()).isEmpty();
        assertThat(store.conversation(author, prepared.conversationId()).memberships())
                .filteredOn(membership -> membership.memberRef().equals(collaborator.actorRef().value()))
                .singleElement()
                .satisfies(membership -> assertThat(membership.state()).isEqualTo("invited"));
        assertThatThrownBy(() -> store.prepareMembership(outsider, prepared.conversationId(), "joined"))
                .isInstanceOf(ChatAccessDeniedException.class);
        assertThatThrownBy(() -> store.conversation(outsider, prepared.conversationId()))
                .isInstanceOf(ChatAccessDeniedException.class);

        CanonicalChatStore.PreparedMembership join = store.prepareMembership(
                collaborator, prepared.conversationId(), "joined");
        store.acknowledgeMembership(collaborator, join, PROVIDER, "membership-version-1");

        assertThat(store.joinedConversations(collaborator).conversations()).hasSize(1);
        assertThat(store.conversation(collaborator, prepared.conversationId()).memberships())
                .filteredOn(membership -> membership.memberRef().equals(collaborator.actorRef().value()))
                .singleElement()
                .satisfies(membership -> assertThat(membership.state()).isEqualTo("joined"));
    }

    @Test
    void pendingEncryptedWriteIsInvisibleThenCommitsExactlyOnceAcrossRestartAndCallbackReplay() {
        DriverManagerDataSource dataSource = dataSource();
        JdbcCanonicalChatStore store = store(dataSource);
        ChatRequestContext author = context("author");
        CanonicalChatStore.PreparedConversation room = store.prepareConversation(
                author,
                new ChatTransactionId("create-2"),
                "Durable encrypted room",
                "channel",
                List.of(),
                PROVIDER,
                "#_weave_durable:matrix.internal",
                ChatEncryptionState.unencrypted());
        store.acknowledgeConversation(author, room, PROVIDER, "!durable-room:matrix.internal", "room-v1");
        CanonicalChatStore.PreparedEncryption encryption = store.prepareEncryption(
                author, room.conversationId(), ChatEncryptedEnvelope.MEGOLM_V1);
        store.acknowledgeEncryption(
                author, encryption, PROVIDER, "$encryption:matrix.internal", "encryption-v1");
        acknowledgeActor(store, author, "@_weave_sender:matrix.internal");

        Map<String, Object> envelope = Map.of(
                "algorithm", ChatEncryptedEnvelope.MEGOLM_V1,
                "ciphertext", "opaque-ciphertext",
                "sender_key", "curve25519:opaque",
                "session_id", "opaque-session",
                "device_id", "OPAQUEDEVICE");
        CanonicalChatStore.PreparedEvent pending = store.prepareEvent(
                author,
                room.conversationId(),
                new ChatTransactionId("send-1"),
                ChatEventContent.encrypted(envelope));

        assertThat(store.timelineEvents(author, room.conversationId(), null, 100).events()).isEmpty();
        store.failOperation(author.tenantId(), pending.operationId(), "chat-provider-unavailable", FIXED.instant().plusSeconds(60));
        assertThat(store.activeRetryWindow(author.tenantId(), pending.operationId(), FIXED.instant()))
                .hasValueSatisfying(window -> {
                    assertThat(window.supportSafeCode()).isEqualTo("chat-provider-unavailable");
                    assertThat(window.retryAt()).isEqualTo(FIXED.instant().plusSeconds(60));
                });
        CanonicalChatStore.PreparedEvent retry = store.prepareEvent(
                author,
                room.conversationId(),
                new ChatTransactionId("send-1"),
                ChatEventContent.encrypted(envelope));
        assertThat(retry.operationId()).isEqualTo(pending.operationId());
        assertThat(retry.providerTransactionId()).isEqualTo(pending.providerTransactionId());
        store.acknowledgeEvent(author, retry, PROVIDER, "$opaque-event:matrix.internal", "event-v1");

        JdbcCanonicalChatStore restarted = store(dataSource);
        assertThat(restarted.timelineEvents(author, room.conversationId(), null, 100).events())
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.content().body()).isNull();
                    assertThat(event.content().encryptedEnvelope().content()).isEqualTo(envelope);
                    assertThat(event.deliveryState()).isEqualTo("committed");
                });
        CanonicalChatStore.PreparedEvent replay = restarted.prepareEvent(
                author,
                room.conversationId(),
                new ChatTransactionId("send-1"),
                ChatEventContent.encrypted(envelope));
        assertThat(replay.committed()).isTrue();

        assertThat(restarted.beginCallback(PROVIDER, "hs-txn-1", "a".repeat(64), 1))
                .isEqualTo(CanonicalChatStore.CallbackStart.NEW);
        CanonicalChatStore.CallbackEventResult callback = restarted.recordCallbackEvent(
                PROVIDER,
                new CanonicalChatStore.ProviderCallbackEvent(
                        "hs-txn-1",
                        pending.providerTransactionId(),
                        "$opaque-event:matrix.internal",
                        "!durable-room:matrix.internal",
                        "@_weave_sender:matrix.internal",
                        "m.room.encrypted",
                        envelope,
                        "event-v1"));
        restarted.completeCallback(PROVIDER, "hs-txn-1", callback.state().contains("duplicate") ? 1 : 0);
        assertThat(restarted.beginCallback(PROVIDER, "hs-txn-1", "a".repeat(64), 1))
                .isEqualTo(CanonicalChatStore.CallbackStart.DUPLICATE);

        CanonicalChatStore.EvidenceSnapshot evidence = restarted.evidence(
                author.tenantId(), room.conversationId(), PROVIDER);
        assertThat(evidence.persistencePosture()).isEqualTo("durable-relational-flyway");
        assertThat(evidence.canonicalCommittedEventCount()).isEqualTo(1);
        assertThat(evidence.canonicalEncryptedEventCount()).isEqualTo(1);
        assertThat(evidence.canonicalPlaintextEventCount()).isZero();
        assertThat(evidence.committedOperationCount()).isGreaterThanOrEqualTo(3);
        assertThat(evidence.callbackTransactionCount()).isEqualTo(1);
        assertThat(evidence.callbackDuplicateCount()).isEqualTo(1);
        assertThat(evidence.quarantineCount()).isZero();
        assertThat(evidence.degradedMappingCount()).isZero();
        assertThat(callback.state()).isEqualTo("acknowledged-echo");
        assertThat(restarted.timelineEvents(author, room.conversationId(), null, 100).events()).hasSize(1);
        assertThat(restarted.acknowledgedProviderEventRefs(
                author.tenantId(), room.conversationId(), PROVIDER))
                .containsExactly("$opaque-event:matrix.internal");
    }

    @Test
    void correlatedRedactionCallbackCommitsOnceWithoutDegradingTheRoom() {
        JdbcCanonicalChatStore store = store(dataSource());
        ChatRequestContext author = context("redaction-author");
        CanonicalChatStore.PreparedConversation room = store.prepareConversation(
                author,
                new ChatTransactionId("create-redaction-room"),
                "Redaction callback",
                "channel",
                List.of(),
                PROVIDER,
                "#_weave_redaction:matrix.internal",
                ChatEncryptionState.unencrypted());
        store.acknowledgeConversation(
                author, room, PROVIDER, "!redaction-room:matrix.internal", "room-v1");
        acknowledgeActor(store, author, "@_weave_redactor:matrix.internal");
        CanonicalChatStore.PreparedEvent message = store.prepareEvent(
                author,
                room.conversationId(),
                new ChatTransactionId("send-before-redaction"),
                ChatEventContent.text("remove me"));
        store.acknowledgeEvent(
                author, message, PROVIDER, "$target-event:matrix.internal", "event-v1");
        CanonicalChatStore.PreparedRedaction redaction = store.prepareRedaction(
                author,
                room.conversationId(),
                new ChatTransactionId("redact-once"),
                message.event().eventId());

        CanonicalChatStore.CallbackEventResult callback = store.recordCallbackEvent(
                PROVIDER,
                new CanonicalChatStore.ProviderCallbackEvent(
                        "hs-redaction-1",
                        null,
                        "$redaction-event:matrix.internal",
                        "!redaction-room:matrix.internal",
                        "@_weave_redactor:matrix.internal",
                        "m.room.redaction",
                        null,
                        "$target-event:matrix.internal",
                        Map.of(),
                        "redaction-v1"));

        assertThat(callback.state()).isEqualTo("acknowledged-redaction-echo");
        assertThat(store.timelineEvents(author, room.conversationId(), null, 100).events())
                .singleElement()
                .satisfies(event -> assertThat(event.redacted()).isTrue());

        // The normal southbound HTTP response may arrive after the Application Service echo.
        var receipt = store.acknowledgeRedaction(
                author,
                redaction,
                PROVIDER,
                "$redaction-event:matrix.internal",
                "redaction-v1");

        assertThat(receipt.redactionEventId()).isNotEqualTo(receipt.targetEventId());
        assertThat(receipt.targetEventId()).isEqualTo(message.event().eventId());
        assertThat(store.mapping(
                author.tenantId(), PROVIDER, "redaction", receipt.redactionEventId()))
                .hasValueSatisfying(mapping ->
                        assertThat(mapping.providerRef()).isEqualTo("$redaction-event:matrix.internal"));

        assertThat(store.changes(author, null, 100).changes())
                .filteredOn(change -> "event.redacted".equals(change.kind()))
                .hasSize(1);
        CanonicalChatStore.EvidenceSnapshot evidence = store.evidence(
                author.tenantId(), room.conversationId(), PROVIDER);
        assertThat(evidence.quarantineCount()).isZero();
        assertThat(evidence.degradedMappingCount()).isZero();
        assertThat(evidence.pendingOperationCount()).isZero();
    }

    @Test
    void callbackWithoutUnsignedTransactionReconcilesPendingSendBeforeHttpAck() {
        JdbcCanonicalChatStore store = store(dataSource());
        ChatRequestContext author = context("callback-before-ack");
        CanonicalChatStore.PreparedConversation room = store.prepareConversation(
                author,
                new ChatTransactionId("create-before-ack-room"),
                "Callback ordering",
                "channel",
                List.of(),
                PROVIDER,
                "#_weave_before_ack:matrix.internal",
                ChatEncryptionState.unencrypted());
        store.acknowledgeConversation(
                author, room, PROVIDER, "!before-ack-room:matrix.internal", "room-v1");
        acknowledgeActor(store, author, "@_weave_before_ack:matrix.internal");
        CanonicalChatStore.PreparedEvent pending = store.prepareEvent(
                author,
                room.conversationId(),
                new ChatTransactionId("send-before-http-ack"),
                ChatEventContent.text("committed once"));

        CanonicalChatStore.CallbackEventResult callback = store.recordCallbackEvent(
                PROVIDER,
                new CanonicalChatStore.ProviderCallbackEvent(
                        "hs-before-ack",
                        null,
                        "$before-ack-event:matrix.internal",
                        "!before-ack-room:matrix.internal",
                        "@_weave_before_ack:matrix.internal",
                        "m.room.message",
                        Map.of("msgtype", "m.text", "body", "committed once"),
                        "event-v1"));

        assertThat(callback.state()).isEqualTo("acknowledged-echo");
        store.acknowledgeEvent(
                author, pending, PROVIDER, "$before-ack-event:matrix.internal", "event-v1");
        assertThat(store.timelineEvents(author, room.conversationId(), null, 100).events()).hasSize(1);
        CanonicalChatStore.EvidenceSnapshot evidence = store.evidence(
                author.tenantId(), room.conversationId(), PROVIDER);
        assertThat(evidence.canonicalCommittedEventCount()).isEqualTo(1);
        assertThat(evidence.quarantineCount()).isZero();
        assertThat(evidence.degradedMappingCount()).isZero();
    }

    @Test
    void redactionCallbackAfterHttpAckWithoutUnsignedTransactionIsDeduplicated() {
        JdbcCanonicalChatStore store = store(dataSource());
        ChatRequestContext author = context("redaction-ack-first");
        CanonicalChatStore.PreparedConversation room = store.prepareConversation(
                author,
                new ChatTransactionId("create-redaction-ack-first"),
                "Ack-first redaction",
                "channel",
                List.of(),
                PROVIDER,
                "#_weave_redaction_ack_first:matrix.internal",
                ChatEncryptionState.unencrypted());
        store.acknowledgeConversation(
                author, room, PROVIDER, "!redaction-ack-first:matrix.internal", "room-v1");
        acknowledgeActor(store, author, "@_weave_redaction_ack_first:matrix.internal");
        CanonicalChatStore.PreparedEvent message = store.prepareEvent(
                author,
                room.conversationId(),
                new ChatTransactionId("send-redaction-ack-first"),
                ChatEventContent.text("redact after ack"));
        store.acknowledgeEvent(
                author, message, PROVIDER, "$redaction-target-ack-first:matrix.internal", "event-v1");
        CanonicalChatStore.PreparedRedaction redaction = store.prepareRedaction(
                author,
                room.conversationId(),
                new ChatTransactionId("redaction-ack-first"),
                message.event().eventId());
        var receipt = store.acknowledgeRedaction(
                author, redaction, PROVIDER, "$redaction-ack-first:matrix.internal", "redaction-v1");
        assertThat(receipt.redactionEventId()).isNotEqualTo(receipt.targetEventId());

        CanonicalChatStore.CallbackEventResult callback = store.recordCallbackEvent(
                PROVIDER,
                new CanonicalChatStore.ProviderCallbackEvent(
                        "hs-redaction-ack-first",
                        null,
                        "$redaction-ack-first:matrix.internal",
                        "!redaction-ack-first:matrix.internal",
                        "@_weave_redaction_ack_first:matrix.internal",
                        "m.room.redaction",
                        null,
                        "$redaction-target-ack-first:matrix.internal",
                        Map.of(),
                        "redaction-v1"));

        assertThat(callback.state()).isEqualTo("acknowledged-redaction-echo");
        assertThat(store.changes(author, null, 100).changes())
                .filteredOn(change -> "event.redacted".equals(change.kind()))
                .hasSize(1);
        CanonicalChatStore.EvidenceSnapshot evidence = store.evidence(
                author.tenantId(), room.conversationId(), PROVIDER);
        assertThat(evidence.quarantineCount()).isZero();
        assertThat(evidence.degradedMappingCount()).isZero();
    }

    @Test
    void newRoomStateCallbackRacingCreateAckRequestsRetryWithoutQuarantine() {
        JdbcCanonicalChatStore store = store(dataSource());
        ChatRequestContext author = context("create-race");
        acknowledgeActor(store, author, "@_weave_create_race:matrix.internal");
        CanonicalChatStore.PreparedConversation room = store.prepareConversation(
                author,
                new ChatTransactionId("create-race-room"),
                "Create race",
                "channel",
                List.of(),
                PROVIDER,
                "#_weave_create_race:matrix.internal",
                ChatEncryptionState.unencrypted());
        CanonicalChatStore.ProviderCallbackEvent callback = new CanonicalChatStore.ProviderCallbackEvent(
                "hs-create-race",
                room.providerTransactionId(),
                "$create-state:matrix.internal",
                "!create-race-room:matrix.internal",
                "@_weave_create_race:matrix.internal",
                "m.room.member",
                "",
                null,
                Map.of("membership", "join"),
                "state-v1");

        assertThatThrownBy(() -> store.recordCallbackEvent(PROVIDER, callback))
                .isInstanceOf(ChatCallbackRetryRequiredException.class);
        assertThat(store.evidence(author.tenantId(), room.conversationId(), PROVIDER).quarantineCount())
                .isZero();

        store.acknowledgeConversation(
                author, room, PROVIDER, "!create-race-room:matrix.internal", "room-v1");
        assertThat(store.recordCallbackEvent(PROVIDER, callback).state()).isEqualTo("ignored");
        CanonicalChatStore.EvidenceSnapshot evidence = store.evidence(
                author.tenantId(), room.conversationId(), PROVIDER);
        assertThat(evidence.quarantineCount()).isZero();
        assertThat(evidence.degradedMappingCount()).isZero();
    }

    private ChatRequestContext context(String subject) {
        return new ChatRequestContext("tenant-a", "https://auth.example/realms/a", new ChatActorRef("user:" + subject));
    }

    private JdbcCanonicalChatStore store(DriverManagerDataSource dataSource) {
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        return new JdbcCanonicalChatStore(
                new JdbcTemplate(dataSource),
                new ObjectMapper().findAndRegisterModules(),
                FIXED);
    }

    private void acknowledgeActor(
            JdbcCanonicalChatStore store,
            ChatRequestContext context,
            String providerActorRef) {
        String canonicalActor = "{\"issuer\":\"" + context.identityIssuer()
                + "\",\"actorRef\":\"" + context.actorRef().value() + "\"}";
        store.reserveMapping(
                context.tenantId(), PROVIDER, "actor", canonicalActor, providerActorRef, null);
        store.acknowledgeMapping(
                context.tenantId(), PROVIDER, "actor", canonicalActor, providerActorRef, "actor-v1");
    }

    private DriverManagerDataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_UPPER=true;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return dataSource;
    }
}
