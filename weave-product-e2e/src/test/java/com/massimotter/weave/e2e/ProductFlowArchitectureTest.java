package com.massimotter.weave.e2e;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.massimotter.weave.e2e")
class ProductFlowArchitectureTest {
  @ArchTest
  static final ArchRule PRODUCT_PROOF_STAYS_OUTSIDE_RUNTIME_FRAMEWORKS =
      noClasses()
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..",
              "jakarta.persistence..",
              "org.hibernate..",
              "com.massimotter.weave.backend..",
              "com.massimotter.weave.mcp..");
}
