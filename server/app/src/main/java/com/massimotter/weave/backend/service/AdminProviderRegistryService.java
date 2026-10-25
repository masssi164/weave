package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.model.admin.FilesBindingStatusResponse;
import com.massimotter.weave.backend.model.admin.FilesBindingStatusResponse.BindingState;
import com.massimotter.weave.backend.model.admin.FilesBindingStatusResponse.Readiness;
import com.massimotter.weave.backend.provider.ProviderRegistry;
import com.massimotter.weave.backend.provider.ProviderRegistryResponse;
import com.massimotter.weave.backend.providerbinding.domain.ProviderBinding;
import com.massimotter.weave.backend.providerbinding.port.ProviderBindingRepository;
import com.massimotter.weave.backend.security.DeploymentOrganizationAdmission;
import com.massimotter.weave.backend.service.files.FilesProviderResolver;
import com.massimotter.weave.backend.service.files.FilesUserApiService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public final class AdminProviderRegistryService {
    private final ProviderRegistry registry;
    private final WorkspaceCapabilityService capabilities;
    private final DeploymentOrganizationAdmission admission;
    private final ProviderBindingRepository bindings;
    private final FilesProviderResolver files;

    public AdminProviderRegistryService(ProviderRegistry registry, WorkspaceCapabilityService capabilities,
            DeploymentOrganizationAdmission admission, ProviderBindingRepository bindings, FilesProviderResolver files) {
        this.registry = registry;
        this.capabilities = capabilities;
        this.admission = admission;
        this.bindings = bindings;
        this.files = files;
    }

    public ProviderRegistryResponse status(Jwt jwt) {
        if (!admission.allows(jwt)) {
            throw new ApiErrorException(HttpStatus.FORBIDDEN, "organization-access-denied",
                    "Access to this deployment organization is denied.", Map.of("diagnosticsRedacted", true));
        }
        capabilities.requireCapability(jwt, "admin_control_plane.readiness_read", "provider-registry", "status");
        String organization = admission.canonicalOrganizationId();
        ProviderRegistryResponse configuration = registry.status();
        return new ProviderRegistryResponse(
                configuration.releaseStatus(), configuration.providerConfigSource(),
                configuration.bootstrapDefaultsAreSuggestionsOnly(), configuration.adminSelectedMappingsRequired(),
                configuration.backendOwnedFacades(), configuration.flutterDirectProviderCallsAllowed(),
                configuration.supportSafe(), configuration.generatedAt(), configuration.canonicalDomainRegistry(),
                configuration.domainAdapterRegistry(), configuration.selectedProviderMappings(),
                configuration.categories(), configuration.providers(), organization, filesBinding(organization));
    }

    private FilesBindingStatusResponse filesBinding(String organization) {
        var binding = bindings.current(organization, "files")
                .filter(value -> value.state() == ProviderBinding.State.ACTIVE
                        && organization.equals(value.organizationRef()) && "files".equals(value.domain()));
        if (binding.isEmpty()) {
            return new FilesBindingStatusResponse(BindingState.NO_ACTIVE_BINDING, null, null, Readiness.NOT_CONFIGURED);
        }
        ProviderBinding current = binding.orElseThrow();
        Readiness readiness;
        try {
            files.pinned(current, organization, FilesUserApiService.SPACE_REF);
            readiness = Readiness.CONFIGURED;
        } catch (FilesProviderResolver.ProviderUnavailableException
                | FilesProviderResolver.StaleBindingException | ApiErrorException unavailable) {
            readiness = Readiness.UNAVAILABLE;
        }
        return new FilesBindingStatusResponse(BindingState.ACTIVE, current.revision(), current.adapterKey(), readiness);
    }
}
