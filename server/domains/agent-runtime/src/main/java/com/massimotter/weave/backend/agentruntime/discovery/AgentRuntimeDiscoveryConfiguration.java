package com.massimotter.weave.backend.agentruntime.discovery;

import com.massimotter.weave.backend.security.http.PublicHttpRoute;
import org.springframework.http.HttpMethod;
import com.massimotter.weave.backend.discovery.catalog.CanonicalDomainContribution;
import com.massimotter.weave.backend.discovery.catalog.ProviderCategoryFragment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AgentRuntimeDiscoveryConfiguration {

    @Bean
    PublicHttpRoute runtimeTrustKeysPublicRoute() {
        return new PublicHttpRoute("agentruntime", "/api/v1/agent-runtime/trust/jwks.json", null);
    }

    @Bean
    ProviderCategoryFragment agentruntimeModelCategory() {
        return AgentRuntimeDiscoveryMetadata.modelCategory();
    }

    @Bean
    ProviderCategoryFragment agentruntimeAgentRuntimeControlCategory() {
        return AgentRuntimeDiscoveryMetadata.agentRuntimeControlCategory();
    }

    @Bean
    CanonicalDomainContribution agentruntimeAgentRuntimeControlDomain() {
        return AgentRuntimeDiscoveryMetadata.agentRuntimeControlDomain();
    }
}
