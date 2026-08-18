package com.massimotter.weave.backend.agentruntime.adapter;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.massimotter.weave.backend.agentruntime.adapter")
class SecurityAdapterArchitectureTest {

  @ArchTest
  static final ArchRule securityAdaptersDoNotOwnDeliveryPersistenceOrProviderHttp =
      noClasses()
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework.context..",
              "org.springframework.web..",
              "org.springframework.data..",
              "jakarta.persistence..",
              "javax.persistence..",
              "org.hibernate..",
              "java.net.http..",
              "com.massimotter.weave.backend.controller..",
              "com.massimotter.weave.backend.persistence..",
              "com.massimotter.weave.mcp..");
}
