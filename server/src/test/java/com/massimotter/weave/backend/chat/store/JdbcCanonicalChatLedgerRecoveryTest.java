package com.massimotter.weave.backend.chat.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.chat.domain.ChatActorRef;
import com.massimotter.weave.backend.chat.domain.ChatEventContent;
import com.massimotter.weave.backend.chat.domain.ChatEncryptionState;
import com.massimotter.weave.backend.chat.domain.ChatProviderUnavailableException;
import com.massimotter.weave.backend.chat.domain.ChatRequestContext;
import com.massimotter.weave.backend.chat.domain.ChatTransactionId;
import com.massimotter.weave.backend.chat.domain.ConversationId;
import com.massimotter.weave.backend.chat.port.CanonicalChatStore;
import com.massimotter.weave.backend.chat.provider.synapse.MatrixSynapseCompatibilityProfile;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcCanonicalChatLedgerRecoveryTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-07-18T09:00:00Z"), ZoneOffset.UTC);
    private static final String PROVIDER = "matrix-synapse";

    @Test
    void badFirstSiblingDegradesOnlyItsConversationAndLaterValidSiblingCommitsExactlyOnce() {
        DriverManagerDataSource dataSource = dataSource();
        JdbcCanonicalChatStore store = store(dataSource, MatrixSynapseCompatibilityProfile.pinned());
        ChatRequestContext author = context("tenant-a", "author");
        acknowledgeActor(store, author, "@_weave_author:matrix.internal");
        CanonicalChatStore.PreparedConversation affected = room(
                store, author, "affected", "!affected:matrix.internal");
        CanonicalChatStore.PreparedConversation healthy = room(
                store, author, "healthy", "!healthy:matrix.internal");
        String siblingsDigest = "d".repeat(64);
        assertThat(store.beginCallback(PROVIDER, "hs-siblings", siblingsDigest, 2))
                .isEqualTo(CanonicalChatStore.CallbackStart.NEW);

        CanonicalChatStore.CallbackEventResult unknown = store.recordCallbackEvent(
                PROVIDER,
                stateEvent(
                        "hs-siblings",
                        "$unknown-state:matrix.internal",
                        "!affected:matrix.internal",
                        "@_weave_author:matrix.internal",
                        "org.example.future_state"));
        CanonicalChatStore.ProviderCallbackEvent valid = new CanonicalChatStore.ProviderCallbackEvent(
                "hs-siblings",
                null,
                "$valid-after-bad:matrix.internal",
                "!affected:matrix.internal",
                "@_weave_author:matrix.internal",
                "m.room.message",
                Map.of("msgtype", "m.text", "body", "valid sibling"),
                "event-v1");

        assertThat(unknown.state()).isEqualTo("quarantined");
        assertThat(store.recordCallbackEvent(PROVIDER, valid).state()).isEqualTo("accepted");
        store.completeCallback(PROVIDER, "hs-siblings", 0);
        assertThat(store.beginCallback(PROVIDER, "hs-siblings", siblingsDigest, 2))
                .isEqualTo(CanonicalChatStore.CallbackStart.DUPLICATE);
        assertThat(store.recordCallbackEvent(PROVIDER, new CanonicalChatStore.ProviderCallbackEvent(
                "hs-siblings-replayed-elsewhere",
                valid.providerTransactionId(),
                valid.providerEventRef(),
                valid.providerRoomRef(),
                valid.providerSenderRef(),
                valid.eventType(),
                valid.stateKey(),
                valid.providerRedactsRef(),
                valid.content(),
                valid.providerSourceVersion())).state()).isEqualTo("deduplicated");

        CanonicalChatStore.EvidenceSnapshot affectedEvidence = store.evidence(
                author.tenantId(), affected.conversationId(), PROVIDER);
        CanonicalChatStore.EvidenceSnapshot healthyEvidence = store.evidence(
                author.tenantId(), healthy.conversationId(), PROVIDER);
        assertThat(affectedEvidence.canonicalCommittedEventCount()).isEqualTo(1);
        assertThat(affectedEvidence.quarantineCount()).isEqualTo(1);
        assertThat(affectedEvidence.degradedMappingCount()).isEqualTo(1);
        assertThat(healthyEvidence.quarantineCount()).isZero();
        assertThat(healthyEvidence.degradedMappingCount()).isZero();
        assertThat(store.joinedConversations(author).conversations())
                .extracting(conversation -> conversation.conversationId())
                .containsExactly(healthy.conversationId().value());
        assertThatThrownBy(() -> store.conversation(author, affected.conversationId()))
                .isInstanceOf(ChatProviderUnavailableException.class)
                .satisfies(exception -> assertThat(((ChatProviderUnavailableException) exception).supportSafeCode())
                        .isEqualTo("chat-conversation-mapping-degraded"));
        assertThatThrownBy(() -> store.prepareEvent(
                author,
                affected.conversationId(),
                new ChatTransactionId("blocked-send"),
                ChatEventContent.text("must not send")))
                .isInstanceOf(ChatProviderUnavailableException.class);

        CanonicalChatStore.PreparedEvent healthyWrite = store.prepareEvent(
                author,
                healthy.conversationId(),
                new ChatTransactionId("healthy-send"),
                ChatEventContent.text("unrelated remains available"));
        store.acknowledgeEvent(
                author, healthyWrite, PROVIDER, "$healthy:matrix.internal", "event-v1");
        assertThat(store.timelineEvents(author, healthy.conversationId(), null, 100).events()).hasSize(1);

        ChatRequestContext otherTenant = context("tenant-b", "other");
        CanonicalChatStore.PreparedConversation otherRoom = room(
                store, otherTenant, "other-tenant", "!other-tenant:matrix.internal");
        assertThat(store.conversation(otherTenant, otherRoom.conversationId()).conversationId())
                .isEqualTo(otherRoom.conversationId().value());
    }

    @Test
    void validFirstSiblingAlsoCommitsBeforeUnknownStateIsContained() {
        DriverManagerDataSource dataSource = dataSource();
        JdbcCanonicalChatStore store = store(dataSource, MatrixSynapseCompatibilityProfile.pinned());
        ChatRequestContext author = context("tenant-a", "reverse-order-author");
        acknowledgeActor(store, author, "@_weave_reverse:matrix.internal");
        CanonicalChatStore.PreparedConversation room = room(
                store, author, "reverse-order", "!reverse-order:matrix.internal");
        String digest = "e".repeat(64);
        assertThat(store.beginCallback(PROVIDER, "hs-reverse-order", digest, 2))
                .isEqualTo(CanonicalChatStore.CallbackStart.NEW);

        CanonicalChatStore.CallbackEventResult valid = store.recordCallbackEvent(
                PROVIDER,
                new CanonicalChatStore.ProviderCallbackEvent(
                        "hs-reverse-order",
                        null,
                        "$valid-first:matrix.internal",
                        "!reverse-order:matrix.internal",
                        "@_weave_reverse:matrix.internal",
                        "m.room.message",
                        Map.of("msgtype", "m.text", "body", "valid first sibling"),
                        "event-v1"));
        CanonicalChatStore.CallbackEventResult unknown = store.recordCallbackEvent(
                PROVIDER,
                stateEvent(
                        "hs-reverse-order",
                        "$unknown-second:matrix.internal",
                        "!reverse-order:matrix.internal",
                        "@_weave_reverse:matrix.internal",
                        "org.example.future_state"));
        store.completeCallback(PROVIDER, "hs-reverse-order", 0);

        assertThat(valid.state()).isEqualTo("accepted");
        assertThat(unknown.state()).isEqualTo("quarantined");
        CanonicalChatStore.EvidenceSnapshot evidence = store.evidence(
                author.tenantId(), room.conversationId(), PROVIDER);
        assertThat(evidence.canonicalCommittedEventCount()).isEqualTo(1);
        assertThat(evidence.quarantineCount()).isEqualTo(1);
        assertThat(evidence.degradedMappingCount()).isEqualTo(1);
    }

    @Test
    void repeatedCallbackTransactionRejectsSemanticDigestOrCountDisagreement() {
        DriverManagerDataSource dataSource = dataSource();
        JdbcCanonicalChatStore store = store(dataSource, MatrixSynapseCompatibilityProfile.pinned());
        String digest = "a".repeat(64);

        assertThat(store.beginCallback(PROVIDER, "hs-semantic", digest, 2))
                .isEqualTo(CanonicalChatStore.CallbackStart.NEW);
        assertThat(store.beginCallback(PROVIDER, "hs-semantic", digest, 2))
                .isEqualTo(CanonicalChatStore.CallbackStart.RESUME);
        store.completeCallback(PROVIDER, "hs-semantic", 0);
        assertThat(store.beginCallback(PROVIDER, "hs-semantic", digest, 2))
                .isEqualTo(CanonicalChatStore.CallbackStart.DUPLICATE);
        assertThat(store.beginCallback(PROVIDER, "hs-semantic", "b".repeat(64), 2))
                .isEqualTo(CanonicalChatStore.CallbackStart.SEMANTIC_MISMATCH);
        assertThat(store.beginCallback(PROVIDER, "hs-semantic", digest, 3))
                .isEqualTo(CanonicalChatStore.CallbackStart.SEMANTIC_MISMATCH);

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject(
                "select transaction_state from weave_chat_appservice_transactions "
                        + "where provider_key = ? and homeserver_transaction_id = ?",
                String.class,
                PROVIDER,
                "hs-semantic")).isEqualTo("semantic-mismatch");
        assertThat(jdbc.queryForObject(
                "select semantic_mismatch_count from weave_chat_appservice_transactions "
                        + "where provider_key = ? and homeserver_transaction_id = ?",
                Integer.class,
                PROVIDER,
                "hs-semantic")).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "select semantic_mismatch_hash from weave_chat_appservice_transactions "
                        + "where provider_key = ? and homeserver_transaction_id = ?",
                String.class,
                PROVIDER,
                "hs-semantic")).matches("[0-9a-f]{64}");
        assertThat(store.systemicCallbackIntegrityFailureCount(PROVIDER)).isEqualTo(1);
        assertThat(store.evidence("tenant-a", new ConversationId("missing"), PROVIDER)
                .callbackSemanticMismatchCount())
                .isEqualTo(2);
    }

    @Test
    void concurrentFirstDeliveryConvergesOnOneTransactionIdentity() throws Exception {
        DriverManagerDataSource dataSource = dataSource();
        JdbcCanonicalChatStore store = store(dataSource, MatrixSynapseCompatibilityProfile.pinned());
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await();
                return store.beginCallback(PROVIDER, "hs-concurrent", "f".repeat(64), 1);
            });
            var second = executor.submit(() -> {
                start.await();
                return store.beginCallback(PROVIDER, "hs-concurrent", "f".repeat(64), 1);
            });

            start.countDown();
            assertThat(Set.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder(
                            CanonicalChatStore.CallbackStart.NEW,
                            CanonicalChatStore.CallbackStart.RESUME);
        }
        assertThat(new JdbcTemplate(dataSource).queryForObject(
                "select count(*) from weave_chat_appservice_transactions "
                        + "where provider_key = ? and homeserver_transaction_id = ?",
                Long.class,
                PROVIDER,
                "hs-concurrent")).isEqualTo(1L);
    }

    @Test
    void completedFalsePositiveQuarantinesReconcileUnderNextClassifierAndHealAfterAllResolve() {
        DriverManagerDataSource dataSource = dataSource();
        MatrixSynapseCompatibilityProfile firstProfile = MatrixSynapseCompatibilityProfile.pinned();
        JdbcCanonicalChatStore first = store(dataSource, firstProfile);
        ChatRequestContext author = context("tenant-a", "recovery-author");
        acknowledgeActor(first, author, "@_weave_recovery:matrix.internal");
        CanonicalChatStore.PreparedConversation room = room(
                first, author, "recovery", "!recovery:matrix.internal");
        String digest = "c".repeat(64);
        CanonicalChatStore.ProviderCallbackEvent futureState = stateEvent(
                "hs-recovery",
                "$future-state:matrix.internal",
                "!recovery:matrix.internal",
                "@_weave_recovery:matrix.internal",
                "org.example.future_state");
        CanonicalChatStore.ProviderCallbackEvent secondFutureState = stateEvent(
                "hs-recovery",
                "$future-state-two:matrix.internal",
                "!recovery:matrix.internal",
                "@_weave_recovery:matrix.internal",
                "org.example.future_state_two");

        assertThat(first.beginCallback(PROVIDER, "hs-recovery", digest, 2))
                .isEqualTo(CanonicalChatStore.CallbackStart.NEW);
        CanonicalChatStore.CallbackEventResult quarantine = first.recordCallbackEvent(PROVIDER, futureState);
        CanonicalChatStore.CallbackEventResult secondQuarantine =
                first.recordCallbackEvent(PROVIDER, secondFutureState);
        first.completeCallback(PROVIDER, "hs-recovery", 0);
        assertThat(first.beginCallback(PROVIDER, "hs-recovery", digest, 2))
                .isEqualTo(CanonicalChatStore.CallbackStart.DUPLICATE);
        assertThat(first.reconcileQuarantine(author.tenantId(), PROVIDER, quarantine.correlationHash()))
                .satisfies(result -> {
                    assertThat(result.lifecycleState()).isEqualTo("pending");
                    assertThat(result.outcomeCode()).isEqualTo("classifier-not-advanced");
                    assertThat(result.attemptCount()).isZero();
                });

        MatrixSynapseCompatibilityProfile nextProfile = firstProfile
                .withReclassifiedState("matrix-synapse-state-v2", "org.example.future_state")
                .withReclassifiedState("matrix-synapse-state-v2", "org.example.future_state_two");
        JdbcCanonicalChatStore next = storeWithoutMigration(dataSource, nextProfile);
        CanonicalChatStore.QuarantineReconciliationResult reconciled = next.reconcileQuarantine(
                author.tenantId(), PROVIDER, quarantine.correlationHash());

        assertThat(reconciled.lifecycleState()).isEqualTo("reconciled");
        assertThat(reconciled.outcomeCode()).isEqualTo("reconciliation-committed");
        assertThat(reconciled.attemptCount()).isEqualTo(1);
        assertThat(reconciled.conversationHealed()).isFalse();
        assertThat(next.evidence(author.tenantId(), room.conversationId(), PROVIDER))
                .satisfies(evidence -> {
                    assertThat(evidence.quarantineCount()).isEqualTo(1);
                    assertThat(evidence.degradedMappingCount()).isEqualTo(1);
                });
        assertThat(next.recordCallbackEvent(PROVIDER, secondFutureState).state()).isEqualTo("ignored");
        assertThat(next.reconcileQuarantine(
                author.tenantId(), PROVIDER, secondQuarantine.correlationHash()))
                .satisfies(result -> {
                    assertThat(result.lifecycleState()).isEqualTo("superseded");
                    assertThat(result.outcomeCode()).isEqualTo("reconciliation-superseded");
                    assertThat(result.attemptCount()).isEqualTo(1);
                    assertThat(result.conversationHealed()).isTrue();
                });
        assertThat(next.reconcileQuarantine(author.tenantId(), PROVIDER, quarantine.correlationHash()))
                .satisfies(result -> {
                    assertThat(result.lifecycleState()).isEqualTo("reconciled");
                    assertThat(result.outcomeCode()).isEqualTo("quarantine-already-reconciled");
                    assertThat(result.attemptCount()).isEqualTo(1);
                    assertThat(result.conversationHealed()).isFalse();
                });
        assertThat(next.mapping(author.tenantId(), PROVIDER, "conversation", room.conversationId().value()))
                .hasValueSatisfying(mapping -> assertThat(mapping.state()).isEqualTo("acknowledged"));
        assertThat(next.conversation(author, room.conversationId()).conversationId())
                .isEqualTo(room.conversationId().value());
        assertThat(next.beginCallback(PROVIDER, "hs-recovery", digest, 2))
                .isEqualTo(CanonicalChatStore.CallbackStart.DUPLICATE);

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject(
                "select count(*) from weave_chat_bridge_ledger where provider_key = ? "
                        + "and provider_event_ref = ? and ledger_state = 'ignored-supported-state'",
                Long.class,
                PROVIDER,
                futureState.providerEventRef())).isEqualTo(1L);
        assertThat(jdbc.queryForMap(
                "select lifecycle_state, reason_code, category_code, recoverable, classifier_version, attempt_count, "
                        + "private_normalized_event_json from weave_chat_quarantine "
                        + "where tenant_id = ? and correlation_hash = ?",
                author.tenantId(),
                quarantine.correlationHash()))
                .containsEntry("LIFECYCLE_STATE", "reconciled")
                .containsEntry("REASON_CODE", "provider-state-event-type-unsupported")
                .containsEntry("CATEGORY_CODE", "provider-compatibility")
                .containsEntry("RECOVERABLE", true)
                .containsEntry("CLASSIFIER_VERSION", "matrix-synapse-state-v2")
                .containsEntry("ATTEMPT_COUNT", 1)
                .satisfies(row -> assertThat(row.get("PRIVATE_NORMALIZED_EVENT_JSON")).isNotNull());
        CanonicalChatStore.EvidenceSnapshot evidence = next.evidence(
                author.tenantId(), room.conversationId(), PROVIDER);
        assertThat(evidence.quarantineCount()).isZero();
        assertThat(evidence.degradedMappingCount()).isZero();
        assertThat(evidence.toString())
                .doesNotContain(futureState.providerEventRef(), futureState.providerRoomRef(), "org.example.future_state");
    }

    @Test
    void boundedPendingBatchRunsOnlyAfterClassifierAdvanceAndDoesNotRepeatTerminalWork() {
        DriverManagerDataSource dataSource = dataSource();
        MatrixSynapseCompatibilityProfile firstProfile = MatrixSynapseCompatibilityProfile.pinned();
        JdbcCanonicalChatStore first = store(dataSource, firstProfile);
        ChatRequestContext author = context("tenant-batch", "batch-author");
        acknowledgeActor(first, author, "@_weave_batch:matrix.internal");
        CanonicalChatStore.PreparedConversation room = room(
                first, author, "batch-room", "!batch-room:matrix.internal");
        CanonicalChatStore.ProviderCallbackEvent futureState = stateEvent(
                "hs-batch",
                "$batch-state:matrix.internal",
                "!batch-room:matrix.internal",
                "@_weave_batch:matrix.internal",
                "org.example.batch_state");

        first.beginCallback(PROVIDER, "hs-batch", "9".repeat(64), 1);
        first.recordCallbackEvent(PROVIDER, futureState);
        first.completeCallback(PROVIDER, "hs-batch", 0);
        assertThat(first.reconcilePendingQuarantines(PROVIDER, 25)).isEmpty();

        JdbcCanonicalChatStore next = storeWithoutMigration(
                dataSource,
                firstProfile.withReclassifiedState(
                        "matrix-synapse-state-v2", "org.example.batch_state"));
        assertThat(next.reconcilePendingQuarantines(PROVIDER, 1))
                .singleElement()
                .satisfies(result -> {
                    assertThat(result.lifecycleState()).isEqualTo("reconciled");
                    assertThat(result.outcomeCode()).isEqualTo("reconciliation-committed");
                    assertThat(result.attemptCount()).isEqualTo(1);
                    assertThat(result.conversationHealed()).isTrue();
                });
        assertThat(next.reconcilePendingQuarantines(PROVIDER, 25)).isEmpty();
        assertThat(next.mapping(author.tenantId(), PROVIDER, "conversation", room.conversationId().value()))
                .hasValueSatisfying(mapping -> assertThat(mapping.state()).isEqualTo("acknowledged"));
    }

    private CanonicalChatStore.ProviderCallbackEvent stateEvent(
            String transactionId,
            String eventRef,
            String roomRef,
            String senderRef,
            String eventType) {
        return new CanonicalChatStore.ProviderCallbackEvent(
                transactionId,
                null,
                eventRef,
                roomRef,
                senderRef,
                eventType,
                "",
                null,
                Map.of("enabled", true),
                "state-v1");
    }

    private CanonicalChatStore.PreparedConversation room(
            JdbcCanonicalChatStore store,
            ChatRequestContext context,
            String key,
            String providerRoomRef) {
        CanonicalChatStore.PreparedConversation room = store.prepareConversation(
                context,
                new ChatTransactionId("create-" + context.tenantId() + "-" + key),
                "Room " + key,
                "channel",
                List.of(),
                PROVIDER,
                "#_weave_" + key + ":matrix.internal",
                ChatEncryptionState.unencrypted());
        store.acknowledgeConversation(context, room, PROVIDER, providerRoomRef, "room-v11");
        return room;
    }

    private ChatRequestContext context(String tenantId, String actor) {
        return new ChatRequestContext(
                tenantId,
                "context-" + tenantId,
                "https://auth.example/realms/" + tenantId,
                new ChatActorRef("user:" + actor));
    }

    private void acknowledgeActor(
            JdbcCanonicalChatStore store,
            ChatRequestContext context,
            String providerActorRef) {
        String canonicalActor = "{\"issuer\":\"" + context.identityIssuer()
                + "\",\"actorRef\":\"" + context.actorRef().value() + "\"}";
        store.reserveMapping(context.tenantId(), PROVIDER, "actor", canonicalActor, providerActorRef, null);
        store.acknowledgeMapping(
                context.tenantId(), PROVIDER, "actor", canonicalActor, providerActorRef, "actor-v1");
    }

    private JdbcCanonicalChatStore store(
            DriverManagerDataSource dataSource,
            MatrixSynapseCompatibilityProfile profile) {
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        return storeWithoutMigration(dataSource, profile);
    }

    private JdbcCanonicalChatStore storeWithoutMigration(
            DriverManagerDataSource dataSource,
            MatrixSynapseCompatibilityProfile profile) {
        return new JdbcCanonicalChatStore(
                new JdbcTemplate(dataSource),
                new ObjectMapper().findAndRegisterModules(),
                FIXED,
                profile);
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
