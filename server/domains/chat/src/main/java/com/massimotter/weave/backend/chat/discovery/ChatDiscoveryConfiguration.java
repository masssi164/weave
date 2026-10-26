package com.massimotter.weave.backend.chat.discovery;

import com.massimotter.weave.backend.discovery.catalog.CanonicalDomainContribution;
import com.massimotter.weave.backend.discovery.catalog.ProviderCategoryFragment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ChatDiscoveryConfiguration {

    @Bean
    ProviderCategoryFragment chatChatCategory() {
        return ChatDiscoveryMetadata.chatCategory();
    }

    @Bean
    CanonicalDomainContribution chatChatDomain() {
        return ChatDiscoveryMetadata.chatDomain();
    }
}
