package com.massimotter.weave.backend.config;

import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.agentruntime.adapter.FileRuntimeWorkloadCredentialStore;
import com.massimotter.weave.backend.agentruntime.adapter.KeycloakAdminAccessTokenProvider;
import com.massimotter.weave.backend.agentruntime.adapter.KeycloakAgentRuntimeWorkloadIdentityAdmin;
import com.massimotter.weave.backend.agentruntime.adapter.KeycloakClientRegistrationTransport;
import com.massimotter.weave.backend.agentruntime.adapter.KeycloakRuntimeIdentityAuthority;
import com.massimotter.weave.backend.agentruntime.adapter.McpExchangedTokenPolicy;
import com.massimotter.weave.backend.agentruntime.application.AgentRuntimeControlService;
import com.massimotter.weave.backend.agentruntime.application.AgentRuntimeWorkloadReconciliationService;
import com.massimotter.weave.backend.agentruntime.application.McpWorkloadAuthorizationService;
import com.massimotter.weave.backend.agentruntime.port.RuntimeCellRepository;
import com.massimotter.weave.backend.agentruntime.port.RuntimeCommandRepository;
import com.massimotter.weave.backend.agentruntime.port.RuntimeEntitlementAuthority;
import com.massimotter.weave.backend.agentruntime.port.RuntimeGovernanceRepository;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileRepository;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileVerifier;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadCredentialStore;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityAdmin;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityInventory;
import com.massimotter.weave.backend.agentruntime.port.SecretRefAccess;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
  AgentRuntimeWorkloadIdentityProperties.class,
  AgentRuntimeEntitlementProperties.class
})
@ConditionalOnExpression("'${weave.agent-runtime.workload-identity.enabled:false}' == 'true'")
public class AgentRuntimeWorkloadIdentityConfiguration {

  @Bean
  FileRuntimeWorkloadCredentialStore fileRuntimeWorkloadCredentialStore(
      AgentRuntimeWorkloadIdentityProperties properties, ObjectMapper objectMapper) {
    return new FileRuntimeWorkloadCredentialStore(properties.requiredSecretRoot(), objectMapper);
  }

  @Bean
  KeycloakAdminAccessTokenProvider keycloakAgentRuntimeAdminAccessTokenProvider(
      AgentRuntimeWorkloadIdentityProperties properties,
      @Qualifier("fileRuntimeWorkloadCredentialStore") SecretRefAccess secrets) {
    return new SpringSecurityKeycloakAdminAccessTokenProvider(
        properties.workloadAdminTokenSettings(), secrets);
  }

  @Bean
  KeycloakAdminAccessTokenProvider keycloakAgentRuntimeEntitlementAccessTokenProvider(
      AgentRuntimeWorkloadIdentityProperties properties,
      OAuth2AuthorizedClientManager authorizedClients,
      OAuth2AuthorizedClientService authorizedClientService) {
    return new SpringAuthorizedClientKeycloakAccessTokenProvider(
        properties.entitlementClientId(), authorizedClients, authorizedClientService);
  }

  @Bean
  KeycloakClientRegistrationTransport keycloakWorkloadClientRegistrationTransport(
      AgentRuntimeWorkloadIdentityProperties properties) {
    return new SpringKeycloakClientRegistrationTransport(
        properties.keycloakAdminBaseUrl(), properties.realm(), properties.timeout());
  }

  @Bean
  KeycloakAgentRuntimeWorkloadIdentityAdmin runtimeWorkloadIdentityAdmin(
      AgentRuntimeWorkloadIdentityProperties properties,
      FileRuntimeWorkloadCredentialStore credentials,
      @Qualifier("keycloakAgentRuntimeAdminAccessTokenProvider")
          KeycloakAdminAccessTokenProvider accessTokens,
      @Qualifier("keycloakWorkloadClientRegistrationTransport")
          KeycloakClientRegistrationTransport registrationTransport,
      ObjectMapper objectMapper) {
    return new KeycloakAgentRuntimeWorkloadIdentityAdmin(
        properties.workloadSettings(),
        credentials,
        accessTokens,
        registrationTransport,
        objectMapper);
  }

  @Bean
  KeycloakRuntimeIdentityAuthority runtimeIdentityAuthority(
      AgentRuntimeWorkloadIdentityProperties properties,
      AgentRuntimeEntitlementProperties entitlement,
      @Qualifier("keycloakAgentRuntimeEntitlementAccessTokenProvider")
          KeycloakAdminAccessTokenProvider accessTokens,
      ObjectMapper objectMapper) {
    return new KeycloakRuntimeIdentityAuthority(
        properties.entitlementSettings(entitlement), accessTokens, objectMapper);
  }

  @Bean
  AgentRuntimeWorkloadReconciliationService agentRuntimeWorkloadReconciliationService(
      RuntimeCellRepository cells,
      AgentRuntimeControlService controlService,
      RuntimeWorkloadIdentityAdmin workloadIdentityAdmin,
      RuntimeWorkloadIdentityInventory workloadIdentityInventory,
      RuntimeWorkloadCredentialStore credentials,
      ProviderHealthProperties providerHealth,
      MeterRegistry meters) {
    return new AgentRuntimeWorkloadReconciliationService(
        cells,
        controlService,
        workloadIdentityAdmin,
        workloadIdentityInventory,
        credentials,
        providerHealth,
        meters);
  }

  @Bean
  AgentRuntimeControlService agentRuntimeControlService(
      RuntimeCellRepository cells,
      RuntimeCommandRepository commands,
      RuntimeProfileRepository profiles,
      RuntimeWorkloadIdentityAdmin workloadIdentityAdmin,
      RuntimeEntitlementAuthority entitlementAuthority,
      RuntimeGovernanceRepository governance) {
    return new AgentRuntimeControlService(
        cells,
        commands,
        profiles,
        workloadIdentityAdmin,
        entitlementAuthority,
        governance,
        Clock.systemUTC());
  }

  @Bean
  McpExchangedTokenPolicy mcpExchangedTokenPolicy(PlatformContractProperties platform) {
    return new McpExchangedTokenPolicy(platform.apiBaseUrl(), "weave-mcp-server");
  }

  @Bean
  McpWorkloadAuthorizationService mcpWorkloadAuthorizationService(
      RuntimeCellRepository cells,
      RuntimeProfileRepository profiles,
      RuntimeProfileVerifier verifier,
      RuntimeGovernanceRepository governance,
      RuntimeWorkloadIdentityAdmin workloadIdentityAdmin,
      RuntimeEntitlementAuthority entitlementAuthority) {
    return new McpWorkloadAuthorizationService(
        cells,
        profiles,
        verifier,
        governance,
        workloadIdentityAdmin,
        entitlementAuthority,
        Clock.systemUTC());
  }
}
