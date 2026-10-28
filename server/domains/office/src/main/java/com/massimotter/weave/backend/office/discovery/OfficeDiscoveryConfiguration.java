package com.massimotter.weave.backend.office.discovery;

import com.massimotter.weave.backend.admin.domain.AdminCapabilityDeclaration;
import com.massimotter.weave.backend.admin.domain.SuiteDomainDeclaration;
import com.massimotter.weave.backend.discovery.catalog.CanonicalDomainContribution;
import com.massimotter.weave.backend.discovery.catalog.ProviderCategoryFragment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OfficeDiscoveryConfiguration {

    @Bean
    SuiteDomainDeclaration officeAdminSuite() {
        return OfficeAdminMetadata.suite();
    }

    @Bean
    AdminCapabilityDeclaration officeAdminCapabilities() {
        return OfficeAdminMetadata.capabilities();
    }

    @Bean
    ProviderCategoryFragment officeDocumentsCollaborationCategory() {
        return OfficeDiscoveryMetadata.documentsCollaborationCategory();
    }

    @Bean
    CanonicalDomainContribution officeDocumentsDomain() {
        return OfficeDiscoveryMetadata.documentsDomain();
    }
}
