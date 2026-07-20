package com.massimotter.weave.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.agentruntime.adapter.ClientSecretKeycloakAdminAccessTokenProvider;
import com.massimotter.weave.backend.agentruntime.adapter.FileRuntimeWorkloadCredentialStore;
import com.massimotter.weave.backend.agentruntime.adapter.KeycloakAdminAccessTokenProvider;
import com.massimotter.weave.backend.agentruntime.adapter.KeycloakAgentRuntimeWorkloadIdentityAdmin;
import com.massimotter.weave.backend.agentruntime.application.AgentRuntimeControlService;
import com.massimotter.weave.backend.agentruntime.port.RuntimeCellRepository;
import com.massimotter.weave.backend.agentruntime.port.RuntimeCommandRepository;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileRepository;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadCredentialStore;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityAdmin;
import com.massimotter.weave.backend.agentruntime.port.SecretRefAccess;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AgentRuntimeWorkloadIdentityProperties.class)
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
    RuntimeWorkloadIdentityAdmin runtimeWorkloadIdentityAdmin(
            AgentRuntimeWorkloadIdentityProperties properties,
            RuntimeWorkloadCredentialStore credentials,
            KeycloakAdminAccessTokenProvider accessTokens,
            ObjectMapper objectMapper) {
        return new KeycloakAgentRuntimeWorkloadIdentityAdmin(
                properties.workloadSettings(), credentials, accessTokens, objectMapper);
    }

    @Bean
    AgentRuntimeControlService agentRuntimeControlService(
            RuntimeCellRepository cells,
            RuntimeCommandRepository commands,
            RuntimeProfileRepository profiles,
            RuntimeWorkloadIdentityAdmin workloadIdentityAdmin) {
        return new AgentRuntimeControlService(
                cells, commands, profiles, workloadIdentityAdmin, Clock.systemUTC());
    }
}
