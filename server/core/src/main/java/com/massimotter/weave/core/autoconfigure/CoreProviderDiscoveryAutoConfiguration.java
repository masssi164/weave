package com.massimotter.weave.core.autoconfigure;

import com.massimotter.weave.backend.capability.WorkspaceCapabilityService;
import com.massimotter.weave.backend.discovery.api.ProviderRegistryController;
import com.massimotter.weave.backend.discovery.application.AdminProviderRegistryService;
import com.massimotter.weave.backend.discovery.application.ProviderRegistry;
import com.massimotter.weave.backend.discovery.catalog.CanonicalDomainContribution;
import com.massimotter.weave.backend.discovery.catalog.CanonicalDomainRegistry;
import com.massimotter.weave.backend.discovery.catalog.CoreDiscoveryConfiguration;
import com.massimotter.weave.backend.discovery.catalog.ProviderCapabilityContracts;
import com.massimotter.weave.backend.discovery.catalog.ProviderCategoryCatalog;
import com.massimotter.weave.backend.discovery.catalog.ProviderCategoryFragment;
import com.massimotter.weave.backend.discovery.provider.ProviderPort;
import com.massimotter.weave.backend.providerbinding.selection.ProviderSelectionRepository;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/** Shared discovery policy consumes actual module declarations and never installs optional implementations. */
@AutoConfiguration(after = {CoreWorkspaceCapabilityAutoConfiguration.class, CoreProviderBindingAutoConfiguration.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import({CoreDiscoveryConfiguration.class, AdminProviderRegistryService.class, ProviderRegistryController.class})
public class CoreProviderDiscoveryAutoConfiguration {
    @Bean
    ProviderCategoryCatalog providerCategoryCatalog(List<ProviderCategoryFragment> fragments) {
        return new ProviderCategoryCatalog(fragments);
    }

    @Bean
    ProviderCapabilityContracts providerCapabilityContracts(ProviderCategoryCatalog catalog) {
        return new ProviderCapabilityContracts(catalog);
    }

    @Bean
    CanonicalDomainRegistry canonicalDomainRegistry(List<CanonicalDomainContribution> domains) {
        return new CanonicalDomainRegistry(domains);
    }

    @Bean
    ProviderRegistry providerRegistry(List<ProviderPort> providers, WorkspaceCapabilityService capabilities,
            ProviderSelectionRepository selections, ProviderCategoryCatalog catalog,
            ProviderCapabilityContracts contracts, CanonicalDomainRegistry canonicalDomains) {
        return new ProviderRegistry(providers, capabilities, selections, catalog, contracts, canonicalDomains);
    }
}
