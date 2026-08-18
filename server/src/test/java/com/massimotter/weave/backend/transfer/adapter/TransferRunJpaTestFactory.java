package com.massimotter.weave.backend.transfer.adapter;

import com.massimotter.weave.backend.testing.JpaTestDatabase;
import javax.sql.DataSource;

/** Cross-context test fixture for the real transfer-run JPA adapter. */
public final class TransferRunJpaTestFactory {

    private TransferRunJpaTestFactory() {
    }

    public static JpaTransferRunRepository create(DataSource dataSource) {
        return JpaTestDatabase.transactional(
                dataSource,
                new JpaTransferRunRepository(
                        JpaTestDatabase.repository(dataSource, TransferRunJpaRepository.class)));
    }
}
