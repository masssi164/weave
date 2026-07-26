package com.massimotter.weave.mcp;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.massimotter.weave.mcp")
class McpModuleArchitectureTest {

  @ArchTest
  static final ArchRule mcpIsOnlyAProtocolAndStandardFacadeProjection =
      noClasses()
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.massimotter.weave.backend..",
              "jakarta.persistence..",
              "org.springframework.data..",
              "org.hibernate..");
}
