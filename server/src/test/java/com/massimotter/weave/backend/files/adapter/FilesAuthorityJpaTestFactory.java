package com.massimotter.weave.backend.files.adapter;

import com.massimotter.weave.backend.testing.JpaTestDatabase;
import javax.sql.DataSource;

/** Focused-test composition root for the canonical Files JPA authority. */
public final class FilesAuthorityJpaTestFactory {

    private FilesAuthorityJpaTestFactory() {
    }

    public static JpaFilesAuthorityRepository create(DataSource dataSource) {
        return JpaTestDatabase.transactional(
                dataSource,
                new JpaFilesAuthorityRepository(
                        JpaTestDatabase.repository(dataSource, FileObjectJpaRepository.class),
                        JpaTestDatabase.repository(dataSource, FileLockJpaRepository.class)));
    }
}
