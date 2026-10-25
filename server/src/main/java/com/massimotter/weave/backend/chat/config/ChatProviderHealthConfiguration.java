package com.massimotter.weave.backend.chat.config;

import com.massimotter.weave.backend.chat.port.ChatProviderPort;
import com.massimotter.weave.backend.health.ProviderHealthContribution;
import com.massimotter.weave.backend.portability.ProviderCapabilityProbeResult;
import com.massimotter.weave.backend.portability.ProviderReadiness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ChatProviderHealthConfiguration {
    @Bean
    ProviderHealthContribution chatProviderHealth(ObjectProvider<ChatProviderPort> providers) {
        ChatProviderPort provider = providers.getIfUnique();
        return new ProviderHealthContribution("chat", () -> provider != null && provider.configured(),
                () -> provider == null ? ProviderCapabilityProbeResult.unavailable("chat-adapter-unavailable") : probe(provider));
    }

    private static ProviderCapabilityProbeResult probe(ChatProviderPort provider) {
        ProviderReadiness observation = provider.readiness();
        if (observation.available()) return ProviderCapabilityProbeResult.available(observation.supportSafeCode());
        String code = observation.supportSafeCode();
        return code.contains("unavailable") || code.contains("interrupted")
                ? ProviderCapabilityProbeResult.unavailable(code)
                : ProviderCapabilityProbeResult.degraded(code);
    }
}
