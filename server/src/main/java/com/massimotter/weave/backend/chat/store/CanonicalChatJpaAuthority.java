package com.massimotter.weave.backend.chat.store;

import static java.util.Objects.requireNonNull;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Composition boundary for canonical Chat persistence.
 *
 * <p>The adapter owns the complete repository set so persistence entities do
 * not leak into application services or runtime configuration.
 */
@Component
public final class CanonicalChatJpaAuthority {
    private final ChatConversationJpaRepository conversations;
    private final ChatMembershipJpaRepository memberships;
    private final ChatEventJpaRepository events;
    private final ChatOperationJpaRepository operations;
    private final ChatOutboxJpaRepository outbox;
    private final ChatProviderMappingJpaRepository mappings;
    private final ChatBridgeLedgerJpaRepository ledger;
    private final ChatAppserviceTransactionJpaRepository callbacks;
    private final ChatQuarantineJpaRepository quarantines;
    private final ChatReadReceiptJpaRepository receipts;
    private final ChatChangeJpaRepository changes;
    private final PlatformTransactionManager transactionManager;
    private final TransactionTemplate callbackClaimTransactions;

    CanonicalChatJpaAuthority(
            ChatConversationJpaRepository conversations,
            ChatMembershipJpaRepository memberships,
            ChatEventJpaRepository events,
            ChatOperationJpaRepository operations,
            ChatOutboxJpaRepository outbox,
            ChatProviderMappingJpaRepository mappings,
            ChatBridgeLedgerJpaRepository ledger,
            ChatAppserviceTransactionJpaRepository callbacks,
            ChatQuarantineJpaRepository quarantines,
            ChatReadReceiptJpaRepository receipts,
            ChatChangeJpaRepository changes,
            PlatformTransactionManager transactionManager) {
        this.conversations = requireNonNull(conversations, "conversations");
        this.memberships = requireNonNull(memberships, "memberships");
        this.events = requireNonNull(events, "events");
        this.operations = requireNonNull(operations, "operations");
        this.outbox = requireNonNull(outbox, "outbox");
        this.mappings = requireNonNull(mappings, "mappings");
        this.ledger = requireNonNull(ledger, "ledger");
        this.callbacks = requireNonNull(callbacks, "callbacks");
        this.quarantines = requireNonNull(quarantines, "quarantines");
        this.receipts = requireNonNull(receipts, "receipts");
        this.changes = requireNonNull(changes, "changes");
        this.transactionManager = requireNonNull(transactionManager, "transactionManager");
        this.callbackClaimTransactions = new TransactionTemplate(transactionManager);
        this.callbackClaimTransactions.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    ChatConversationJpaRepository conversations() { return conversations; }
    ChatMembershipJpaRepository memberships() { return memberships; }
    ChatEventJpaRepository events() { return events; }
    ChatOperationJpaRepository operations() { return operations; }
    ChatOutboxJpaRepository outbox() { return outbox; }
    ChatProviderMappingJpaRepository mappings() { return mappings; }
    ChatBridgeLedgerJpaRepository ledger() { return ledger; }
    ChatAppserviceTransactionJpaRepository callbacks() { return callbacks; }
    ChatQuarantineJpaRepository quarantines() { return quarantines; }
    ChatReadReceiptJpaRepository receipts() { return receipts; }
    ChatChangeJpaRepository changes() { return changes; }
    PlatformTransactionManager transactionManager() { return transactionManager; }

    /**
     * Claims a previously absent callback identity in its own transaction.
     *
     * <p>A uniqueness conflict marks only this short transaction for rollback.
     * The caller's use-case transaction remains usable and can reconcile the
     * row committed by the concurrent first writer. This is portable JPA and
     * does not rely on a database-specific upsert.
     */
    boolean claimCallback(
            String providerKey,
            String transactionId,
            String payloadDigest,
            int eventCount,
            String fingerprintVersion,
            java.time.Instant receivedAt) {
        ChatPairId id = new ChatPairId(providerKey, transactionId);
        try {
            callbackClaimTransactions.executeWithoutResult(status ->
                    callbacks.saveAndFlush(ChatAppserviceTransactionJpaEntity.processing(
                            providerKey,
                            transactionId,
                            payloadDigest,
                            eventCount,
                            fingerprintVersion,
                            receivedAt)));
            return true;
        } catch (RuntimeException race) {
            if (callbacks.existsById(id)) {
                return false;
            }
            throw race;
        }
    }
}
