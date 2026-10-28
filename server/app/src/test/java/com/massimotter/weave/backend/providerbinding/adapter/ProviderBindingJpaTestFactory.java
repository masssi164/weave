package com.massimotter.weave.backend.providerbinding.adapter;

import com.massimotter.weave.backend.testing.JpaTestDatabase;
import javax.sql.DataSource;

/** Cross-context test fixture for the real typed provider-binding adapter. */
public final class ProviderBindingJpaTestFactory {

    private ProviderBindingJpaTestFactory() {
    }

    public static JpaProviderBindingRepository create(DataSource dataSource) {
        return JpaTestDatabase.transactional(
                dataSource,
                new JpaProviderBindingRepository(
                JpaTestDatabase.repository(
                        dataSource,
                        ProviderBindingJpaRepository.class),
                JpaTestDatabase.repository(
                        dataSource,
                        ProviderObjectMappingJpaRepository.class),
                java.util.List.of(new com.massimotter.weave.backend.files.domain.FilesProviderBindingPolicy(),
                        new com.massimotter.weave.backend.calendar.domain.CalendarProviderBindingPolicy(),
                        new com.massimotter.weave.backend.chat.domain.ChatProviderBindingPolicy())));
    }
}
