package com.massimotter.weave.backend.config;

import com.massimotter.weave.backend.chat.port.ChatProviderPort;
import com.massimotter.weave.backend.providerbinding.port.ProviderBindingRepository;
import java.time.Instant;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Starts the configured deployment organization with one private Chat authority. */
@Configuration(proxyBeanMethods = false)
public class ChatBindingBootstrapConfiguration {
    @Bean
    ApplicationRunner chatBindingBootstrap(ProviderBindingRepository bindings,
            ContextAuthorizationProperties context, ChatProviderPort adapter) {
        return args -> {
            String organizationRef = context.defaultTenantId();
            String adapterKey = adapter.providerKey();
            String configurationRef = "profile:" + adapterKey;
            var current = bindings.current(organizationRef, "chat");
            if (current.isPresent()) {
                var binding = current.orElseThrow();
                if (!adapterKey.equals(binding.adapterKey())
                        || !configurationRef.equals(binding.configurationRef())) {
                    throw new IllegalStateException("Chat binding bootstrap conflicts with the active organization authority");
                }
                return;
            }
            bindings.activate(organizationRef, "chat", 0, adapterKey, configurationRef, Instant.now());
        };
    }
}
