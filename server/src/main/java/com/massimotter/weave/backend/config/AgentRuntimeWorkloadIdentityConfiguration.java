package com.massimotter.weave.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.agentruntime.adapter.ClientSecretKeycloakAdminAccessTokenProvider;
import com.massimotter.weave.backend.agentruntime.adapter.FileRuntimeWorkloadCredentialStore;
import com.massimotter.weave.backend.agentruntime.adapter.KeycloakAdminAccessTokenProvider;
import com.massimotter.weave.backend.agentruntime.adapter.KeycloakAgentRuntimeWorkloadIdentityAdmin;
import com.massimotter.weave.backend.agentruntime.adapter.KeycloakRuntimeEntitlementAuthority;
import com.massimotter.weave.backend.agentruntime.application.AgentRuntimeControlService;
import com.massimotter.weave.backend.agentruntime.application.AgentRuntimeWorkloadReconciliationService;
import com.massimotter.weave.backend.agentruntime.port.RuntimeCellRepository;
import com.massimotter.weave.backend.agentruntime.port.RuntimeCommandRepository;
import com.massimotter.weave.backend.agentruntime.port.RuntimeEntitlementAuthority;
import com.massimotter.weave.backend.agentruntime.port.RuntimeGovernanceRepository;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileRepository;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadCredentialStore;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityAdmin;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityInventory;
import com.massimotter.weave.backend.agentruntime.port.SecretRefAccess;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({AgentRuntimeWorkloadIdentityProperties.class, WeaverRuntimeProperties.class})
@ConditionalOnExpression(
        "'${weave.agent-runtime.storage.mode:disabled}' == 'jdbc'"
                + " && '${weave.agent-runtime.workload-identity.enabled:false}' == 'true'")
public class AgentRuntimeWorkloadIdentityConfiguration {

    @Bean
    FileRuntimeWorkloadCredentialStore fileRuntimeWorkloadCredentialStore(
            AgentRuntimeWorkloadIdentityProperties properties,
            ObjectMapper objectMapper) {
        return new FileRuntimeWorkloadCredentialStore(properties.requiredSecretRoot(), objectMapper);
    }

    @Bean
    KeycloakAdminAccessTokenProvider keycloakAgentRuntimeAdminAccessTokenProvider(
            AgentRuntimeWorkloadIdentityProperties properties,
            SecretRefAccess secrets,
            ObjectMapper objectMapper) {
        return new ClientSecretKeycloakAdminAccessTokenProvider(
                properties.adminTokenSettings(), secrets, objectMapper);
    }

    @Bean
    KeycloakAgentRuntimeWorkloadIdentityAdmin runtimeWorkloadIdentityAdmin(
            AgentRuntimeWorkloadIdentityProperties properties,
            RuntimeWorkloadCredentialStore credentials,
            KeycloakAdminAccessTokenProvider accessTokens,
            ObjectMapper objectMapper) {
        return new KeycloakAgentRuntimeWorkloadIdentityAdmin(
                properties.workloadSettings(), credentials, accessTokens, objectMapper);
    }

    @Bean
    RuntimeEntitlementAuthority runtimeEntitlementAuthority(
            AgentRuntimeWorkloadIdentityProperties properties,
            WeaverRuntimeProperties runtimePolicy,
            KeycloakAdminAccessTokenProvider accessTokens,
            ObjectMapper objectMapper) {
        return new KeycloakRuntimeEntitlementAuthority(
                properties.entitlementSettings(runtimePolicy), accessTokens, objectMapper);
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
                cells, commands, profiles, workloadIdentityAdmin, entitlementAuthority, governance,
                Clock.systemUTC());
    }
}
