package com.massimotter.weave.backend.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.massimotter.weave.backend")
class ApplicationCoreArchitectureTest {

  @ArchTest
  static final ArchRule applicationCoreIsFrameworkFree =
      noClasses()
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..",
              "jakarta..",
              "javax.persistence..",
              "com.fasterxml..",
              "tools.jackson..",
              "com.massimotter.weave.backend.agentruntime.adapter..",
              "com.massimotter.weave.backend.config..",
              "com.massimotter.weave.backend.controller..",
              "com.massimotter.weave.backend.persistence..");

  @ArchTest
  static final ArchRule domainDoesNotDependOnPortsOrUseCases =
      noClasses()
          .that()
          .resideInAPackage("..agentruntime.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..agentruntime.port..", "..agentruntime.application..");

  @ArchTest
  static final ArchRule coreLayersAreAcyclic =
      slices()
          .matching("com.massimotter.weave.backend.agentruntime.(*)..")
          .should()
          .beFreeOfCycles();
}
