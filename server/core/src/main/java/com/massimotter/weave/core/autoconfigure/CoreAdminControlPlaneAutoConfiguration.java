package com.massimotter.weave.core.autoconfigure;

import com.massimotter.weave.backend.admin.api.AdminControlPlaneController;
import com.massimotter.weave.backend.admin.api.McpServerBindingResponse;
import com.massimotter.weave.backend.admin.application.AdminCapabilityCatalog;
import com.massimotter.weave.backend.admin.application.AdminControlPlaneService;
import com.massimotter.weave.backend.admin.application.SuiteDomainCatalog;
import com.massimotter.weave.backend.admin.domain.AdminCapabilityDeclaration;
import com.massimotter.weave.backend.admin.domain.CoreAdminMetadata;
import com.massimotter.weave.backend.admin.domain.SuiteDomainDeclaration;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.capability.WorkspaceCapabilityService;
import com.massimotter.weave.backend.discovery.application.ProviderRegistry;
import com.massimotter.weave.backend.discovery.catalog.ProviderCategoryCatalog;
import com.massimotter.weave.backend.discovery.catalog.ProviderCapabilityContracts;
import com.massimotter.weave.backend.identity.OrganizationIdentityContextResolver;
import com.massimotter.weave.backend.providerbinding.port.ProviderBindingRepository;
import com.massimotter.weave.backend.providerbinding.selection.ProviderSelectionRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/** Shared Admin policy and transport assemble only actual installed domain declarations. */
@AutoConfiguration(after = CoreProviderDiscoveryAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import(AdminControlPlaneController.class)
public class CoreAdminControlPlaneAutoConfiguration {
    @Bean
    AdminCapabilityDeclaration coreAdminCapabilities() {
        return CoreAdminMetadata.capabilities();
    }

    @Bean
    AdminCapabilityCatalog adminCapabilityCatalog(List<AdminCapabilityDeclaration> declarations) {
        return new AdminCapabilityCatalog(declarations);
    }

    @Bean
    SuiteDomainCatalog suiteDomainCatalog(List<SuiteDomainDeclaration> declarations) {
        return new SuiteDomainCatalog(declarations);
    }

    @Bean
    AdminControlPlaneService adminControlPlaneService(
            ProviderCategoryCatalog catalog, ProviderCapabilityContracts contracts, ProviderRegistry registry,
            WorkspaceCapabilityService capabilities, ProviderSelectionRepository selections,
            AuditEventPublisher audit, OrganizationIdentityContextResolver identities,
            ProviderBindingRepository bindings, AdminCapabilityCatalog adminCapabilities,
            SuiteDomainCatalog suites, List<McpServerBindingResponse> mcpBindings,
            List<com.massimotter.weave.backend.admin.domain.AdminApiLink> adminApiLinks) {
        return new AdminControlPlaneService(catalog, contracts, registry, capabilities, selections, audit,
                Clock.systemUTC(), identities, bindings, adminCapabilities, suites, mcpBindings, adminApiLinks);
    }
}
