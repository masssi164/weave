package com.massimotter.weave.backend.chat.store;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.springframework.stereotype.Repository;

/**
 * Reviewed native exception for an absent-row idempotency claim.
 *
 * <p>JPA optimistic locking starts after a row exists. This single static
 * statement provides the database-enforced first-writer claim needed for
 * concurrent Matrix Application Service delivery. No caller-controlled query
 * fragment or identifier enters the statement.
 */
@Repository
class ChatCallbackClaimNativeRepository {
    private static final String CLAIM_CALLBACK = """
            insert into weave_chat_appservice_transactions
                (provider_key, homeserver_transaction_id, payload_digest,
                 transaction_state, event_count, duplicate_count,
                 received_at_utc, completed_at_utc,
                 semantic_fingerprint_version, semantic_mismatch_count,
                 semantic_mismatch_hash, version)
            values
                (:providerKey, :transactionId, :payloadDigest,
                 'processing', :eventCount, 0,
                 :receivedAt, null,
                 :fingerprintVersion, 0,
                 null, 0)
            on conflict do nothing
            """;

    private final EntityManager entityManager;

    ChatCallbackClaimNativeRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    boolean claim(
            String providerKey,
            String transactionId,
            String payloadDigest,
            int eventCount,
            String fingerprintVersion,
            Instant receivedAt) {
        int inserted = entityManager.createNativeQuery(CLAIM_CALLBACK)
                .setParameter("providerKey", providerKey)
                .setParameter("transactionId", transactionId)
                .setParameter("payloadDigest", payloadDigest)
                .setParameter("eventCount", eventCount)
                .setParameter("receivedAt", CanonicalChatPersistence.utc(receivedAt))
                .setParameter("fingerprintVersion", fingerprintVersion)
                .executeUpdate();
        entityManager.clear();
        return inserted == 1;
    }
}
