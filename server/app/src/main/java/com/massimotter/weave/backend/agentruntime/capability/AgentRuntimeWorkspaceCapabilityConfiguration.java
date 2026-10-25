package com.massimotter.weave.backend.agentruntime.capability;

import com.massimotter.weave.backend.capability.port.WorkspaceCapabilityContribution;
import com.massimotter.weave.backend.security.workload.config.WorkloadEntitlementProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the owning domain's capability declaration into shared workspace policy. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({AgentRuntimeWorkspaceCapabilityProperties.class, WorkloadEntitlementProperties.class})
public class AgentRuntimeWorkspaceCapabilityConfiguration {

    @Bean
    public WorkspaceCapabilityContribution agentruntimeWorkspaceCapabilityContribution(
            AgentRuntimeWorkspaceCapabilityProperties properties,
            WorkloadEntitlementProperties entitlement) {
        return AgentRuntimeWorkspaceCapabilities.contribution(properties, entitlement);
    }
}
