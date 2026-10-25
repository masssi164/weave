package com.massimotter.weave.backend.agentruntime.adapter;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.massimotter.weave.backend.agentruntime.adapter")
class ProviderAdapterArchitectureTest {

  @ArchTest
  static final ArchRule providerAdaptersRemainFrameworkAndPersistenceFree =
      noClasses()
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..",
              "jakarta.persistence..",
              "javax.persistence..",
              "org.hibernate..",
              "com.massimotter.weave.backend.controller..",
              "com.massimotter.weave.backend.persistence..",
              "com.massimotter.weave.mcp..");

  @ArchTest
  static final ArchRule providerAdaptersDoNotImplementOAuthTokenProtocols =
      noClasses()
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework.security.oauth2..",
              "com.nimbusds.oauth2..");
}
