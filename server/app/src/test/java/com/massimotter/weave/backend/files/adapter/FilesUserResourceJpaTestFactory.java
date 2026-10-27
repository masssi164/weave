package com.massimotter.weave.backend.files.adapter;

import com.massimotter.weave.backend.testing.JpaTestDatabase;
import javax.sql.DataSource;

/** Cross-context fixture for the real Files identity and Space relationship repository. */
public final class FilesUserResourceJpaTestFactory {

  private FilesUserResourceJpaTestFactory() {}

  public static JpaFilesUserResourceRepository create(DataSource dataSource) {
    return JpaTestDatabase.transactional(
        dataSource,
        new JpaFilesUserResourceRepository(
            JpaTestDatabase.repository(dataSource, FilesUserResourceJpaRepository.class)));
  }
}
