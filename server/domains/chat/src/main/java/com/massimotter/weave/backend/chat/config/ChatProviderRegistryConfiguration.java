package com.massimotter.weave.backend.chat.config;

import com.massimotter.weave.backend.chat.application.ChatProviderCatalog;

import com.massimotter.weave.backend.chat.domain.ChatProviderBindingPolicy;
import org.springframework.context.annotation.Import;

import com.massimotter.weave.backend.chat.port.ChatProviderPort;
import com.massimotter.weave.backend.discovery.provider.ProviderModule;
import com.massimotter.weave.backend.discovery.provider.ProviderPort;
import com.massimotter.weave.backend.discovery.provider.RuntimeProviderStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@Import({ChatProviderBindingPolicy.class, com.massimotter.weave.backend.chat.api.security.MatrixClientSecurityErrorWriter.class})
public class ChatProviderRegistryConfiguration {

    @Bean
    ProviderPort chatProviderRegistrySeam(ChatProviderPort runtime) {
        return chatProviderRegistrySeamFor(runtime);
    }

    ProviderPort chatProviderRegistrySeamFor(ChatProviderPort runtime) {
        return RuntimeProviderStatus.fromConformancePort(
                ProviderModule.MATRIX,
                runtime.providerKey(),
                runtime.configured(),
                runtime.conformanceProfile(),
                "The selected Chat adapter is bound behind the canonical Chat port; runtime reachability is reported by cached capability health.",
                ChatProviderCatalog.registryCandidates());
    }
}
