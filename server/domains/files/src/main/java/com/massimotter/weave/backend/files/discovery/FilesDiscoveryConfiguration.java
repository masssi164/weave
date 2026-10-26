package com.massimotter.weave.backend.files.discovery;

import com.massimotter.weave.backend.discovery.catalog.CanonicalDomainContribution;
import com.massimotter.weave.backend.discovery.catalog.ProviderCategoryFragment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class FilesDiscoveryConfiguration {

    @Bean
    ProviderCategoryFragment filesFilesCategory() {
        return FilesDiscoveryMetadata.filesCategory();
    }

    @Bean
    CanonicalDomainContribution filesFilesDomain() {
        return FilesDiscoveryMetadata.filesDomain();
    }
}
