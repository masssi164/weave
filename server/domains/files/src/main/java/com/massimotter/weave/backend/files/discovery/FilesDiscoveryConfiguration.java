package com.massimotter.weave.backend.files.discovery;

import com.massimotter.weave.backend.capability.WorkspaceCapabilityService;
import com.massimotter.weave.backend.discovery.catalog.CanonicalDomainContribution;
import com.massimotter.weave.backend.discovery.catalog.ProviderCategoryFragment;
import com.massimotter.weave.backend.discovery.config.PlatformContractProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class FilesDiscoveryConfiguration {

    @Bean
    NextcloudPlatformDiagnostics nextcloudPlatformDiagnostics(
            PlatformContractProperties platform) {
        return new NextcloudPlatformDiagnostics(platform);
    }

    @Bean
    FilesPlatformDiagnostics filesPlatformDiagnostics(
            WorkspaceCapabilityService capabilities) {
        return new FilesPlatformDiagnostics(capabilities);
    }

    @Bean
    ProviderCategoryFragment filesFilesCategory() {
        return FilesDiscoveryMetadata.filesCategory();
    }

    @Bean
    CanonicalDomainContribution filesFilesDomain() {
        return FilesDiscoveryMetadata.filesDomain();
    }
}
