package com.massimotter.weave.mcp;

import com.massimotter.weave.backend.agentruntime.adapter.Ed25519JcsRuntimeProfileVerifier;
import com.massimotter.weave.backend.agentruntime.adapter.FileRuntimeProfileTrustKeyProvider;
import com.massimotter.weave.backend.agentruntime.application.McpWorkloadAuthorizationService;
import com.massimotter.weave.backend.agentruntime.port.RuntimeCellRepository;
import com.massimotter.weave.backend.agentruntime.port.RuntimeEntitlementAuthority;
import com.massimotter.weave.backend.agentruntime.port.RuntimeGovernanceRepository;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileRepository;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileVerifier;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadBindingAuthority;
import com.massimotter.weave.shared.persistence.AgentRuntimeJpaComposition;
import com.massimotter.weave.shared.persistence.SharedSchemaReadinessConfiguration;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = "weave.mcp.application-core.enabled",
        havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(McpApplicationCoreProperties.class)
@Import({AgentRuntimeJpaComposition.class, SharedSchemaReadinessConfiguration.class})
class McpApplicationCoreConfiguration {

    @Bean
    RuntimeProfileVerifier mcpRuntimeProfileVerifier(
            McpApplicationCoreProperties properties,
            JsonMapper mapper) {
        return new Ed25519JcsRuntimeProfileVerifier(
                mapper,
                new FileRuntimeProfileTrustKeyProvider(properties.profileTrustManifest(), mapper));
    }

    @Bean
    McpWorkloadAuthorizationService mcpWorkloadAuthorizationService(
            RuntimeCellRepository cells,
            RuntimeProfileRepository profiles,
            RuntimeProfileVerifier verifier,
            RuntimeGovernanceRepository governance,
            RuntimeWorkloadBindingAuthority identities,
            RuntimeEntitlementAuthority entitlementAuthority) {
        return new McpWorkloadAuthorizationService(
                cells,
                profiles,
                verifier,
                governance,
                identities,
                entitlementAuthority,
                Clock.systemUTC());
    }
}
