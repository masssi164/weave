package com.massimotter.weave.shared.persistence;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import com.massimotter.weave.backend.agentruntime.port.RuntimeCellRepository;
import com.massimotter.weave.backend.agentruntime.port.RuntimeEntitlementAuthority;
import com.massimotter.weave.backend.agentruntime.port.RuntimeGovernanceRepository;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadBindingAuthority;
import org.springframework.context.annotation.Bean;

/**
 * Explicit MCP-side composition of the same adapter-private ARC entities and
 * repositories used by weave-server.
 */
@Configuration(proxyBeanMethods = false)
@EntityScan(basePackages = "com.massimotter.weave.backend.agentruntime.adapter")
@EnableJpaRepositories(basePackages = "com.massimotter.weave.backend.agentruntime.adapter")
@ComponentScan(basePackages = "com.massimotter.weave.backend.agentruntime.adapter")
public class AgentRuntimeJpaComposition {
    @Bean
    RuntimeWorkloadBindingAuthority persistedRuntimeWorkloadBindingAuthority(
            RuntimeCellRepository cells) {
        return new PersistedRuntimeWorkloadBindingAuthority(cells);
    }

    @Bean
    RuntimeEntitlementAuthority persistedRuntimeEntitlementAuthority(
            RuntimeGovernanceRepository governance) {
        return new PersistedRuntimeEntitlementAuthority(governance);
    }
}
