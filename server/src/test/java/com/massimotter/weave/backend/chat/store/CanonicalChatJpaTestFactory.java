package com.massimotter.weave.backend.chat.store;

import com.massimotter.weave.backend.testing.JpaTestDatabase;
import javax.sql.DataSource;

final class CanonicalChatJpaTestFactory {
    private CanonicalChatJpaTestFactory() {
    }

    static CanonicalChatJpaAuthority authority(DataSource dataSource) {
        return new CanonicalChatJpaAuthority(
                JpaTestDatabase.repository(dataSource, ChatConversationJpaRepository.class),
                JpaTestDatabase.repository(dataSource, ChatMembershipJpaRepository.class),
                JpaTestDatabase.repository(dataSource, ChatEventJpaRepository.class),
                JpaTestDatabase.repository(dataSource, ChatOperationJpaRepository.class),
                JpaTestDatabase.repository(dataSource, ChatOutboxJpaRepository.class),
                JpaTestDatabase.repository(dataSource, ChatProviderMappingJpaRepository.class),
                JpaTestDatabase.repository(dataSource, ChatBridgeLedgerJpaRepository.class),
                JpaTestDatabase.repository(
                        dataSource, ChatAppserviceTransactionJpaRepository.class),
                JpaTestDatabase.repository(dataSource, ChatQuarantineJpaRepository.class),
                JpaTestDatabase.repository(dataSource, ChatReadReceiptJpaRepository.class),
                JpaTestDatabase.repository(dataSource, ChatChangeJpaRepository.class),
                new ChatCallbackClaimNativeRepository(
                        JpaTestDatabase.entityManager(dataSource)),
                JpaTestDatabase.transactionManager(dataSource));
    }
}
