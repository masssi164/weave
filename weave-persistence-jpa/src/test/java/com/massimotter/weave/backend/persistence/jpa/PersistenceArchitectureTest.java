package com.massimotter.weave.backend.persistence.jpa;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = {
      "com.massimotter.weave.backend.persistence.jpa",
      "com.massimotter.weave.backend.agentruntime.adapter"
    })
class PersistenceArchitectureTest {

  @ArchTest
  static final ArchRule persistenceDoesNotDependOnDeliveryOrProviders =
      noClasses()
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.massimotter.weave.backend.controller..",
              "com.massimotter.weave.backend.model..",
              "com.massimotter.weave.backend.provider..",
              "com.massimotter.weave.mcp..");

  @ArchTest
  static final ArchRule entitiesRemainPortableJpaTypes =
      noClasses()
          .that()
          .haveSimpleNameEndingWith("Entity")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..", "org.hibernate..", "com.fasterxml..", "tools.jackson..");

  @ArchTest
  static final ArchRule adaptersDoNotReintroduceJdbcDataAccess =
      noClasses()
          .that()
          .resideInAPackage("..agentruntime.adapter..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.springframework.jdbc..", "javax.sql..");
}
