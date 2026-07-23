package com.massimotter.weave.backend.chat.store;

import static java.util.Objects.requireNonNull;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Composition boundary for canonical Chat persistence.
 *
 * <p>Keeping the repository set behind one adapter-owned dependency prevents
 * persistence entities from leaking into configuration or application services.
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
    private final ChatCallbackClaimNativeRepository callbackClaims;
    private final PlatformTransactionManager transactionManager;

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
            ChatCallbackClaimNativeRepository callbackClaims,
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
        this.callbackClaims = requireNonNull(callbackClaims, "callbackClaims");
        this.transactionManager = requireNonNull(transactionManager, "transactionManager");
    }

    ChatConversationJpaRepository conversations() {
        return conversations;
    }

    ChatMembershipJpaRepository memberships() {
        return memberships;
    }

    ChatEventJpaRepository events() {
        return events;
    }

    ChatOperationJpaRepository operations() {
        return operations;
    }

    ChatOutboxJpaRepository outbox() {
        return outbox;
    }

    ChatProviderMappingJpaRepository mappings() {
        return mappings;
    }

    ChatBridgeLedgerJpaRepository ledger() {
        return ledger;
    }

    ChatAppserviceTransactionJpaRepository callbacks() {
        return callbacks;
    }

    ChatQuarantineJpaRepository quarantines() {
        return quarantines;
    }

    ChatReadReceiptJpaRepository receipts() {
        return receipts;
    }

    ChatChangeJpaRepository changes() {
        return changes;
    }

    ChatCallbackClaimNativeRepository callbackClaims() {
        return callbackClaims;
    }

    PlatformTransactionManager transactionManager() {
        return transactionManager;
    }
}
