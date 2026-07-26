package com.massimotter.weave.backend.agentruntime.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.agentruntime.application.AgentRuntimeControlService;
import com.massimotter.weave.backend.agentruntime.application.AgentRuntimeWorkloadReconciliationService;
import com.massimotter.weave.backend.agentruntime.application.RuntimeProfileDeliveryService;
import com.massimotter.weave.backend.agentruntime.port.RuntimeEntitlementAuthority;
import com.massimotter.weave.backend.agentruntime.port.RuntimePersonDirectory;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileSigner;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileSigningKeyLifecycle;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileSigningKeyProvider;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileTrustBundlePublisher;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileTrustKeyProvider;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileVerifier;
import com.massimotter.weave.backend.agentruntime.port.RuntimeStateKeyWrapper;
import com.massimotter.weave.backend.agentruntime.port.RuntimeStateStore;
import com.massimotter.weave.backend.agentruntime.port.RuntimeStateStoreAdmin;
import com.massimotter.weave.backend.agentruntime.port.RuntimeStateWrappingKeyLifecycle;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadCredentialStore;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityAdmin;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityInventory;
import com.massimotter.weave.backend.agentruntime.port.SecretRefAccess;
import com.massimotter.weave.backend.config.AgentRuntimeProfileConfiguration;
import com.massimotter.weave.backend.config.AgentRuntimeProfileSigningConfiguration;
import com.massimotter.weave.backend.config.AgentRuntimeProfileSigningProperties;
import com.massimotter.weave.backend.config.AgentRuntimeStateStoreConfiguration;
import com.massimotter.weave.backend.config.AgentRuntimeStateStoreProperties;
import com.massimotter.weave.backend.config.AgentRuntimeWorkloadIdentityConfiguration;
import com.massimotter.weave.backend.config.AgentRuntimeWorkloadIdentityProperties;
import com.massimotter.weave.backend.config.PlatformContractProperties;
import com.massimotter.weave.backend.config.ProviderHealthProperties;
import com.massimotter.weave.backend.config.WeavePersistenceConfiguration;
import com.massimotter.weave.backend.persistence.jpa.agentruntime.RuntimeAuditCorrelationJpaRepository;
import com.massimotter.weave.backend.persistence.jpa.agentruntime.RuntimeCellJpaRepository;
import com.massimotter.weave.backend.persistence.jpa.agentruntime.RuntimeCommandJpaRepository;
import com.massimotter.weave.backend.persistence.jpa.agentruntime.RuntimeEntitlementJpaRepository;
import com.massimotter.weave.backend.persistence.jpa.agentruntime.RuntimeProfileJpaRepository;
import com.massimotter.weave.backend.persistence.jpa.agentruntime.RuntimeProfileSignatureJpaRepository;
import com.massimotter.weave.backend.persistence.jpa.agentruntime.RuntimeRevocationJpaRepository;
import com.massimotter.weave.backend.persistence.jpa.agentruntime.RuntimeStateDeletionJpaRepository;
import com.massimotter.weave.backend.persistence.jpa.agentruntime.RuntimeStateGenerationJpaRepository;
import com.massimotter.weave.backend.persistence.jpa.agentruntime.RuntimeStateHeadJpaRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;

class AgentRuntimePersistenceConfigurationTest {
  @TempDir Path temporary;

