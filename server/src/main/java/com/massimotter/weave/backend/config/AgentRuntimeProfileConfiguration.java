package com.massimotter.weave.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.agentruntime.adapter.AgentRuntimeWorkloadTokenPolicy;
import com.massimotter.weave.backend.agentruntime.adapter.Ed25519JcsRuntimeProfileVerifier;
import com.massimotter.weave.backend.agentruntime.adapter.Ed25519RuntimeProfileTrustBundlePublisher;
import com.massimotter.weave.backend.agentruntime.adapter.UnavailableRuntimeProfileTrustKeyProvider;
import com.massimotter.weave.backend.agentruntime.application.RuntimeProfileDeliveryService;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileRepository;
import com.massimotter.weave.backend.agentruntime.port.RuntimeGovernanceRepository;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileTrustBundlePublisher;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileTrustKeyProvider;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileVerifier;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "weave.agent-runtime.storage.mode", havingValue = "jdbc")
public class AgentRuntimeProfileConfiguration {

    @Bean
    RuntimeProfileVerifier runtimeProfileVerifier(
            ObjectMapper objectMapper,
            ObjectProvider<RuntimeProfileTrustKeyProvider> trustKeys) {
        return new Ed25519JcsRuntimeProfileVerifier(
                objectMapper,
                trustKeys.getIfAvailable(UnavailableRuntimeProfileTrustKeyProvider::new));
    }

    @Bean
    RuntimeProfileTrustBundlePublisher runtimeProfileTrustBundlePublisher(
            ObjectProvider<RuntimeProfileTrustKeyProvider> trustKeys) {
        return new Ed25519RuntimeProfileTrustBundlePublisher(
                trustKeys.getIfAvailable(UnavailableRuntimeProfileTrustKeyProvider::new));
    }

    @Bean
    RuntimeProfileDeliveryService runtimeProfileDeliveryService(
            RuntimeProfileRepository profiles,
            RuntimeProfileVerifier verifier,
            RuntimeGovernanceRepository governance) {
        return new RuntimeProfileDeliveryService(profiles, verifier, governance, Clock.systemUTC());
    }

    @Bean
    AgentRuntimeWorkloadTokenPolicy agentRuntimeWorkloadTokenPolicy(
            PlatformContractProperties platform) {
        return new AgentRuntimeWorkloadTokenPolicy(platform.agentRuntimeControlResource());
    }
}
