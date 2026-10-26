package com.massimotter.weave.backend.discovery.application;

import com.massimotter.weave.backend.api.error.ApiErrorException;
import com.massimotter.weave.backend.capability.WorkspaceCapabilityService;
import com.massimotter.weave.backend.discovery.api.FilesBindingStatusResponse.BindingState;
import com.massimotter.weave.backend.discovery.api.FilesBindingStatusResponse.Readiness;
import com.massimotter.weave.backend.discovery.api.FilesBindingStatusResponse;
import com.massimotter.weave.backend.discovery.api.ProviderRegistryResponse;
import com.massimotter.weave.backend.providerbinding.port.ProviderBindingStatusContribution;
import com.massimotter.weave.backend.security.DeploymentOrganizationAdmission;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

public final class AdminProviderRegistryService {
    private final ProviderRegistry registry;
    private final WorkspaceCapabilityService capabilities;
    private final DeploymentOrganizationAdmission admission;
    private final Map<String, ProviderBindingStatusContribution> bindingStatuses;

    public AdminProviderRegistryService(ProviderRegistry registry, WorkspaceCapabilityService capabilities,
            DeploymentOrganizationAdmission admission, List<ProviderBindingStatusContribution> bindingStatuses) {
        this.registry = registry;
        this.capabilities = capabilities;
        this.admission = admission;
        this.bindingStatuses = bindingStatuses.stream().collect(Collectors.toUnmodifiableMap(
                ProviderBindingStatusContribution::domain, Function.identity()));
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
        var contribution = bindingStatuses.get("files");
        if (contribution == null) return null;
        var status = contribution.status(organization);
        return new FilesBindingStatusResponse(BindingState.valueOf(status.bindingState().name()),
                status.bindingRevision(), status.adapterKey(), Readiness.valueOf(status.readiness().name()));
    }
}
