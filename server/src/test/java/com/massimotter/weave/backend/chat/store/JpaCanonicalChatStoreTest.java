package com.massimotter.weave.backend.chat.store;

import tools.jackson.databind.ObjectMapper;
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
import com.massimotter.weave.backend.chat.port.ChatSouthboundProvider;
import com.massimotter.weave.backend.chat.provider.synapse.MatrixSynapseChatSouthboundAdapter;
import com.massimotter.weave.backend.chat.provider.synapse.SynapseBackedCanonicalChatAdapter;
import com.massimotter.weave.backend.config.ChatRuntimeProperties;
import com.massimotter.weave.backend.testing.JpaTestDatabase;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JpaCanonicalChatStoreTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-07-15T08:00:00Z"), ZoneOffset.UTC);
    private static final String PROVIDER = "matrix-synapse";

    @Test
    void encryptedConversationTraversesTheCanonicalStoreAndSynapseAdapter() {
        DriverManagerDataSource dataSource = dataSource();
        JpaCanonicalChatStore store = store(dataSource);
        MatrixSynapseChatSouthboundAdapter provider = mock(MatrixSynapseChatSouthboundAdapter.class);
        when(provider.providerKey()).thenReturn(PROVIDER);
        when(provider.ensureVirtualUser(anyString())).thenAnswer(invocation -> {
            String providerActor = invocation.getArgument(0, String.class);
            return new ChatSouthboundProvider.ProviderAck(providerActor, "registered");
        });
        when(provider.createRoom(
                anyString(), anyString(), anyString(), anyList(), anyString()))
                .thenReturn(new ChatSouthboundProvider.ProviderAck(
                        "!isolated-room:matrix.internal",
                        "!isolated-room:matrix.internal"));
        SynapseBackedCanonicalChatAdapter adapter = new SynapseBackedCanonicalChatAdapter(
                store,
                provider,
                new ChatRuntimeProperties.Matrix(
                        "http://synapse:8008",
                        "matrix.internal",
                        "weave-chat-synapse",
                        "_weave_",
                        "",
                        "",
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(10),
                        Duration.ofSeconds(60),
                        1_048_576,
                        100),
                tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build(),
                FIXED);
        ChatRequestContext author = new ChatRequestContext(
                "tenant-test-app",
                "workspace-default",
                "https://auth.weave.test/realms/weave",
                new ChatActorRef("user:11111111-1111-1111-1111-111111111111"),
                "11111111-1111-1111-1111-111111111111");
        ChatRequestContext collaborator = new ChatRequestContext(
                author.tenantId(),
                author.contextId(),
                author.identityIssuer(),
                new ChatActorRef("user:22222222-2222-2222-2222-222222222222"),
                "22222222-2222-2222-2222-222222222222");

        var conversation = adapter.createConversation(
                author,
                new ChatTransactionId("create-" + "a".repeat(64)),
                "Weave isolated collaboration",
                "channel",
                List.of(ChatResolvedIdentity.from(collaborator)),
                ChatEncryptionState.matrixMegolm());

        assertThat(conversation.encryptionState().encrypted()).isTrue();
        assertThat(conversation.memberships()).hasSize(2);
        assertThat(store.evidence(
                author.tenantId(), new com.massimotter.weave.backend.chat.domain.ConversationId(
                        conversation.conversationId()), PROVIDER)
                .pendingOperationCount()).isZero();
    }

    @Test
    void redactionPresentationContentIsNarrowlyBounded() {
        assertThat(JpaCanonicalChatStore.supportedRedactionPresentationContent(Map.of())).isTrue();
        assertThat(JpaCanonicalChatStore.supportedRedactionPresentationContent(
                Map.of("reason", "isolated-e2e-cleanup"))).isTrue();
        assertThat(JpaCanonicalChatStore.supportedRedactionPresentationContent(
                Map.of("reason", "line\nbreak"))).isFalse();
        assertThat(JpaCanonicalChatStore.supportedRedactionPresentationContent(
                Map.of("unsupported", "private-value"))).isFalse();
    }

    @Test
    void invitedMembershipIsDistinctAndOutsiderCannotJoinOrRead() {
        JpaCanonicalChatStore store = store(dataSource());
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
    void joinedConversationFetchPlanUsesTwoQueriesRegardlessOfAggregateCount() {
        DriverManagerDataSource dataSource = dataSource();
        JpaCanonicalChatStore store = store(dataSource);
        ChatRequestContext author = context("fetch-plan-author");

        for (int index = 0; index < 5; index++) {
            CanonicalChatStore.PreparedConversation prepared =
                    store.prepareConversation(
                            author,
                            new ChatTransactionId("fetch-plan-" + index),
                            "Fetch plan " + index,
                            "channel",
                            List.of(ChatResolvedIdentity.from(
                                    context("fetch-plan-invite-" + index))),
                            PROVIDER,
                            "#_weave_fetch_plan_" + index + ":matrix.internal",
                            ChatEncryptionState.unencrypted());
            store.acknowledgeConversation(
                    author,
                    prepared,
                    PROVIDER,
                    "!fetch-plan-" + index + ":matrix.internal",
                    "room-v1");
        }

        var statistics = JpaTestDatabase.statistics(dataSource);
        statistics.clear();

        assertThat(store.joinedConversations(author).conversations())
                .hasSize(5)
                .allSatisfy(conversation ->
                        assertThat(conversation.memberships()).hasSize(2));
        assertThat(statistics.getPrepareStatementCount())
                .as("one aggregate query plus one batched membership query")
                .isEqualTo(2);
    }

    @Test
    void queryInputsRemainBoundValuesForQuotesCommentsWildcardsAndUnicode() {
        JpaCanonicalChatStore store = store(dataSource());
        ChatRequestContext author = context("bound-value-author");
        ChatRequestContext outsider = new ChatRequestContext(
                author.tenantId(),
                author.identityIssuer(),
                new ChatActorRef("user:outsider' OR '1'='1' -- %_ Ω"));
        String title = "Quarterly ' -- %_ Ω desc nulls first, (select 1)";
        String providerKey = "matrix' /* %_ Ω */";
        String providerRoomRef = "!room' -- %_ Ω:matrix.internal";

        CanonicalChatStore.PreparedConversation prepared =
                store.prepareConversation(
                        author,
                        new ChatTransactionId("bound-value-create"),
                        title,
                        "channel",
                        List.of(),
                        providerKey,
                        "#_weave_bound_value:matrix.internal",
                        ChatEncryptionState.unencrypted());
        store.acknowledgeConversation(
                author,
                prepared,
                providerKey,
                providerRoomRef,
                "room-v1");

        assertThat(store.joinedConversations(author).conversations())
                .singleElement()
                .satisfies(conversation ->
                        assertThat(conversation.title()).isEqualTo(title));
        assertThat(store.joinedConversations(outsider).conversations()).isEmpty();
        assertThat(store.mapping(
                        author.tenantId(),
                        providerKey,
                        "conversation",
                        prepared.conversationId().value()))
                .hasValueSatisfying(mapping ->
                        assertThat(mapping.providerRef()).isEqualTo(providerRoomRef));
        assertThat(store.mapping(
                        author.tenantId(),
                        PROVIDER,
                        "conversation",
                        prepared.conversationId().value()))
                .isEmpty();
    }

    @Test
    void pendingEncryptedWriteIsInvisibleThenCommitsExactlyOnceAcrossRestartAndCallbackReplay() {
        DriverManagerDataSource dataSource = dataSource();
        JpaCanonicalChatStore store = store(dataSource);
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
                "session_id", "opaque-session");
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

        JpaCanonicalChatStore restarted = store(dataSource);
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

        String callbackDigest = "a".repeat(64);
        assertThat(restarted.beginCallback(PROVIDER, "hs-txn-1", callbackDigest, 1))
                .isEqualTo(CanonicalChatStore.CallbackStart.NEW);
        assertThat(restarted.beginCallback(PROVIDER, "hs-txn-1", callbackDigest, 1))
                .as("a semantically identical homeserver retry resumes by transaction ID")
                .isEqualTo(CanonicalChatStore.CallbackStart.RESUME);
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
        assertThat(restarted.beginCallback(PROVIDER, "hs-txn-1", callbackDigest, 1))
                .as("a completed homeserver transaction is acknowledged idempotently by transaction ID")
                .isEqualTo(CanonicalChatStore.CallbackStart.DUPLICATE);

        CanonicalChatStore.EvidenceSnapshot evidence = restarted.evidence(
                author.tenantId(), room.conversationId(), PROVIDER);
        assertThat(evidence.persistencePosture()).isEqualTo("durable-relational-jpa-code-first");
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

        CanonicalChatStore.ProviderCallbackEvent redactedProjection =
                new CanonicalChatStore.ProviderCallbackEvent(
                        "hs-txn-redacted",
                        null,
                        "$opaque-event:matrix.internal",
                        "!durable-room:matrix.internal",
                        "@_weave_sender:matrix.internal",
                        "m.room.encrypted",
                        null,
                        null,
                        Map.of(),
                        "event-v2",
                        true);
        assertThat(restarted.recordCallbackEvent(PROVIDER, redactedProjection).state())
                .isEqualTo("acknowledged-redacted-projection");
        assertThat(restarted.recordCallbackEvent(PROVIDER, redactedProjection).state())
                .isEqualTo("deduplicated-redacted-projection");
        assertThat(restarted.timelineEvents(author, room.conversationId(), null, 100).events())
                .singleElement()
                .satisfies(event -> assertThat(event.redacted()).isTrue());
        assertThat(restarted.evidence(author.tenantId(), room.conversationId(), PROVIDER).degradedMappingCount())
                .isZero();
    }

    @Test
    void correlatedRedactionCallbackCommitsOnceWithoutDegradingTheRoom() {
        JpaCanonicalChatStore store = store(dataSource());
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
                        Map.of("reason", "isolated-e2e-cleanup"),
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
        JpaCanonicalChatStore store = store(dataSource());
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
        JpaCanonicalChatStore store = store(dataSource());
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
    void newRoomStateCallbacksRetryUntilAckAndIgnoreTheProviderCanonicalAlias() {
        JpaCanonicalChatStore store = store(dataSource());
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
        CanonicalChatStore.ProviderCallbackEvent canonicalAliasCallback =
                new CanonicalChatStore.ProviderCallbackEvent(
                        "hs-create-race-alias",
                        room.providerTransactionId(),
                        "$create-alias:matrix.internal",
                        "!create-race-room:matrix.internal",
                        "@_weave_create_race:matrix.internal",
                        "m.room.canonical_alias",
                        "",
                        null,
                        Map.of("alias", "#_weave_create_race:matrix.internal"),
                        "state-v2");
        assertThat(store.recordCallbackEvent(PROVIDER, canonicalAliasCallback).state()).isEqualTo("ignored");

        CanonicalChatStore.PreparedMembership leave = store.prepareMembership(
                author, room.conversationId(), "left");
        store.acknowledgeMembership(author, leave, PROVIDER, "membership-left-v1");
        CanonicalChatStore.ProviderCallbackEvent leaveCallback =
                new CanonicalChatStore.ProviderCallbackEvent(
                        "hs-create-race-leave",
                        leave.providerTransactionId(),
                        "$create-leave:matrix.internal",
                        "!create-race-room:matrix.internal",
                        "@_weave_create_race:matrix.internal",
                        "m.room.member",
                        "@_weave_create_race:matrix.internal",
                        null,
                        Map.of("membership", "leave"),
                        "state-v3");
        assertThat(store.recordCallbackEvent(PROVIDER, leaveCallback).state()).isEqualTo("ignored");
        CanonicalChatStore.EvidenceSnapshot evidence = store.evidence(
                author.tenantId(), room.conversationId(), PROVIDER);
        assertThat(evidence.quarantineCount()).isZero();
        assertThat(evidence.degradedMappingCount()).isZero();
    }

    private ChatRequestContext context(String subject) {
        return new ChatRequestContext("tenant-a", "https://auth.example/realms/a", new ChatActorRef("user:" + subject));
    }

    private JpaCanonicalChatStore store(DriverManagerDataSource dataSource) {
        JpaTestDatabase.initializeSchema(dataSource);
        return new JpaCanonicalChatStore(
                CanonicalChatJpaTestFactory.authority(dataSource),
                tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build(),
                FIXED);
    }

    private void acknowledgeActor(
            JpaCanonicalChatStore store,
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
        return com.massimotter.weave.backend.testing.JpaTestDatabase
                .dataSource("canonical-chat");
    }
}
