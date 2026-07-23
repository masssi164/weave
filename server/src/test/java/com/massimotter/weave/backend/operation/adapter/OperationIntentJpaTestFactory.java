package com.massimotter.weave.backend.operation.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.testing.JpaTestDatabase;
import javax.sql.DataSource;

/** Cross-context test fixture for the real typed operation-intent adapter. */
public final class OperationIntentJpaTestFactory {

    private OperationIntentJpaTestFactory() {
    }

    public static JpaOperationIntentRepository create(DataSource dataSource) {
        return new JpaOperationIntentRepository(
                JpaTestDatabase.repository(
                        dataSource,
                        OperationIntentJpaRepository.class),
                JpaTestDatabase.repository(
                        dataSource,
                        OperationOutboxJpaRepository.class),
                new OperationIntentLeaseNativeRepository(
                        JpaTestDatabase.entityManager(dataSource)),
                new ObjectMapper().findAndRegisterModules(),
                JpaTestDatabase.transactionManager(dataSource));
    }
}
