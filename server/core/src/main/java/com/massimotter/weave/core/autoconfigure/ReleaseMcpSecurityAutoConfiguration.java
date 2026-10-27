package com.massimotter.weave.core.autoconfigure;

import com.massimotter.weave.backend.discovery.config.PlatformContractProperties;
import com.massimotter.weave.backend.security.keycloak.port.KeycloakAdminAccessTokenProvider;
import com.massimotter.weave.backend.security.workload.application.ReleaseMcpWorkloadAuthorizationService;
import com.massimotter.weave.backend.security.workload.binding.FileReleaseMcpBindingRepository;
import com.massimotter.weave.backend.security.workload.config.WorkloadEntitlementProperties;
import com.massimotter.weave.backend.security.workload.config.WorkloadIdentityProperties;
import com.massimotter.weave.backend.security.workload.jwt.McpExchangedTokenPolicy;
import com.massimotter.weave.backend.security.workload.port.ReleaseMcpBindingRepository;
import com.massimotter.weave.backend.security.workload.port.WorkloadEntitlementAuthority;
import com.massimotter.weave.backend.security.workload.provider.KeycloakWorkloadEntitlementAuthority;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ObjectMapper;

/** Current release MCP authorization; future runtime lifecycle and profiles are not prerequisites. */
@AutoConfiguration(afterName = "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnExpression("'${weave.mcp.release-binding-file:}' != ''")
@EnableConfigurationProperties({WorkloadIdentityProperties.class, WorkloadEntitlementProperties.class,
        PlatformContractProperties.class})
public class ReleaseMcpSecurityAutoConfiguration {
    @Bean
    ReleaseMcpBindingRepository releaseMcpBindingRepository(
            @Value("${weave.mcp.release-binding-file}") Path path, ObjectMapper mapper) {
        return new FileReleaseMcpBindingRepository(path, mapper);
    }

    @Bean
    WorkloadEntitlementAuthority releaseMcpEntitlementAuthority(
            WorkloadIdentityProperties properties, WorkloadEntitlementProperties entitlement,
            @Qualifier("keycloakIdentityAdminAccessTokenProvider") KeycloakAdminAccessTokenProvider tokens,
            ObjectMapper mapper) {
        return new KeycloakWorkloadEntitlementAuthority(properties.entitlementSettings(entitlement), tokens, mapper);
    }

    @Bean
    McpExchangedTokenPolicy releaseMcpExchangedTokenPolicy(PlatformContractProperties platform) {
        return new McpExchangedTokenPolicy(platform.apiBaseUrl(), "weave-mcp-server");
    }

    @Bean
    ReleaseMcpWorkloadAuthorizationService releaseMcpWorkloadAuthorizationService(
            ReleaseMcpBindingRepository bindings, WorkloadEntitlementAuthority entitlement,
            WorkloadEntitlementProperties properties) {
        return new ReleaseMcpWorkloadAuthorizationService(bindings, entitlement,
                Set.copyOf(properties.allowedCapabilities()), Clock.systemUTC());
    }
}
