package com.massimotter.weave.backend.testing;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("postgres")
class PostgresJpaSchemaValidationTest {

  @Test
  void codeFirstPostgresSchemaMatchesTheCompleteHibernateEntityModel() {
    var dataSource = JpaTestDatabase.entityFirstDataSource("schema-validation");

    assertThatCode(() -> JpaTestDatabase.validateSchema(dataSource)).doesNotThrowAnyException();
  }
}