  private final ApplicationContextRunner context =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(WeavePersistenceConfiguration.class))
          .withUserConfiguration(
              AgentRuntimeProfileConfiguration.class,
              AgentRuntimeProfileSigningConfiguration.class,
              AgentRuntimeStateStoreConfiguration.class,
              AgentRuntimeWorkloadIdentityConfiguration.class)
          .withBean(ObjectMapper.class, ObjectMapper::new)
          .withBean(
              PlatformContractProperties.class,
              () -> new PlatformContractProperties(null, null, null, null, null, null, null, null))
          .withBean(
              ProviderHealthProperties.class,
              () -> new ProviderHealthProperties(null, null, null, null))
          .withBean(SimpleMeterRegistry.class, SimpleMeterRegistry::new)
          .withBean(
              PlatformTransactionManager.class,
              () -> org.mockito.Mockito.mock(PlatformTransactionManager.class))
          .withBean(
              RuntimeCellJpaRepository.class,
              () -> org.mockito.Mockito.mock(RuntimeCellJpaRepository.class))
          .withBean(
              RuntimeCommandJpaRepository.class,
              () -> org.mockito.Mockito.mock(RuntimeCommandJpaRepository.class))
          .withBean(
              RuntimeProfileJpaRepository.class,
              () -> org.mockito.Mockito.mock(RuntimeProfileJpaRepository.class))
          .withBean(
              RuntimeProfileSignatureJpaRepository.class,
              () -> org.mockito.Mockito.mock(RuntimeProfileSignatureJpaRepository.class))
          .withBean(
              RuntimeEntitlementJpaRepository.class,
              () -> org.mockito.Mockito.mock(RuntimeEntitlementJpaRepository.class))
          .withBean(
              RuntimeRevocationJpaRepository.class,
              () -> org.mockito.Mockito.mock(RuntimeRevocationJpaRepository.class))
          .withBean(
              RuntimeAuditCorrelationJpaRepository.class,
              () -> org.mockito.Mockito.mock(RuntimeAuditCorrelationJpaRepository.class))
          .withBean(
              RuntimeStateHeadJpaRepository.class,
              () -> org.mockito.Mockito.mock(RuntimeStateHeadJpaRepository.class))
          .withBean(
              RuntimeStateGenerationJpaRepository.class,
              () -> org.mockito.Mockito.mock(RuntimeStateGenerationJpaRepository.class))
          .withBean(
              RuntimeStateDeletionJpaRepository.class,
              () -> org.mockito.Mockito.mock(RuntimeStateDeletionJpaRepository.class))
          .withPropertyValues(
              "weave.agent-runtime.storage.mode=jpa",
              "spring.datasource.url=jdbc:h2:mem:arc-config;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
              "spring.datasource.username=sa",
              "spring.datasource.password=");

  @Test
  void jpaModePublishesRuntimeControlAndFailClosedProfileServices() {
    context.run(
        application -> {
          assertThat(application).hasSingleBean(JpaRuntimeCellRepository.class);
          assertThat(application).hasSingleBean(JpaRuntimeCommandRepository.class);
          assertThat(application).hasSingleBean(JpaRuntimeProfileRepository.class);
          assertThat(application).hasSingleBean(JpaRuntimeGovernanceRepository.class);
          assertThat(application).hasSingleBean(RuntimeProfileVerifier.class);
          assertThat(application).hasSingleBean(RuntimeProfileTrustBundlePublisher.class);
          assertThat(application).hasSingleBean(RuntimeProfileDeliveryService.class);
          assertThat(application).hasSingleBean(AgentRuntimeWorkloadTokenPolicy.class);
          assertThat(application).doesNotHaveBean(RuntimeProfileTrustKeyProvider.class);
          assertThat(application).doesNotHaveBean(RuntimeProfileSigningKeyProvider.class);
          assertThat(application).doesNotHaveBean(RuntimeProfileSigningKeyLifecycle.class);
          assertThat(application).doesNotHaveBean(RuntimeProfileSigner.class);
          assertThat(application).doesNotHaveBean(RuntimeStateStore.class);
          assertThat(application).doesNotHaveBean(RuntimeStateKeyWrapper.class);
          assertThat(application).doesNotHaveBean(RuntimeWorkloadCredentialStore.class);
          assertThat(application).doesNotHaveBean(RuntimeWorkloadIdentityAdmin.class);
        });
  }

  @Test
  void explicitRuntimeStateEnablementBuildsAnUninitializedFailClosedExternalStore() {
    context
        .withPropertyValues(
            "weave.agent-runtime.state-store.enabled=true",
            "weave.agent-runtime.state-store.wrapping-key-root="
                + temporary.resolve("state-keys").toAbsolutePath())
        .run(
            application -> {
              assertThat(application).hasNotFailed();
              assertThat(application).hasSingleBean(AgentRuntimeStateStoreProperties.class);
              assertThat(application).hasSingleBean(FileRuntimeStateKeyWrapper.class);
              assertThat(application).hasSingleBean(JpaEncryptedRuntimeStateStore.class);
              assertThat(application).hasSingleBean(RuntimeStateKeyWrapper.class);
              assertThat(application).hasSingleBean(RuntimeStateWrappingKeyLifecycle.class);
              assertThat(application).hasSingleBean(RuntimeStateStore.class);
              assertThat(application).hasSingleBean(RuntimeStateStoreAdmin.class);
              assertThat(application.getBean(RuntimeStateStore.class).readiness().ready())
                  .isFalse();
            });
  }

  @Test
  void runtimeStateEnablementWithoutAnExplicitWrappingKeyRootFailsStartup() {
    context
        .withPropertyValues("weave.agent-runtime.state-store.enabled=true")
        .run(
            application ->
                assertThat(application)
                    .hasFailed()
                    .getFailure()
                    .hasRootCauseMessage(
                        "RuntimeStateStore requires an explicit operator-mounted wrapping-key"
                            + " SecretRef root"));
  }

  @Test
  void explicitProfileSigningEnablementBuildsAStillUninitializedFailClosedTrustRoot() {
    context
        .withPropertyValues(
            "weave.agent-runtime.profile-signing.enabled=true",
            "weave.agent-runtime.profile-signing.secret-root=" + temporary.resolve("profile-keys"))
        .run(
            application -> {
              assertThat(application).hasNotFailed();
              assertThat(application).hasSingleBean(AgentRuntimeProfileSigningProperties.class);
              assertThat(application).hasSingleBean(FileRuntimeProfileSigningKeyStore.class);
              assertThat(application).hasSingleBean(RuntimeProfileSigningKeyProvider.class);
              assertThat(application).hasSingleBean(RuntimeProfileTrustKeyProvider.class);
              assertThat(application).hasSingleBean(RuntimeProfileSigningKeyLifecycle.class);
              assertThat(application).hasSingleBean(RuntimeProfileSigner.class);
              assertThat(
                      application
                          .getBean(RuntimeProfileTrustKeyProvider.class)
                          .publishedKeys(java.time.Instant.now()))
                  .isEmpty();
            });
  }

  @Test
  void profileSigningEnablementWithoutAnExplicitSecretRootFailsStartup() {
    context
        .withPropertyValues("weave.agent-runtime.profile-signing.enabled=true")
        .run(
            application ->
                assertThat(application)
                    .hasFailed()
                    .getFailure()
                    .hasRootCauseMessage(
                        "RuntimeProfile signing requires an explicit operator-mounted SecretRef"
                            + " root"));
  }

  @Test
  void explicitWorkloadIdentityEnablementBuildsTheCompleteSecretSafeControlGraph() {
    context
        .withPropertyValues(
            "weave.agent-runtime.workload-identity.enabled=true",
            "weave.agent-runtime.workload-identity.keycloak-admin-base-url=http://127.0.0.1:8180",
            "weave.agent-runtime.workload-identity.issuer=https://auth.weave.test/realms/weave",
            "weave.agent-runtime.workload-identity.organization-ref=tenant-default",
            "weave.agent-runtime.workload-identity.keycloak-organization-id=keycloak-org-uuid",
            "weave.agent-runtime.workload-identity.admin-credential-ref="
                + "credentialref://weave/agent-runtime/admin/keycloak",
            "weave.agent-runtime.workload-identity.entitlement-credential-ref="
                + "credentialref://weave/agent-runtime/admin/identity",
            "weave.agent-runtime.workload-identity.secret-root=" + temporary)
        .run(
            application -> {
              assertThat(application).hasSingleBean(AgentRuntimeWorkloadIdentityProperties.class);
              assertThat(application).hasSingleBean(FileRuntimeWorkloadCredentialStore.class);
              assertThat(application).hasSingleBean(RuntimeWorkloadCredentialStore.class);
              assertThat(application).hasSingleBean(SecretRefAccess.class);
              assertThat(application).getBeans(KeycloakAdminAccessTokenProvider.class).hasSize(2);
              assertThat(application).hasSingleBean(RuntimeWorkloadIdentityAdmin.class);
              assertThat(application).hasSingleBean(RuntimeWorkloadIdentityInventory.class);
              assertThat(application).hasSingleBean(RuntimeEntitlementAuthority.class);
              assertThat(application).hasSingleBean(RuntimePersonDirectory.class);
              assertThat(application).hasSingleBean(AgentRuntimeControlService.class);
              assertThat(application)
                  .hasSingleBean(AgentRuntimeWorkloadReconciliationService.class);
            });
  }
}
