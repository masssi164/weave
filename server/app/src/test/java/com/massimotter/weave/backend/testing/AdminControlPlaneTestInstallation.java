package com.massimotter.weave.backend.testing;

import com.massimotter.weave.backend.admin.application.AdminControlPlaneService;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.capability.WorkspaceCapabilityService;
import com.massimotter.weave.backend.discovery.application.ProviderRegistry;
import com.massimotter.weave.backend.discovery.catalog.ProviderCategoryCatalog;
import com.massimotter.weave.backend.discovery.catalog.ProviderCapabilityContracts;
import com.massimotter.weave.backend.identity.OrganizationIdentityContextResolver;
import com.massimotter.weave.backend.providerbinding.port.ProviderBindingRepository;
import com.massimotter.weave.backend.providerbinding.selection.ProviderSelectionRepository;
import java.time.Clock;

/** Explicit full-domain fixture; production has no default identity or missing-persistence constructor. */
public final class AdminControlPlaneTestInstallation {
    private AdminControlPlaneTestInstallation() {}

    public static AdminControlPlaneService service(ProviderCategoryCatalog catalog, ProviderCapabilityContracts contracts,
            ProviderRegistry registry, WorkspaceCapabilityService capabilities, ProviderSelectionRepository selections,
            AuditEventPublisher audit, Clock clock) {
        return service(catalog, contracts, registry, capabilities, selections, audit, clock,
                OrganizationIdentityContextResolver.defaults());
    }

    public static AdminControlPlaneService service(ProviderCategoryCatalog catalog, ProviderCapabilityContracts contracts,
            ProviderRegistry registry, WorkspaceCapabilityService capabilities, ProviderSelectionRepository selections,
            AuditEventPublisher audit, Clock clock, OrganizationIdentityContextResolver identities) {
        return service(catalog, contracts, registry, capabilities, selections, audit, clock, identities,
                org.mockito.Mockito.mock(ProviderBindingRepository.class));
    }

    public static AdminControlPlaneService service(ProviderCategoryCatalog catalog, ProviderCapabilityContracts contracts,
            ProviderRegistry registry, WorkspaceCapabilityService capabilities, ProviderSelectionRepository selections,
            AuditEventPublisher audit, Clock clock, OrganizationIdentityContextResolver identities,
            ProviderBindingRepository bindings) {
        return new AdminControlPlaneService(catalog, contracts, registry, capabilities, selections, audit, clock,
                identities, bindings, ProviderDiscoveryTestInstallation.adminCapabilities(),
                ProviderDiscoveryTestInstallation.adminSuites(), ProviderDiscoveryTestInstallation.mcpBindings(),
                ProviderDiscoveryTestInstallation.adminApiLinks());
    }
}
