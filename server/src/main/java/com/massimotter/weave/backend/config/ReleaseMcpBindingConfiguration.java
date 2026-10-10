package com.massimotter.weave.backend.config;

import com.massimotter.weave.backend.agentruntime.adapter.FileReleaseMcpBindingRepository;
import com.massimotter.weave.backend.agentruntime.adapter.KeycloakAdminAccessTokenProvider;
import com.massimotter.weave.backend.agentruntime.adapter.KeycloakRuntimeIdentityAuthority;
import com.massimotter.weave.backend.agentruntime.adapter.McpExchangedTokenPolicy;
import com.massimotter.weave.backend.agentruntime.application.McpWorkloadAuthorizationService;
import com.massimotter.weave.backend.agentruntime.port.ReleaseMcpBindingRepository;
import com.massimotter.weave.backend.agentruntime.port.RuntimeEntitlementAuthority;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/** The bounded MCP release path has no RuntimeCell, signed profile or lifecycle dependency. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
  AgentRuntimeWorkloadIdentityProperties.class,
  AgentRuntimeEntitlementProperties.class
})
@ConditionalOnExpression("'${weave.mcp.release-binding-file:}' != ''")
public class ReleaseMcpBindingConfiguration {

  @Bean
  ReleaseMcpBindingRepository releaseMcpBindingRepository(
      @Value("${weave.mcp.release-binding-file}") Path path, ObjectMapper mapper) {
    return new FileReleaseMcpBindingRepository(path, mapper);
  }

  @Bean
  RuntimeEntitlementAuthority releaseMcpEntitlementAuthority(
      AgentRuntimeWorkloadIdentityProperties properties,
      AgentRuntimeEntitlementProperties entitlement,
      @Qualifier(KeycloakAdminClientConfiguration.KEYCLOAK_ADMIN_ACCESS_TOKENS)
          KeycloakAdminAccessTokenProvider accessTokens,
      ObjectMapper mapper) {
    return new KeycloakRuntimeIdentityAuthority(
        properties.entitlementSettings(entitlement), accessTokens, mapper);
  }

  @Bean
  McpExchangedTokenPolicy releaseMcpExchangedTokenPolicy(PlatformContractProperties platform) {
    return new McpExchangedTokenPolicy(platform.apiBaseUrl(), "weave-mcp-server");
  }

  @Bean
  McpWorkloadAuthorizationService releaseMcpWorkloadAuthorizationService(
      ReleaseMcpBindingRepository bindings,
      RuntimeEntitlementAuthority entitlementAuthority,
      AgentRuntimeEntitlementProperties entitlement) {
    return new McpWorkloadAuthorizationService(
        bindings, entitlementAuthority, Set.copyOf(entitlement.allowedCapabilities()), Clock.systemUTC());
  }
}
