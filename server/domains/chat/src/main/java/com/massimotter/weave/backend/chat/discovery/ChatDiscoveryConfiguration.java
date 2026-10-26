package com.massimotter.weave.backend.chat.discovery;

import com.massimotter.weave.backend.admin.domain.AdminCapabilityDeclaration;
import com.massimotter.weave.backend.capability.WorkspaceCapabilityService;
import com.massimotter.weave.backend.chat.config.MatrixChatProperties;
import com.massimotter.weave.backend.discovery.catalog.CanonicalDomainContribution;
import com.massimotter.weave.backend.discovery.catalog.ProviderCategoryFragment;
import com.massimotter.weave.backend.discovery.config.PlatformContractProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@org.springframework.context.annotation.Import(ChatPublicRoutesConfiguration.class)
public class ChatDiscoveryConfiguration {

    @Bean
    AdminCapabilityDeclaration chatAdminCapabilities() {
        return ChatAdminMetadata.capabilities();
    }

    @Bean
    ChatMatrixPlatformContribution chatMatrixPlatformContribution(
            PlatformContractProperties platform,
            MatrixChatProperties matrix,
            WorkspaceCapabilityService capabilities,
            @Value("${weave.matrix.facade.base-url:https://api.weave.test}") String facadeBaseUrl,
            @Value("${weave.matrix.facade.server-name:api.weave.test}") String facadeServerName) {
        return new ChatMatrixPlatformContribution(platform, matrix, capabilities, facadeBaseUrl, facadeServerName);
    }

    @Bean
    ProviderCategoryFragment chatChatCategory() {
        return ChatDiscoveryMetadata.chatCategory();
    }

    @Bean
    CanonicalDomainContribution chatChatDomain() {
        return ChatDiscoveryMetadata.chatDomain();
    }
}
