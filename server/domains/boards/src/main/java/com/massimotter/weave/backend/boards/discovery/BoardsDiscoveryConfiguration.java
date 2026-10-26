package com.massimotter.weave.backend.boards.discovery;

import com.massimotter.weave.backend.admin.domain.AdminCapabilityDeclaration;
import com.massimotter.weave.backend.admin.domain.SuiteDomainDeclaration;
import com.massimotter.weave.backend.discovery.catalog.CanonicalDomainContribution;
import com.massimotter.weave.backend.discovery.catalog.ProviderCategoryFragment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class BoardsDiscoveryConfiguration {

    @Bean
    SuiteDomainDeclaration boardsAdminSuite() {
        return BoardsAdminMetadata.suite();
    }

    @Bean
    AdminCapabilityDeclaration boardsAdminCapabilities() {
        return BoardsAdminMetadata.capabilities();
    }

    @Bean
    ProviderCategoryFragment boardsBoardsTasksCategory() {
        return BoardsDiscoveryMetadata.boardsTasksCategory();
    }

    @Bean
    CanonicalDomainContribution boardsBoardsDomain() {
        return BoardsDiscoveryMetadata.boardsDomain();
    }
}
